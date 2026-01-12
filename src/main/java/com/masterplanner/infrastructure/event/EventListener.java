package com.masterplanner.infrastructure.event;

import com.masterplanner.infrastructure.event.base.Event;

/**
 * 事件监听器接口
 */
public interface EventListener {
    void onEvent(Event event);
} 