package com.plot.infrastructure.event;

import com.plot.infrastructure.event.base.Event;

/**
 * 事件监听器接口
 */
public interface EventListener {
    void onEvent(Event event);
} 