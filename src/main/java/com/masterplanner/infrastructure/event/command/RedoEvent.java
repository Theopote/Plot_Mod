package com.masterplanner.infrastructure.event.command;

import com.masterplanner.api.event.EventType;
import com.masterplanner.infrastructure.event.base.Event;

/**
 * 重做命令事件
 */
public class RedoEvent extends Event {
    private final String source;
    
    public RedoEvent() {
        this("CommandManager");
    }
    
    public RedoEvent(String source) {
        super(EventType.REDO);
        this.source = source;
    }
    
    @Override
    public String getSource() {
        return source;
    }
    
    @Override
    public String toString() {
        return String.format("RedoEvent[source=%s]", source);
    }
} 