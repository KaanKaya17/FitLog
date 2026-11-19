package com.example.fitlog.data;

/**
 * Son başarılı okumaların saklandığı anahtar/JSON deposu. Ağ yokken
 * {@link FitLogRepository} buradan son bilinen veriyi döndürür.
 * Üretimde Room ({@link OfflineQueue}), testlerde bellek içi uygulama.
 * Arka plan thread'inden çağrılmalıdır.
 */
public interface ReadCache {
    void put(String key, String json);

    /** @return yoksa null */
    String get(String key);

    /** Önbelleği kullanmayan uygulama. */
    ReadCache NONE = new ReadCache() {
        @Override public void put(String key, String json) {}
        @Override public String get(String key) { return null; }
    };
}
