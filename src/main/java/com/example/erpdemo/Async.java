package com.example.erpdemo;

import javafx.concurrent.Task;
import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** FX thread’ini kilitlemeden iş çalıştırmak için küçük yardımcı. */
public final class Async {
    private Async() {}

    public static <T> void run(Callable<T> work,
                               Consumer<T> onSuccess,
                               Consumer<Throwable> onError,
                               Runnable onFinally) {
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        task.setOnSucceeded(e -> {
            if (onSuccess != null) onSuccess.accept(task.getValue());
            if (onFinally != null) onFinally.run();
        });
        task.setOnFailed(e -> {
            if (onError != null) onError.accept(task.getException());
            if (onFinally != null) onFinally.run();
        });
        Thread t = new Thread(task, "async-task");
        t.setDaemon(true);
        t.start();
    }

    public static void runVoid(Runnable work,
                               Runnable onSuccess,
                               Consumer<Throwable> onError,
                               Runnable onFinally) {
        run(() -> { work.run(); return null; }, x -> {
            if (onSuccess != null) onSuccess.run();
        }, onError, onFinally);
    }

    /** Sırf Platform.runLater için küçük kısayol. */
    public static void later(Runnable r) { Platform.runLater(r); }
}
