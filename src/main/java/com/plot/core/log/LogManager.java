package com.plot.core.log;

import com.plot.api.log.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 日志管理器
 */
public class LogManager implements ILogger {
    private static final LogManager INSTANCE = new LogManager();
    private final List<ILogHandler> handlers;
    private LogLevel level;
    private ILogFormatter formatter;

    private LogManager() {
        this.handlers = new CopyOnWriteArrayList<>();
        this.level = LogLevel.INFO;
        this.formatter = new DefaultLogFormatter();
        
        // 默认添加控制台处理器
        addHandler(new ConsoleLogHandler());
    }

    public static LogManager getInstance() {
        return INSTANCE;
    }

    //@Override
    public void log(LogLevel level, String message) {
        log(new LogRecord(level, message));
    }

    //@Override
    public void log(LogLevel level, String message, Object... params) {
        log(new LogRecord(level, message, params));
    }

    //@Override
    public void log(LogLevel level, String message, Throwable thrown) {
        log(new LogRecord(level, message, thrown));
    }

    @Override
    public void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    @Override
    public void debug(String message, Object... args) {
        log(LogLevel.DEBUG, message, args);
    }

    @Override
    public void info(String message) {
        log(LogLevel.INFO, message);
    }

    @Override
    public void info(String message, Object... args) {
        log(LogLevel.INFO, message, args);
    }

    @Override
    public void warn(String message) {
        log(LogLevel.WARN, message);
    }

    @Override
    public void warn(String message, Object... args) {
        log(LogLevel.WARN, message, args);
    }

    @Override
    public void error(String message) {
        log(LogLevel.ERROR, message);
    }

    @Override
    public void error(String message, Object... args) {
        log(LogLevel.ERROR, message, args);
    }

    @Override
    public void error(String message, Throwable throwable) {
        log(LogLevel.ERROR, message, throwable);
    }

    private void log(LogRecord record) {
        if (record.getLevel().getValue() >= level.getValue()) {
            String formattedMessage = formatter.format(record);
            for (ILogHandler handler : handlers) {
                handler.publish(record);
            }
        }
    }

    @Override
    public void addHandler(ILogHandler handler) {
        if (handler != null && !handlers.contains(handler)) {
            handlers.add(handler);
        }
    }

    @Override
    public void removeHandler(ILogHandler handler) {
        handlers.remove(handler);
    }


    @Override
    public void clearHandlers() {
        handlers.clear();
    }

    @Override
    public void setLevel(LogLevel level) {
        this.level = level;
    }

    @Override
    public LogLevel getLevel() {
        return level;
    }

    @Override
    public ILogFormatter getFormatter() {
        return formatter;
    }

    @Override
    public void setFormatter(ILogFormatter formatter) {
        this.formatter = formatter;
    }

    //@Override
    public void close() {
        for (ILogHandler handler : handlers) {
            handler.close();
        }
        handlers.clear();
    }
}
