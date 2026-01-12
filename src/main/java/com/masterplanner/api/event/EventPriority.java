package com.masterplanner.api.event;

/**
 * 事件优先级枚举
 */
public enum EventPriority {
    /** 最低优先级，最后执行 */
    LOWEST(0),
    
    /** 低优先级 */
    LOW(1),
    
    /** 普通优先级 */
    NORMAL(2),
    
    /** 高优先级 */
    HIGH(3),
    
    /** 最高优先级，最先执行 */
    HIGHEST(4),
    
    /** 监视优先级，用于监视事件，不处理事件 */
    MONITOR(5);

    private final int value;

    EventPriority(int value) {
        this.value = value;
    }

    /**
     * 获取优先级值
     * @return 优先级值
     */
    public int getValue() {
        return value;
    }

    /**
     * 比较优先级
     * @param other 其他优先级
     * @return 如果当前优先级更高返回正数，相等返回0，更低返回负数
     */
    public int compareToOther(EventPriority other) {
        return Integer.compare(this.value, other.value);
    }

    /**
     * 检查是否高于指定优先级
     * @param other 其他优先级
     * @return 如果当前优先级高于指定优先级返回true
     */
    public boolean isHigherThan(EventPriority other) {
        return this.value > other.value;
    }

    /**
     * 检查是否低于指定优先级
     * @param other 其他优先级
     * @return 如果当前优先级低于指定优先级返回true
     */
    public boolean isLowerThan(EventPriority other) {
        return this.value < other.value;
    }
}
