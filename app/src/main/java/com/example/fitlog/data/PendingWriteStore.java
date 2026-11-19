package com.example.fitlog.data;

/**
 * Çevrimdışıyken başarısız olan yazmaları saklayan kuyruk.
 * Üretimde Room ({@link OfflineQueue}), testlerde bellek içi uygulama kullanılır.
 */
public interface PendingWriteStore {

    String KIND_WATER = "water";
    String KIND_MEAL = "meal";
    String KIND_WEIGHT = "weight";

    void enqueue(String kind, String jsonPayload);
}
