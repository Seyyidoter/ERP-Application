package com.example.erpdemo;

import javafx.concurrent.Task;
import javafx.application.Platform;

import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.concurrent.Callable;

/**
 * Uygulama genelinde asenkron işler için tek thread havuzu.
 * - JavaFX Task kullanıldığı için onSuccess/onError callback'leri FX Thread üzerinde çalışır.
 */
public final class Async {
    private Async() {}

    // Havuz ayarları (makul varsayılanlar)
    private static final int CORE = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    private static final int MAX  = Math.max(2, Runtime.getRuntime().availableProcessors());
    private static final int QUEUE_CAP = 512;

    private static final ExecutorService EXEC = new ThreadPoolExecutor(
            CORE,
            MAX,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(QUEUE_CAP),
            r -> {
                Thread t = new Thread(r, "omnis-async");
                t.setDaemon(true); // JVM kapanışını engellemesin
                t.setUncaughtExceptionHandler((th, ex) -> ex.printStackTrace());
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /** Sonuç döndüren iş */
    public static <T> void run(Callable<T> work,
                               Consumer<T> onSuccess,
                               Consumer<Throwable> onError,
                               Runnable onFinally) {
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };

        task.setOnSucceeded(e -> {
            try { if (onSuccess != null) onSuccess.accept(task.getValue()); }
            finally { if (onFinally != null) onFinally.run(); }
        });
        task.setOnFailed(e -> {
            try { if (onError != null) onError.accept(task.getException()); }
            finally { if (onFinally != null) onFinally.run(); }
        });

        EXEC.submit(task);
    }

    /** Sonuç dönmeyen iş */
    public static void runVoid(Runnable work,
                               Runnable onSuccess,
                               Consumer<Throwable> onError,
                               Runnable onFinally) {
        run(() -> { work.run(); return null; },
                v -> { if (onSuccess != null) onSuccess.run(); },
                onError, onFinally);
    }

    /** FX thread'e atmak için kısayol */
    public static void later(Runnable r) { Platform.runLater(r); }

    /** Uygulama kapanışında çağır. */
    public static void shutdownNow() { EXEC.shutdownNow(); }
}
