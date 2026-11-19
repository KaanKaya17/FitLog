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
public final class OfflineQueue implements PendingWriteStore {

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

    @Database(entities = {PendingWrite.class}, version = 1, exportSchema = false)
    public abstract static class Db extends RoomDatabase {
        public abstract PendingWriteDao dao();
    }

    private static volatile OfflineQueue instance;

    private final Context appContext;
    private final Db db;

    private OfflineQueue(Context context) {
        appContext = context.getApplicationContext();
        db = Room.databaseBuilder(appContext, Db.class, "fitlog-offline.db").build();
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

    /** Çıkışta başka kullanıcıya ait bekleyen kayıtları sil. */
    public void clear() { db.dao().clear(); }

    public void scheduleSync() {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(appContext).enqueueUniqueWork(SYNC_WORK, ExistingWorkPolicy.KEEP, req);
    }
}
