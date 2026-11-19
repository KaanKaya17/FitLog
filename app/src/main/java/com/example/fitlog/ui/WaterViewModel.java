package com.example.fitlog.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fitlog.R;
import com.example.fitlog.WaterLog;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Günlük su takibi: ekleme, geri alma ve günü sıfırlama. */
public class WaterViewModel extends BaseViewModel {

    public static final int MAX_DAILY_ML = 10000;

    private final MutableLiveData<Integer> total = new MutableLiveData<>(0);
    private final MutableLiveData<Event<WaterLog>> undoable = new MutableLiveData<>();
    private final List<WaterLog> today = new ArrayList<>();

    public WaterViewModel(@NonNull Application app) { super(app); }

    public LiveData<Integer> total() { return total; }
    /** Az önce sunucuya kaydedilen (geri alınabilir) kayıt. */
    public LiveData<Event<WaterLog>> undoable() { return undoable; }

    public void refresh() {
        run(() -> repo.getWater(LocalDate.now()), logs -> {
            today.clear();
            today.addAll(logs);
            total.setValue(sum(today));
        });
    }

    static int sum(List<WaterLog> logs) {
        int t = 0;
        for (WaterLog w : logs) t += w.getAmount_ml();
        return t;
    }

    /** @return false: günlük üst sınır aşılacağı için eklenmedi */
    public boolean add(int ml) {
        Integer cur = total.getValue();
        if ((cur != null ? cur : 0) + ml > MAX_DAILY_ML) return false;
        total.setValue((cur != null ? cur : 0) + ml); // iyimser güncelleme
        com.example.fitlog.data.AppExecutors.io().execute(() -> {
            try {
                com.example.fitlog.data.FitLogRepository.WriteResult<WaterLog> result = repo.addWater(ml);
                com.example.fitlog.data.AppExecutors.main(() -> {
                    today.add(result.value);
                    total.setValue(sum(today));
                    if (result.queued) postMessage(R.string.msg_saved_offline);
                    else undoable.setValue(new Event<>(result.value));
                });
            } catch (Exception e) {
                // İyimser güncellemeyi geri al (ağ isteği atmadan).
                com.example.fitlog.data.AppExecutors.main(() -> total.setValue(sum(today)));
                postError(e);
            }
        });
        return true;
    }

    public void undo(WaterLog log) {
        run(() -> { repo.undoWater(log); return null; }, ignored -> {
            today.remove(log);
            total.setValue(sum(today));
            postMessage(R.string.water_undone);
        });
    }

    public void resetToday() {
        run(() -> { repo.resetWater(LocalDate.now()); return null; }, ignored -> {
            today.clear();
            total.setValue(0);
            postMessage(R.string.water_msg_reset);
        });
    }
}
