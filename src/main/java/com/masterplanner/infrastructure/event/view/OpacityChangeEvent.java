package com.masterplanner.infrastructure.event.view;

/**
 * 透明度改变事件
 */
public class OpacityChangeEvent extends ViewEvent {
    private final float opacity;

    public OpacityChangeEvent(float opacity) {
        super(null);
        this.opacity = opacity;
    }

    public float getOpacity() {
        return opacity;
    }

    @Override
    public String toString() {
        return String.format("OpacityChangeEvent[opacity=%.1f%%]", opacity);
    }
} 