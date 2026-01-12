package com.masterplanner.infrastructure.event.view;

import com.masterplanner.api.event.EventType;

/**
 * 相机切换事件
 */
public class CameraToggleEvent extends ViewEvent {
    
    public CameraToggleEvent() {
        super("CameraManager", EventType.VIEW_CHANGED);  // 使用 VIEW_CHANGED 事件类型，因为相机切换会影响视图
    }
    
    public CameraToggleEvent(String source) {
        super(source, EventType.VIEW_CHANGED);
    }

    @Override
    public String toString() {
        return String.format("CameraToggleEvent[source=%s]", source);
    }
} 