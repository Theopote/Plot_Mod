package com.plot.core.state;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 共享的延迟 / 防抖任务调度（从 AppState 抽出）。
 */
public final class DebouncedTasks {
    private static final ScheduledExecutorService SCHEDULER =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "plot-debounced-tasks");
            t.setDaemon(true);
            return t;
        });
    private static final ConcurrentHashMap<String, ScheduledFuture<?>> PENDING = new ConcurrentHashMap<>();
    private static final int DEFAULT_DELAY_MS = 16;

    private DebouncedTasks() {
    }

    public static void publishDebounced(String eventKey, Runnable eventPublisher) {
        publishDebounced(eventKey, eventPublisher, DEFAULT_DELAY_MS);
    }

    public static void publishDebounced(String eventKey, Runnable eventPublisher, int delayMs) {
        ScheduledFuture<?> previous = PENDING.get(eventKey);
        if (previous != null) {
            previous.cancel(false);
        }

        ScheduledFuture<?> future = SCHEDULER.schedule(() -> {
            try {
                eventPublisher.run();
            } finally {
                PENDING.remove(eventKey);
            }
        }, delayMs, TimeUnit.MILLISECONDS);

        PENDING.put(eventKey, future);
    }

    public static ScheduledFuture<?> scheduleDelayed(Runnable task, int delayMs) {
        return SCHEDULER.schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }

    public static void shutdown() {
        SCHEDULER.shutdown();
    }
}
