package com.plot.infrastructure.event.base;

import com.plot.api.event.EventType;

/**
 * 事件基类
 */
public abstract class Event {
    private final EventType type;

    protected Event(EventType type) {
        this.type = type;
        long timestamp = System.currentTimeMillis();
        boolean handled = false;
    }

    public EventType getType() {
        return type;
    }

    /**
     * 获取事件源
     * @return 事件源标识符
     */
    public abstract String getSource();

    @Override
    public String toString() {
        return String.format("Event[type=%s]", type);
    }
} 