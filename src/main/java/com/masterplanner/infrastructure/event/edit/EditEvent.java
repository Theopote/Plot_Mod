package com.masterplanner.infrastructure.event.edit;

import com.masterplanner.api.event.EventType;
import com.masterplanner.infrastructure.event.base.Event;

public class EditEvent extends Event {
    public enum Type {
        CUT,
        COPY,
        PASTE
    }

    private final Type editType;
    private final String source;

    public EditEvent(Type editType) {
        this("EditManager", editType);
    }
    
    public EditEvent(String source, Type editType) {
        super(EventType.COMMAND_EXECUTED);
        this.source = source;
        this.editType = editType;
    }

    public Type getEditType() {
        return editType;
    }
    
    @Override
    public String getSource() {
        return source;
    }
    
    @Override
    public String toString() {
        return String.format("EditEvent[source=%s, type=%s]", source, editType);
    }
} 