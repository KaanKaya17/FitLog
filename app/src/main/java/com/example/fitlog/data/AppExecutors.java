package com.example.fitlog.data;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Ağ/disk işleri için ortak thread havuzu ve ana thread yürütücüsü. */
public final class AppExecutors {

    private static final ExecutorService IO = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static Executor ioOverride;

    private AppExecutors() {}

    public static Executor io() {
        return ioOverride != null ? ioOverride : IO;
    }

    public static void main(Runnable r) {
        MAIN.post(r);
    }

    /** Yalnızca testler için: işleri senkron çalıştır. */
    public static void setIoForTests(Executor executor) {
        ioOverride = executor;
    }
}
