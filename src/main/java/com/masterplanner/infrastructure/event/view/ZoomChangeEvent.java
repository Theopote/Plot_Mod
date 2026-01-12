package com.masterplanner.infrastructure.event.view;

/**
 * 缩放改变事件
 */
public class ZoomChangeEvent extends ViewEvent {
    private final float zoomLevel;

    public ZoomChangeEvent(float zoomLevel) {
        super(null);
        this.zoomLevel = zoomLevel;
    }

    public float getZoomLevel() {
        return zoomLevel;
    }

    @Override
    public String toString() {
        return String.format("ZoomChangeEvent[zoomLevel=%.1f%%]", zoomLevel);
    }
} 