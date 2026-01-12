package com.masterplanner.infrastructure.event.edit;

import com.masterplanner.api.event.EventType;
import com.masterplanner.infrastructure.event.base.Event;

public class GroupEvent extends Event {
    public enum Type {
        GROUP,
        UNGROUP
    }

    private final Type groupType;
    private final String source;

    public GroupEvent(Type groupType) {
        this("EditManager", groupType);
    }
    
    public GroupEvent(String source, Type groupType) {
        super(EventType.COMMAND_EXECUTED);
        this.source = source;
        this.groupType = groupType;
    }

    public Type getGroupType() {
        return groupType;
    }
    
    @Override
    public String getSource() {
        return source;
    }
    
    @Override
    public String toString() {
        return String.format("GroupEvent[source=%s, type=%s]", source, groupType);
    }
} 