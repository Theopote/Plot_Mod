package com.plot.api.log;

/**
 * 日志处理器接口
 */
public interface ILogHandler {
    /**
     * 处理日志记录
     * @param record 日志记录
     */
    void publish(LogRecord record);

    /**
     * 刷新日志
     */
    void flush();

    /**
     * 关闭处理器
     */
    void close();

    /**
     * 设置日志格式化器
     * @param formatter 日志格式化器
     */
    void setFormatter(ILogFormatter formatter);

    /**
     * 获取日志格式化器
     * @return 日志格式化器
     */
    ILogFormatter getFormatter();

    /**
     * 设置日志级别
     * @param level 日志级别
     */
    void setLevel(LogLevel level);

    /**
     * 获取日志级别
     * @return 日志级别
     */
    LogLevel getLevel();

    /**
     * 是否启用
     * @return 是否启用
     */
    boolean isEnabled();

    /**
     * 设置是否启用
     * @param enabled 是否启用
     */
    void setEnabled(boolean enabled);
}
