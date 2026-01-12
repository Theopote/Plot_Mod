package com.masterplanner.infrastructure.event.gallery;

import com.masterplanner.api.event.EventType;
import com.masterplanner.infrastructure.event.base.Event;

/**
 * 图库项目选中事件
 */
public class GalleryItemSelectedEvent extends Event {
    private final String itemId;
    private final String source;

    public GalleryItemSelectedEvent(String itemId) {
        this("GalleryManager", itemId);
    }
    
    public GalleryItemSelectedEvent(String source, String itemId) {
        super(EventType.SELECTION_CHANGED);  // 使用 SELECTION_CHANGED 事件类型
        this.source = source;
        this.itemId = itemId;
    }

    /**
     * 获取选中项目的ID
     * @return 项目ID
     */
    public String getItemId() {
        return itemId;
    }
    
    @Override
    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return String.format("GalleryItemSelectedEvent[source=%s, itemId=%s]", source, itemId);
    }
} 