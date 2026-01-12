package com.masterplanner.core.log;

import com.masterplanner.api.log.ILogHandler;
import com.masterplanner.api.log.ILogFormatter;
import com.masterplanner.api.log.LogLevel;
import com.masterplanner.api.log.LogRecord;

/**
 * 控制台日志处理器
 */
public class ConsoleLogHandler implements ILogHandler {
    private ILogFormatter formatter;
    private LogLevel level;
    private boolean enabled;

    public ConsoleLogHandler() {
        this.formatter = new DefaultLogFormatter();
        this.level = LogLevel.INFO;
        this.enabled = true;
    }

    @Override
    public void publish(LogRecord record) {
        if (isEnabled() && record.getLevel().getValue() >= level.getValue()) {
            String formattedLog = formatter.format(record);
            if (record.getLevel().getValue() >= LogLevel.ERROR.getValue()) {
                System.err.println(formattedLog);
            } else {
                System.out.println(formattedLog);
            }
        }
    }

    @Override
    public void flush() {
        System.out.flush();
        System.err.flush();
    }

    @Override
    public void close() {
        flush();
    }

    @Override
    public void setFormatter(ILogFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public ILogFormatter getFormatter() {
        return formatter;
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
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
