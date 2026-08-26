package com.plot.core.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plot 统一线程池。线程均为 daemon，避免阻止 JVM / 游戏退出。
 */
public final class PlotExecutors {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/PlotExecutors");
    private static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);

    private static final ExecutorService UI = Executors.newSingleThreadExecutor(daemonFactory("plot-ui"));
    private static final ExecutorService IO = Executors.newFixedThreadPool(2, daemonFactory("plot-io"));
    private static final ExecutorService COMPUTATION = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        daemonFactory("plot-compute"));
    private static final ScheduledExecutorService SCHEDULER =
        Executors.newSingleThreadScheduledExecutor(daemonFactory("plot-scheduler"));

    private PlotExecutors() {
    }

    public static ExecutorService ui() {
        return UI;
    }

    public static ExecutorService io() {
        return IO;
    }

    public static ExecutorService computation() {
        return COMPUTATION;
    }

    public static ScheduledExecutorService scheduler() {
        return SCHEDULER;
    }

    public static boolean isShutdown() {
        return SHUTDOWN.get();
    }

    /**
     * 关闭全部执行器。可安全重复调用。
     */
    public static void shutdown() {
        if (!SHUTDOWN.compareAndSet(false, true)) {
            return;
        }
        LOGGER.info("Shutting down PlotExecutors...");
        shutdownExecutor("ui", UI);
        shutdownExecutor("io", IO);
        shutdownExecutor("computation", COMPUTATION);
        shutdownExecutor("scheduler", SCHEDULER);
        LOGGER.info("PlotExecutors shut down");
    }

    private static void shutdownExecutor(String name, ExecutorService executor) {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                LOGGER.warn("PlotExecutors.{} did not terminate in time, forcing shutdownNow", name);
                executor.shutdownNow();
                executor.awaitTermination(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while shutting down PlotExecutors.{}", name);
        } catch (Exception e) {
            LOGGER.error("Error shutting down PlotExecutors.{}", name, e);
        }
    }

    private static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger index = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
