package com.masterplanner.infrastructure.event.tool;

import com.masterplanner.api.tool.ITool;
import com.masterplanner.api.tool.ToolState;
import com.masterplanner.infrastructure.event.base.Event;
import com.masterplanner.api.event.EventType;

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
    
    public ToolState getOldState() {
        return oldState;
    }
    
    public ToolState getNewState() {
        return newState;
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