package com.plot.infrastructure.event.tool;

import com.plot.infrastructure.event.base.Event;
import com.plot.api.event.EventType;

/**
 * 工具状态更新事件
 */
public class ToolStatusEvent extends Event {
    private final String toolId;
    private final String message;
    private final String source;

    public ToolStatusEvent(String toolId, String message) {
        this("ToolManager", toolId, message);
    }
    
    public ToolStatusEvent(String source, String toolId, String message) {
        super(EventType.TOOL_CHANGED);
        this.source = source;
        this.toolId = toolId;
        this.message = message;
    }

    public String getToolId() {
        return toolId;
    }

    public String getMessage() {
        return message;
    }
    
    @Override
    public String getSource() {
        return source;
    }
    
    @Override
    public String toString() {
        return String.format("ToolStatusEvent[source=%s, toolId=%s, message=%s]", 
            source, toolId, message);
    }
} 