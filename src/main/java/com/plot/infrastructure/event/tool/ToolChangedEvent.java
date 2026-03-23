package com.plot.infrastructure.event.tool;

import com.plot.infrastructure.event.base.Event;
import com.plot.api.event.EventType;

/**
 * 工具切换事件
 */
public class ToolChangedEvent extends Event {
    private final String toolName;
    private final String source;

    public ToolChangedEvent(String toolName) {
        this("ToolManager", toolName);
    }
    
    public ToolChangedEvent(String source, String toolName) {
        super(EventType.TOOL_CHANGED);
        this.source = source;
        this.toolName = toolName;
    }

    public String getToolName() {
        return toolName;
    }
    
    @Override
    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return String.format("ToolChangedEvent[source=%s, toolName=%s]", source, toolName);
    }
} 