package com.example.fitlog.data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.fitlog.AuthManager;
import com.example.fitlog.MealInsertDto;
import com.example.fitlog.RetrofitClient;
import com.example.fitlog.SupabaseApi;
import com.example.fitlog.WaterLog;
import com.example.fitlog.WeightLog;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.List;

import retrofit2.Response;

/** Çevrimdışı kuyruktaki kayıtları sunucuya gönderir. */
public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";
    private static final int MAX_ATTEMPTS = 8;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        if (!AuthManager.hasSession(ctx)) return Result.success(); // çıkış yapılmış

        OfflineQueue queue = OfflineQueue.get(ctx);
        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
        Gson gson = new Gson();

        List<OfflineQueue.PendingWrite> batch = queue.dao().nextBatch();
        for (OfflineQueue.PendingWrite w : batch) {
            try {
                Response<?> r = send(api, gson, w);
                if (r.isSuccessful() || r.code() == 409) {
                    queue.dao().delete(w);
                } else if (new ApiException(r.code(), "").isTransient()) {
                    return Result.retry();
                } else {
                    // Kalıcı hata (ör. doğrulama): sonsuz döngüye girmemek için at.
                    Log.w(TAG, "Bekleyen kayıt reddedildi: " + w.kind + " HTTP " + r.code());
                    queue.dao().delete(w);
                }
            } catch (IOException e) {
                w.attempts++;
                if (w.attempts >= MAX_ATTEMPTS) queue.dao().delete(w); else queue.dao().update(w);
                return Result.retry();
            } catch (RuntimeException e) {
                Log.w(TAG, "Bozuk bekleyen kayıt atlandı", e);
                queue.dao().delete(w);
            }
        }
        return queue.dao().nextBatch().isEmpty() ? Result.success() : Result.retry();
    }

    static Response<?> send(SupabaseApi api, Gson gson, OfflineQueue.PendingWrite w) throws IOException {
        switch (w.kind) {
            case PendingWriteStore.KIND_WATER:
                return api.insertWaterLog(gson.fromJson(w.payload, WaterLog.class)).execute();
            case PendingWriteStore.KIND_MEAL:
                return api.insertMealLog(gson.fromJson(w.payload, MealInsertDto.class)).execute();
            case PendingWriteStore.KIND_WEIGHT:
                return api.insertWeightLog(gson.fromJson(w.payload, WeightLog.class)).execute();
            default:
                throw new IllegalArgumentException("unknown kind " + w.kind);
        }
    }
}
