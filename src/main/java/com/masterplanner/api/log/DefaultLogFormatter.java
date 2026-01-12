package com.masterplanner.api.log;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 默认日志格式化器
 */
public class DefaultLogFormatter implements ILogFormatter {
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
    private DateTimeFormatter dateFormatter;
    private String dateTimeFormat;
    private boolean includeTimestamp;
    private boolean includeLevel;
    private boolean includeThread;
    private boolean includeSource;

    public DefaultLogFormatter() {
        this.dateTimeFormat = DEFAULT_DATE_FORMAT;
        this.dateFormatter = DateTimeFormatter.ofPattern(dateTimeFormat);
        this.includeTimestamp = true;
        this.includeLevel = true;
        this.includeThread = false;
        this.includeSource = false;
    }

    @Override
    public String format(LogRecord record) {
        StringBuilder sb = new StringBuilder();
        
        // 添加时间戳
        if (includeTimestamp) {
            sb.append(LocalDateTime.now().format(dateFormatter))
              .append(" ");
        }

        // 添加日志级别
        if (includeLevel) {
            sb.append("[")
              .append(record.getLevel())
              .append("] ");
        }

        // 添加线程信息
        if (includeThread) {
            sb.append("[")
              .append(Thread.currentThread().getName())
              .append("] ");
        }

        // 添加源信息
        if (includeSource) {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            if (stack.length > 4) {
                StackTraceElement source = stack[4];
                sb.append("[")
                  .append(source.getClassName())
                  .append(".")
                  .append(source.getMethodName())
                  .append(":")
                  .append(source.getLineNumber())
                  .append("] ");
            }
        }

        // 添加消息
        String message = record.getMessage();
        Object[] params = record.getParameters();
        if (params != null && params.length > 0) {
            message = String.format(message, params);
        }
        sb.append(message);

        // 添加异常信息
        Throwable thrown = record.getThrowable();
        if (thrown != null) {
            sb.append("\n").append(formatThrowable(thrown));
        }

        return sb.toString();
    }

    private String formatThrowable(Throwable thrown) {
        StringBuilder sb = new StringBuilder();
        sb.append(thrown.toString());
        
        for (StackTraceElement element : thrown.getStackTrace()) {
            sb.append("\n    at ").append(element.toString());
        }

        Throwable cause = thrown.getCause();
        if (cause != null) {
            sb.append("\nCaused by: ").append(formatThrowable(cause));
        }

        return sb.toString();
    }

    @Override
    public boolean isIncludeTimestamp() {
        return includeTimestamp;
    }

    @Override
    public void setIncludeTimestamp(boolean include) {
        this.includeTimestamp = include;
    }

    @Override
    public boolean isIncludeLevel() {
        return includeLevel;
    }

    @Override
    public void setIncludeLevel(boolean include) {
        this.includeLevel = include;
    }

    @Override
    public boolean isIncludeThread() {
        return includeThread;
    }

    @Override
    public void setIncludeThread(boolean include) {
        this.includeThread = include;
    }

    @Override
    public boolean isIncludeSource() {
        return includeSource;
    }

    @Override
    public void setIncludeSource(boolean include) {
        this.includeSource = include;
    }

    @Override
    public String getDateTimeFormat() {
        return dateTimeFormat;
    }

    @Override
    public void setDateTimeFormat(String format) {
        this.dateTimeFormat = format;
        this.dateFormatter = DateTimeFormatter.ofPattern(format);
    }
} 