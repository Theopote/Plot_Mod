package com.masterplanner.infrastructure.event.view;

import com.masterplanner.infrastructure.event.base.Event;
import com.masterplanner.api.event.EventType;

/**
 * 网格颜色改变事件
 */
public class GridColorChangedEvent extends Event {
    private final float[] color;
    private final String source;

    public GridColorChangedEvent(float[] color) {
        this("ViewManager", color);
    }
    
    public GridColorChangedEvent(String source, float[] color) {
        super(EventType.VIEW_CHANGED);  // 使用 VIEW_CHANGED 事件类型，因为网格颜色变化会影响视图
        this.source = source;
        this.color = color;
    }

    public float[] getColor() {
        return color;
    }
    
    @Override
    public String getSource() {
        return source;
    }
    
    @Override
    public String toString() {
        return String.format("GridColorChangedEvent[source=%s, color=(%f,%f,%f,%f)]", 
            source, color[0], color[1], color[2], color.length > 3 ? color[3] : 1.0f);
    }
} 