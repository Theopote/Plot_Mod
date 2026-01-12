package com.masterplanner.api.log;

/**
 * 日志格式化器接口
 */
public interface ILogFormatter {
    /**
     * 格式化日志记录
     * @param record 日志记录
     * @return 格式化后的字符串
     */
    String format(LogRecord record);

    /**
     * 获取日期时间格式
     * @return 日期时间格式
     */
    String getDateTimeFormat();

    /**
     * 设置日期时间格式
     * @param format 日期时间格式
     */
    void setDateTimeFormat(String format);

    /**
     * 是否包含时间戳
     * @return 是否包含时间戳
     */
    boolean isIncludeTimestamp();

    /**
     * 设置是否包含时间戳
     * @param include 是否包含时间戳
     */
    void setIncludeTimestamp(boolean include);

    /**
     * 是否包含日志级别
     * @return 是否包含日志级别
     */
    boolean isIncludeLevel();

    /**
     * 设置是否包含日志级别
     * @param include 是否包含日志级别
     */
    void setIncludeLevel(boolean include);

    /**
     * 是否包含源信息
     * @return 是否包含源信息
     */
    boolean isIncludeSource();

    /**
     * 设置是否包含源信息
     * @param include 是否包含源信息
     */
    void setIncludeSource(boolean include);

    /**
     * 是否包含线程信息
     * @return 是否包含线程信息
     */
    boolean isIncludeThread();

    /**
     * 设置是否包含线程信息
     * @param include 是否包含线程信息
     */
    void setIncludeThread(boolean include);
}
