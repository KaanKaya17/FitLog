package com.example.fitlog.data;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Delete;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.Update;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Room tabanlı çevrimdışı yazma kuyruğu.
 * Kayıtlar cihazda tutulur; ağ geldiğinde {@link SyncWorker} sırayla gönderir.
 */
public final class OfflineQueue implements PendingWriteStore, ReadCache {

    public static final String SYNC_WORK = "fitlog-offline-sync";

    @Entity(tableName = "pending_writes")
    public static class PendingWrite {
        @PrimaryKey(autoGenerate = true) public long id;
        public String kind;
        public String payload;
        public long createdAt;
        public int attempts;
    }

    @Dao
    public interface PendingWriteDao {
        @Insert long insert(PendingWrite w);
        @Query("SELECT * FROM pending_writes ORDER BY id ASC LIMIT 100") List<PendingWrite> nextBatch();
        @Query("SELECT COUNT(*) FROM pending_writes") LiveData<Integer> observeCount();
        @Query("DELETE FROM pending_writes") void clear();
        @Delete void delete(PendingWrite w);
        @Update void update(PendingWrite w);
    }

    /** Son başarılı okumaların JSON kopyası (çevrimdışı gösterim için). */
    @Entity(tableName = "read_cache")
    public static class CacheEntry {
        @PrimaryKey @androidx.annotation.NonNull public String key = "";
        public String json;
        public long updatedAt;
    }

    @Dao
    public interface CacheDao {
        @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE) void put(CacheEntry e);
        @Query("SELECT * FROM read_cache WHERE `key` = :key") CacheEntry get(String key);
        @Query("DELETE FROM read_cache") void clear();
    }

    @Database(entities = {PendingWrite.class, CacheEntry.class}, version = 2, exportSchema = false)
    public abstract static class Db extends RoomDatabase {
        public abstract PendingWriteDao dao();
        public abstract CacheDao cache();
    }

    /** v1 → v2: kuyruğu koruyarak önbellek tablosunu ekler. */
    static final androidx.room.migration.Migration MIGRATION_1_2 = new androidx.room.migration.Migration(1, 2) {
        @Override
        public void migrate(@androidx.annotation.NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `read_cache` (`key` TEXT NOT NULL, `json` TEXT, "
                    + "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))");
        }
    };

    private static volatile OfflineQueue instance;

    private final Context appContext;
    private final Db db;

    private OfflineQueue(Context context) {
        appContext = context.getApplicationContext();
        db = Room.databaseBuilder(appContext, Db.class, "fitlog-offline.db")
                .addMigrations(MIGRATION_1_2)
                .build();
    }

    public static OfflineQueue get(Context context) {
        if (instance == null) {
            synchronized (OfflineQueue.class) {
                if (instance == null) instance = new OfflineQueue(context);
            }
        }
        return instance;
    }

    public PendingWriteDao dao() { return db.dao(); }

    /** Arka plan thread'inden çağrılmalıdır (Room ana thread'de sorguya izin vermez). */
    @Override
    public void enqueue(String kind, String jsonPayload) {
        PendingWrite w = new PendingWrite();
        w.kind = kind;
        w.payload = jsonPayload;
        w.createdAt = System.currentTimeMillis();
        db.dao().insert(w);
        scheduleSync();
    }

    /** Çıkışta başka kullanıcıya ait bekleyen kayıtları ve önbelleği sil. */
    public void clear() {
        db.dao().clear();
        db.cache().clear();
    }

    @Override
    public void put(String key, String json) {
        CacheEntry e = new CacheEntry();
        e.key = key;
        e.json = json;
        e.updatedAt = System.currentTimeMillis();
        db.cache().put(e);
    }

    @Override
    public String get(String key) {
        CacheEntry e = db.cache().get(key);
        return e != null ? e.json : null;
    }

    public void scheduleSync() {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(appContext).enqueueUniqueWork(SYNC_WORK, ExistingWorkPolicy.KEEP, req);
    }
}
