package com.masterplanner.infrastructure.event.tool;

/**
 * 工具激活事件
 */
public class ToolActivatedEvent extends ToolEvent {
    private final String previousToolId;
    
    public ToolActivatedEvent(String toolId, String previousToolId) {
        super("ToolManager", ToolEventType.TOOL_SELECT, toolId);
        this.previousToolId = previousToolId;
    }
    
    public String getPreviousToolId() {
        return previousToolId;
    }
    
    @Override
    public String toString() {
        return String.format("ToolActivatedEvent[tool=%s, previous=%s]",
            getToolId(), previousToolId);
    }
} 