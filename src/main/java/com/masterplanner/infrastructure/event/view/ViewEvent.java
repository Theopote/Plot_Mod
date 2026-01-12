package com.masterplanner.infrastructure.event.view;

import com.masterplanner.infrastructure.event.base.Event;
import com.masterplanner.api.event.EventType;

/**
 * 视图事件基类
 */
public abstract class ViewEvent extends Event {
    protected final String source;
    
    /**
     * 构造视图事件
     * @param type 事件类型
     */
    protected ViewEvent(EventType type) {
        this("ViewManager", type);
    }
    
    /**
     * 构造视图事件
     * @param source 事件源
     * @param type 事件类型
     */
    protected ViewEvent(String source, EventType type) {
        super(type);
        this.source = source;
    }
    
    @Override
    public String getSource() {
        return source;
    }
} 