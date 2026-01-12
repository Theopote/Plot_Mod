package com.masterplanner.infrastructure.event.tool;

import com.masterplanner.infrastructure.event.base.Event;
import com.masterplanner.api.event.EventType;

/**
 * 工具选择事件
 */
public class ToolSelectedEvent extends Event {
    private final String toolId;
    private final String source;

    public ToolSelectedEvent(String toolId) {
        this("ToolManager", toolId);
    }
    
    public ToolSelectedEvent(String source, String toolId) {
        super(EventType.TOOL_CHANGED);
        this.source = source;
        this.toolId = toolId;
    }

    public String getToolId() {
        return toolId;
    }
    
    @Override
    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return String.format("ToolSelectedEvent[source=%s, toolId=%s]", source, toolId);
    }
} 