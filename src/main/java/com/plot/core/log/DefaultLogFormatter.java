package com.plot.core.log;

import com.plot.api.log.ILogFormatter;
import com.plot.api.log.LogRecord;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 默认日志格式化器（项目内唯一实现）。
 * <p>
 * 支持 SLF4J 风格 {@code {}} 占位符，以及 {@link String#format} 的 {@code %s}/{@code %d} 等格式。
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

        sb.append(formatMessage(record.getMessage(), record.getParameters()));

        if (record.getThrowable() != null) {
            sb.append('\n').append(formatThrowable(record.getThrowable()));
        }

        return sb.toString();
    }

    /**
     * 优先按 SLF4J {@code {}} 逐个替换；否则回退到 {@link String#format}。
     */
    static String formatMessage(String message, Object[] parameters) {
        if (message == null) {
            return "";
        }
        if (parameters == null || parameters.length == 0) {
            return message;
        }
        if (message.contains("{}")) {
            return formatSlf4jStyle(message, parameters);
        }
        try {
            return String.format(message, parameters);
        } catch (Exception e) {
            return message + " " + java.util.Arrays.toString(parameters);
        }
    }

    private static String formatSlf4jStyle(String message, Object[] parameters) {
        StringBuilder sb = new StringBuilder(message.length() + 32);
        int paramIndex = 0;
        int i = 0;
        while (i < message.length()) {
            int idx = message.indexOf("{}", i);
            if (idx < 0) {
                sb.append(message, i, message.length());
                break;
            }
            sb.append(message, i, idx);
            if (paramIndex < parameters.length) {
                sb.append(parameters[paramIndex++]);
            } else {
                sb.append("{}");
            }
            i = idx + 2;
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
