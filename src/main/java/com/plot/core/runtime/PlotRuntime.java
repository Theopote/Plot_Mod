package com.plot.core.runtime;

import com.plot.core.state.DebouncedTasks;
import com.plot.core.context.ApplicationContext;
import com.plot.infrastructure.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plot 运行时收口：在客户端停止等生命周期点统一释放后台资源。
 */
public final class PlotRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/PlotRuntime");
    private static final Object LOCK = new Object();
    private static volatile boolean shutDown;

    private PlotRuntime() {
    }

    public static boolean isShutDown() {
        return shutDown;
    }

    /**
     * 取消防抖任务、清空 EventBus 订阅、关闭 PlotExecutors。可重复调用。
     */
    public static void shutdown() {
        synchronized (LOCK) {
            if (shutDown) {
                return;
            }
            shutDown = true;
        }

        LOGGER.info("PlotRuntime.shutdown starting...");
        try {
            DebouncedTasks.shutdown();
        } catch (Exception e) {
            LOGGER.error("DebouncedTasks.shutdown failed", e);
        }
        try {
            ApplicationContext.getInstance().getEventBus().clear();
        } catch (Exception e) {
            LOGGER.error("EventBus.clear failed", e);
        }
        try {
            PlotExecutors.shutdown();
        } catch (Exception e) {
            LOGGER.error("PlotExecutors.shutdown failed", e);
        }
        LOGGER.info("PlotRuntime.shutdown complete");
    }
}
