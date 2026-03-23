package com.plot.infrastructure.event.tool;

import com.plot.api.tool.ITool;
import com.plot.api.tool.ToolState;
import com.plot.infrastructure.event.base.Event;
import com.plot.api.event.EventType;

/**
 * 工具状态改变事件
 */
public class ToolStateChangedEvent extends Event {
    private final ITool tool;
    private final ToolState oldState;
    private final ToolState newState;
    private final String source;
    
    public ToolStateChangedEvent(ITool tool, ToolState oldState, ToolState newState) {
        this("ToolManager", tool, oldState, newState);
    }
    
    public ToolStateChangedEvent(String source, ITool tool, ToolState oldState, ToolState newState) {
        super(EventType.TOOL_CHANGED);
        this.source = source;
        this.tool = tool;
        this.oldState = oldState;
        this.newState = newState;
    }
    
    public ITool getTool() {
        return tool;
    }

    @Override
    public String getSource() {
        return source;
    }
    
    @Override
    public String toString() {
        return String.format("ToolStateChangedEvent[source=%s, tool=%s, old=%s, new=%s]",
            source, tool.getName(), oldState, newState);
    }
} 