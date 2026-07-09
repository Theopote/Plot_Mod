package com.plot.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 为有意忽略的异常提供统一的 debug 级日志，避免静默吞掉错误。
 */
public final class ExceptionDebug {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/IgnoredException");

    private ExceptionDebug() {
    }

    public static void log(String context, Throwable throwable) {
        if (throwable == null || !LOGGER.isDebugEnabled()) {
            return;
        }
        LOGGER.debug("{}: {}", context, throwable.toString(), throwable);
    }
}
