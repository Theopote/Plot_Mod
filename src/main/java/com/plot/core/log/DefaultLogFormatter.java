package com.plot.core.log;

import com.plot.api.log.ILogFormatter;
import com.plot.api.log.LogRecord;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 默认日志格式化器（项目内唯一实现）。
 */
public class DefaultLogFormatter implements ILogFormatter {
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
    private static final ZoneId ZONE_ID = ZoneId.systemDefault();

    private String dateTimeFormat = DEFAULT_DATE_FORMAT;
    private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT);
    private boolean includeTimestamp = true;
    private boolean includeLevel = true;
    private boolean includeSource = true;
    private boolean includeThread = true;

    @Override
    public String format(LogRecord record) {
        StringBuilder sb = new StringBuilder();

        if (includeTimestamp) {
            sb.append('[')
              .append(Instant.ofEpochMilli(record.getTimestamp()).atZone(ZONE_ID).format(dateFormatter))
              .append("] ");
        }

        if (includeLevel) {
            sb.append('[').append(record.getLevel().getName()).append("] ");
        }

        if (includeThread) {
            sb.append('[').append(record.getThread().getName()).append("] ");
        }

        if (includeSource && record.getSourceClassName() != null) {
            sb.append('[').append(record.getSourceClassName());
            if (record.getSourceMethodName() != null) {
                sb.append('.').append(record.getSourceMethodName());
            }
            sb.append("] ");
        }

        String message = record.getMessage();
        Object[] parameters = record.getParameters();
        if (parameters != null && parameters.length > 0) {
            message = String.format(message, parameters);
        }
        sb.append(message);

        if (record.getThrowable() != null) {
            sb.append('\n').append(formatThrowable(record.getThrowable()));
        }

        return sb.toString();
    }

    private String formatThrowable(Throwable thrown) {
        StringBuilder sb = new StringBuilder();
        sb.append(thrown);

        for (StackTraceElement element : thrown.getStackTrace()) {
            sb.append("\n    at ").append(element);
        }

        Throwable cause = thrown.getCause();
        if (cause != null) {
            sb.append("\nCaused by: ").append(formatThrowable(cause));
        }

        return sb.toString();
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
    public boolean isIncludeSource() {
        return includeSource;
    }

    @Override
    public void setIncludeSource(boolean include) {
        this.includeSource = include;
    }

    @Override
    public boolean isIncludeThread() {
        return includeThread;
    }

    @Override
    public void setIncludeThread(boolean include) {
        this.includeThread = include;
    }
}
