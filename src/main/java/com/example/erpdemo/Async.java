package com.example.erpdemo;

import javafx.concurrent.Task;
import javafx.application.Platform;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class Async {
    private Async() {}

    // Havuz ayarları
    private static final int CORE = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    private static final int MAX  = Math.max(2, Runtime.getRuntime().availableProcessors());
    private static final int QUEUE_CAP = 512;

    // ⚠️ Değişiklik: CallerRunsPolicy → AbortPolicy (veya custom handler)
    private static final ExecutorService EXEC = new ThreadPoolExecutor(
            CORE,
            MAX,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(QUEUE_CAP),
            r -> {
                Thread t = new Thread(r, "omnis-async");
                t.setDaemon(true);
                t.setUncaughtExceptionHandler((th, ex) -> ex.printStackTrace());
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy() // <-- kritik değişiklik
    );

    private static final AtomicBoolean STOPPING = new AtomicBoolean(false);

    public static void blockNewTasks() { STOPPING.set(true); }

    public static boolean shutdownGracefully(long timeout, TimeUnit unit) {
        STOPPING.set(true);
        EXEC.shutdown();
        try {
            return EXEC.awaitTermination(timeout, unit);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static void shutdownNow() {
        STOPPING.set(true);
        EXEC.shutdownNow();
    }

    /** Sonuç döndüren iş */
    public static <T> void run(Callable<T> work,
                               Consumer<T> onSuccess,
                               Consumer<Throwable> onError,
                               Runnable onFinally) {

        if (STOPPING.get() || EXEC.isShutdown() || EXEC.isTerminated()) {
            // Havuz kapalıysa "nazikçe" bitir
            if (onFinally != null) Platform.runLater(onFinally);
            // İsteğe bağlı: onError bildirimi
            if (onError != null) Platform.runLater(() ->
                    onError.accept(new RejectedExecutionException("Arkaplan havuzu kapalı.")));
            return;
        }

        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };

        // JavaFX Task callback’leri FX thread’de çalışır
        task.setOnSucceeded(e -> {
            try { if (onSuccess != null) onSuccess.accept(task.getValue()); }
            finally { if (onFinally != null) onFinally.run(); }
        });
        task.setOnFailed(e -> {
            try { if (onError != null) onError.accept(task.getException()); }
            finally { if (onFinally != null) onFinally.run(); }
        });

        try {
            EXEC.submit(task);
        } catch (RejectedExecutionException rex) {
            // ⚠️ Kuyruk dolu: UI’da BLOK YOK — kullanıcıya meşgul uyarısı ver
            if (onError != null) {
                Platform.runLater(() -> onError.accept(
                        new RejectedExecutionException("Sistem meşgul: çok sayıda arkaplan işlem var. Lütfen tekrar deneyin.", rex)
                ));
            }
            if (onFinally != null) Platform.runLater(onFinally);
        }
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

    /** FX thread kısayolu */
    public static void later(Runnable r) { Platform.runLater(r); }
}
