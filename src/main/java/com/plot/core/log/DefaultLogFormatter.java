package com.plot.core.log;

import com.plot.api.log.ILogFormatter;
import com.plot.api.log.LogRecord;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 默认日志格式化器
 */
public class DefaultLogFormatter implements ILogFormatter {
    private String dateTimeFormat = "yyyy-MM-dd HH:mm:ss.SSS";
    private boolean includeTimestamp = true;
    private boolean includeLevel = true;
    private boolean includeSource = true;
    private boolean includeThread = true;

    @Override
    public String format(LogRecord record) {
        StringBuilder sb = new StringBuilder();
        
        // 添加时间戳
        if (includeTimestamp) {
            SimpleDateFormat sdf = new SimpleDateFormat(dateTimeFormat);
            sb.append('[').append(sdf.format(new Date(record.getTimestamp()))).append("] ");
        }
        
        // 添加日志级别
        if (includeLevel) {
            sb.append('[').append(record.getLevel().getName()).append("] ");
        }
        
        // 添加线程信息
        if (includeThread) {
            sb.append('[').append(record.getThread().getName()).append("] ");
        }
        
        // 添加源信息
        if (includeSource && record.getSourceClassName() != null) {
            String className = record.getSourceClassName();
            String methodName = record.getSourceMethodName();
            sb.append('[').append(className);
            if (methodName != null) {
                sb.append('.').append(methodName);
            }
            sb.append("] ");
        }
        
        // 添加消息
        sb.append(record.getMessage());
        
        // 添加参数
        if (record.getParameters() != null) {
            for (Object param : record.getParameters()) {
                sb.append(" ").append(param);
            }
        }
        
        // 添加异常信息
        if (record.getThrowable() != null) {
            sb.append('\n');
            Throwable t = record.getThrowable();
            sb.append(t.toString());
            for (StackTraceElement element : t.getStackTrace()) {
                sb.append("\n    at ").append(element.toString());
            }
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
