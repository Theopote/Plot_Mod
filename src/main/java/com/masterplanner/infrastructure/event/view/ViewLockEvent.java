package com.masterplanner.infrastructure.event.view;

/**
 * 视图锁定事件
 */
public class ViewLockEvent extends ViewEvent {
    private final boolean locked;

    public ViewLockEvent(boolean locked) {
        super(null);
        this.locked = locked;
    }

    public boolean isLocked() {
        return locked;
    }

    @Override
    public String toString() {
        return String.format("ViewLockEvent[locked=%s]", locked);
    }
} 