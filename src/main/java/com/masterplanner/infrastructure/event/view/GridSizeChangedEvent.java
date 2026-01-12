package com.masterplanner.infrastructure.event.view;

import com.masterplanner.infrastructure.event.base.Event;
import com.masterplanner.api.event.EventType;

/**
 * 网格大小改变事件
 */
public class GridSizeChangedEvent extends Event {
    private final float size;
    private final String source;

    public GridSizeChangedEvent(float size) {
        this("ViewManager", size);
    }
    
    public GridSizeChangedEvent(String source, float size) {
        super(EventType.VIEW_CHANGED);  // 使用 VIEW_CHANGED 事件类型，因为网格大小变化会影响视图
        this.source = source;
        this.size = size;
    }

    public float getSize() {
        return size;
    }
    
    @Override
    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return String.format("GridSizeChangedEvent[source=%s, size=%.1f]", source, size);
    }
} 