package com.masterplanner.infrastructure.event.command;

import com.masterplanner.api.event.EventType;
import com.masterplanner.infrastructure.event.base.Event;

/**
 * 撤销命令事件
 */
public class UndoEvent extends Event {
    private final String source;
    
    public UndoEvent() {
        this("CommandManager");
    }
    
    public UndoEvent(String source) {
        super(EventType.UNDO);
        this.source = source;
    }
    
    @Override
    public String getSource() {
        return source;
    }
    
    @Override
    public String toString() {
        return String.format("UndoEvent[source=%s]", source);
    }
} 