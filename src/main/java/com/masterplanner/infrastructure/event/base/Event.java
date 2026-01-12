package com.masterplanner.infrastructure.event.base;

import com.masterplanner.api.event.EventType;

/**
 * 事件基类
 */
public abstract class Event {
    private final EventType type;
    private final long timestamp;
    private boolean handled;

    protected Event(EventType type) {
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.handled = false;
    }

    public EventType getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isHandled() {
        return handled;
    }

    public void setHandled(boolean handled) {
        this.handled = handled;
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