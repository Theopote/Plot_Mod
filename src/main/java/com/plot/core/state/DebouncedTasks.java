package com.plot.core.state;

import com.plot.core.runtime.PlotExecutors;
import com.plot.core.runtime.PlotRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 防抖 / 延迟任务。调度器由 {@link PlotExecutors} 拥有，勿自建线程池。
 */
public final class DebouncedTasks {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/DebouncedTasks");
    private static final ConcurrentHashMap<String, ScheduledFuture<?>> PENDING = new ConcurrentHashMap<>();
    private static final int DEFAULT_DELAY_MS = 16;

    private DebouncedTasks() {
    }

    public static void publishDebounced(String eventKey, Runnable eventPublisher) {
        publishDebounced(eventKey, eventPublisher, DEFAULT_DELAY_MS);
    }

    public static void publishDebounced(String eventKey, Runnable eventPublisher, int delayMs) {
        if (eventPublisher == null || isUnavailable()) {
            return;
        }

        ScheduledFuture<?> previous = PENDING.get(eventKey);
        if (previous != null) {
            previous.cancel(false);
        }

        ScheduledFuture<?> future = PlotExecutors.scheduler().schedule(() -> {
            try {
                if (!isUnavailable()) {
                    eventPublisher.run();
                }
            } catch (Exception e) {
                LOGGER.error("Debounced task failed for key={}", eventKey, e);
            } finally {
                PENDING.remove(eventKey);
            }
        }, delayMs, TimeUnit.MILLISECONDS);

        PENDING.put(eventKey, future);
    }

    public static ScheduledFuture<?> scheduleDelayed(Runnable task, int delayMs) {
        if (task == null || delayMs < 0) {
            throw new IllegalArgumentException("task is null or delay is negative");
        }
        if (isUnavailable()) {
            return null;
        }
        return PlotExecutors.scheduler().schedule(() -> {
            if (isUnavailable()) {
                return;
            }
            try {
                task.run();
            } catch (Exception e) {
                LOGGER.error("Delayed task failed", e);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 取消全部 pending 任务。不关闭执行器（由 {@link PlotRuntime} / {@link PlotExecutors} 负责）。
     */
    public static void shutdown() {
        for (ScheduledFuture<?> future : PENDING.values()) {
            if (future != null) {
                future.cancel(false);
            }
        }
        PENDING.clear();
    }

    private static boolean isUnavailable() {
        return PlotRuntime.isShutDown() || PlotExecutors.isShutdown();
    }
}
