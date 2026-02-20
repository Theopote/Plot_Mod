package com.masterplanner.infrastructure.event;

import com.masterplanner.infrastructure.event.base.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 事件总线，用于事件的发布和订阅
 */
public class EventBus {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventBus.class);
    private static EventBus INSTANCE;
    private final Map<Class<? extends Event>, List<EventListener>> listeners;

    private EventBus() {
        this.listeners = new ConcurrentHashMap<>();
    }

    public static EventBus getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new EventBus();
        }
        return INSTANCE;
    }

    /**
     * 发布事件
     * @param event 要发布的事件
     */
    public void publish(Event event) {
        if (event == null) return;

        LOGGER.debug("EventBus: 发布事件 {} : {}", event.getClass().getSimpleName(), event);

        List<EventListener> eventListeners = listeners.get(event.getClass());
        if (eventListeners != null && !eventListeners.isEmpty()) {
            LOGGER.debug("EventBus: 找到 {} 个监听器，准备通知", eventListeners.size());
            for (EventListener listener : eventListeners) {
                try {
                    LOGGER.debug("EventBus: 通知监听器 {} 处理事件 {}",
                        listener.getClass().getName(), event.getClass().getSimpleName());
                    listener.onEvent(event);
                    LOGGER.debug("EventBus: 监听器 {} 已处理事件 {}",
                        listener.getClass().getName(), event.getClass().getSimpleName());
                } catch (Throwable e) {
                    // 记录错误但不中断其他监听器
                    LOGGER.error("EventBus: 监听器处理事件时出错: {}", e.getMessage(), e);
                }
            }
        } else {
            LOGGER.debug("EventBus: 没有找到监听器处理事件 {}", event.getClass().getSimpleName());
        }
    }

    /**
     * 订阅事件
     * @param eventType 事件类型
     * @param listener 事件监听器
     */
    public <T extends Event> void subscribe(Class<T> eventType, EventListener listener) {
        if (eventType == null || listener == null) {
            LOGGER.error("EventBus: 无法订阅，事件类型或监听器为null");
            return;
        }

        LOGGER.debug("EventBus: 订阅事件 {} 监听器: {}", eventType.getSimpleName(), listener.getClass().getName());

        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);

        // 打印当前订阅情况
        List<EventListener> currentListeners = listeners.get(eventType);
        LOGGER.debug("EventBus: 当前 {} 有 {} 个监听器", eventType.getSimpleName(),
            (currentListeners != null ? currentListeners.size() : 0));
    }

    /**
     * 取消订阅
     * @param eventType 事件类型
     * @param listener 事件监听器
     */
    public <T extends Event> void unsubscribe(Class<T> eventType, EventListener listener) {
        List<EventListener> eventListeners = listeners.get(eventType);
        if (eventListeners != null) {
            eventListeners.remove(listener);
        }
    }
} 