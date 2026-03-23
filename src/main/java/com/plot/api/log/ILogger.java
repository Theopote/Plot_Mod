package com.plot.api.log;

/**
 * 日志接口
 */
public interface ILogger {
    /**
     * 输出调试信息
     * @param message 日志消息
     */
    void debug(String message);

    /**
     * 输出调试信息
     * @param message 日志消息
     * @param args 格式化参数
     */
    void debug(String message, Object... args);

    /**
     * 输出信息
     * @param message 日志消息
     */
    void info(String message);

    /**
     * 输出信息
     * @param message 日志消息
     * @param args 格式化参数
     */
    void info(String message, Object... args);

    /**
     * 输出警告
     * @param message 日志消息
     */
    void warn(String message);

    /**
     * 输出警告
     * @param message 日志消息
     * @param args 格式化参数
     */
    void warn(String message, Object... args);

    /**
     * 输出错误
     * @param message 日志消息
     */
    void error(String message);

    /**
     * 输出错误
     * @param message 日志消息
     * @param args 格式化参数
     */
    void error(String message, Object... args);

    /**
     * 输出错误
     * @param message 日志消息
     * @param throwable 异常
     */
    void error(String message, Throwable throwable);

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
     * 添加日志处理器
     * @param handler 日志处理器
     */
    void addHandler(ILogHandler handler);

    /**
     * 移除日志处理器
     * @param handler 日志处理器
     */
    void removeHandler(ILogHandler handler);

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
     * 清空日志处理器
     */
    void clearHandlers();
}
