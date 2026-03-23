package com.plot.api.log;

/**
 * 日志级别枚举
 */
public enum LogLevel {
    /** 调试级别 */
    DEBUG(0, "DEBUG"),
    
    /** 信息级别 */
    INFO(1, "INFO"),
    
    /** 警告级别 */
    WARN(2, "WARN"),
    
    /** 错误级别 */
    ERROR(3, "ERROR"),
    
    /** 关闭日志 */
    OFF(4, "OFF");

    private final int value;
    private final String name;

    LogLevel(int value, String name) {
        this.value = value;
        this.name = name;
    }

    /**
     * 获取日志级别值
     * @return 级别值
     */
    public int getValue() {
        return value;
    }

    /**
     * 获取日志级别名称
     * @return 级别名称
     */
    public String getName() {
        return name;
    }

    /**
     * 判断是否启用指定级别
     * @param level 要判断的级别
     * @return 是否启用
     */
    public boolean isEnabled(LogLevel level) {
        return this.value <= level.value;
    }

    /**
     * 通过名称获取日志级别
     * @param name 级别名称
     * @return 日志级别，如果不存在返回null
     */
    public static LogLevel fromName(String name) {
        for (LogLevel level : values()) {
            if (level.name.equalsIgnoreCase(name)) {
                return level;
            }
        }
        return null;
    }
}
