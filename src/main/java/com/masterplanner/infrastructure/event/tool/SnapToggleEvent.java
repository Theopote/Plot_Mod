package com.masterplanner.infrastructure.event.tool;

/**
 * 捕捉开关事件
 */
public class SnapToggleEvent extends ToolEvent {
    private final boolean enabled;

    public SnapToggleEvent(boolean enabled) {
        super("ToolManager", ToolEventType.TOOL_CONFIG, "snap_toggle");
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String toString() {
        return String.format("SnapToggleEvent[enabled=%s]", enabled);
    }
} 