package com.masterplanner.infrastructure.event.canvas;

import com.masterplanner.api.event.EventType;
import com.masterplanner.infrastructure.event.base.Event;

/**
 * 画布事件基类
 */
public abstract class CanvasEvent extends Event {
    protected final String source;
    
    /**
     * 构造画布事件
     * @param type 事件类型
     */
    protected CanvasEvent(EventType type) {
        this("CanvasManager", type);
    }
    
    /**
     * 构造画布事件
     * @param source 事件源
     * @param type 事件类型
     */
    protected CanvasEvent(String source, EventType type) {
        super(type);
        this.source = source;
    }
    
    @Override
    public String getSource() {
        return source;
    }
} 