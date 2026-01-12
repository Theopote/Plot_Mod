package com.masterplanner.infrastructure.event.canvas;

import com.masterplanner.api.event.EventType;

/**
 * 清除画布事件
 */
public class ClearCanvasEvent extends CanvasEvent {
    
    /**
     * 构造清除画布事件
     */
    public ClearCanvasEvent() {
        super(EventType.CANVAS_RESIZED);  // 使用可用的事件类型
    }
    
    /**
     * 构造清除画布事件
     * @param source 事件源
     */
    public ClearCanvasEvent(String source) {
        super(source, EventType.CANVAS_RESIZED);
    }
    
    @Override
    public String toString() {
        return String.format("ClearCanvasEvent[source=%s]", getSource());
    }
} 