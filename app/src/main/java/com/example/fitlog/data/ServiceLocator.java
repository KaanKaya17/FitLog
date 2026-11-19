package com.example.fitlog.data;

import android.content.Context;

import com.example.fitlog.PrefManager;
import com.example.fitlog.RetrofitClient;
import com.example.fitlog.SupabaseApi;

import java.time.ZoneId;

/** Basit bağımlılık sağlayıcı (DI kütüphanesi olmadan). */
public final class ServiceLocator {

    private static volatile FitLogRepository repository;

    private ServiceLocator() {}

    public static FitLogRepository repository(Context context) {
        if (repository == null) {
            synchronized (ServiceLocator.class) {
                if (repository == null) {
                    Context app = context.getApplicationContext();
                    repository = new FitLogRepository(
                            RetrofitClient.getClient().create(SupabaseApi.class),
                            () -> new PrefManager(app).getId(),
                            ZoneId::systemDefault,
                            OfflineQueue.get(app),
                            new OpenFoodFactsClient());
                }
            }
        }
        return repository;
    }

    /** Yalnızca testler için. */
    public static void setRepositoryForTests(FitLogRepository repo) {
        repository = repo;
    }
}
