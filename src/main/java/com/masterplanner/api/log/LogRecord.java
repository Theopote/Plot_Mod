package com.masterplanner.api.log;

/**
 * 日志记录类
 */
public class LogRecord {
    private final long timestamp;
    private final LogLevel level;
    private final String message;
    private final Object[] parameters;
    private final Throwable throwable;
    private final String sourceClassName;
    private final String sourceMethodName;
    private final Thread thread;

    public LogRecord(LogLevel level, String message) {
        this(level, message, null, null);
    }

    public LogRecord(LogLevel level, String message, Object[] parameters) {
        this(level, message, parameters, null);
    }

    public LogRecord(LogLevel level, String message, Throwable throwable) {
        this(level, message, null, throwable);
    }

    public LogRecord(LogLevel level, String message, Object[] parameters, Throwable throwable) {
        this.timestamp = System.currentTimeMillis();
        this.level = level;
        this.message = message;
        this.parameters = parameters;
        this.throwable = throwable;
        this.thread = Thread.currentThread();

        // 获取调用者信息
        StackTraceElement[] stack = new Throwable().getStackTrace();
        this.sourceClassName = stack.length > 2 ? stack[2].getClassName() : null;
        this.sourceMethodName = stack.length > 2 ? stack[2].getMethodName() : null;
    }

    /**
     * 获取时间戳
     * @return 时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 获取日志级别
     * @return 日志级别
     */
    public LogLevel getLevel() {
        return level;
    }

    /**
     * 获取日志消息
     * @return 日志消息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 获取参数数组
     * @return 参数数组
     */
    public Object[] getParameters() {
        return parameters;
    }

    /**
     * 获取异常
     * @return 异常
     */
    public Throwable getThrowable() {
        return throwable;
    }

    /**
     * 获取源类名
     * @return 源类名
     */
    public String getSourceClassName() {
        return sourceClassName;
    }

    /**
     * 获取源方法名
     * @return 源方法名
     */
    public String getSourceMethodName() {
        return sourceMethodName;
    }

    /**
     * 获取线程
     * @return 线程
     */
    public Thread getThread() {
        return thread;
    }
}
