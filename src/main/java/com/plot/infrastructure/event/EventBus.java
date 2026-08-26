package com.plot.infrastructure.event;

import com.plot.infrastructure.event.base.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 事件总线。线程安全单例；订阅支持去重、token 与 owner 范围清理。
 */
public final class EventBus {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventBus.class);
    private static volatile EventBus INSTANCE;
    private static final Object LOCK = new Object();

    private final Map<Class<? extends Event>, CopyOnWriteArrayList<EventListener>> listeners =
        new ConcurrentHashMap<>();
    private final Map<Object, CopyOnWriteArrayList<SubscriptionToken>> ownerSubscriptions =
        new ConcurrentHashMap<>();

    private EventBus() {
    }

    public static EventBus getInstance() {
        EventBus local = INSTANCE;
        if (local == null) {
            synchronized (LOCK) {
                local = INSTANCE;
                if (local == null) {
                    INSTANCE = local = new EventBus();
                }
            }
        }
        return local;
    }

    public void publish(Event event) {
        if (event == null) {
            return;
        }

        LOGGER.debug("EventBus: 发布事件 {} : {}", event.getClass().getSimpleName(), event);

        List<EventListener> eventListeners = listeners.get(event.getClass());
        if (eventListeners == null || eventListeners.isEmpty()) {
            LOGGER.debug("EventBus: 没有找到监听器处理事件 {}", event.getClass().getSimpleName());
            return;
        }

        LOGGER.debug("EventBus: 找到 {} 个监听器，准备通知", eventListeners.size());
        for (EventListener listener : eventListeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                LOGGER.error("EventBus: 监听器处理事件时出错: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 订阅事件。同一 listener 实例对同一事件类型只注册一次。
     *
     * @return 可用于可靠取消订阅的 token（请保存 method reference / lambda 的 token）
     */
    public <T extends Event> SubscriptionToken subscribe(Class<T> eventType, EventListener listener) {
        return subscribe(null, eventType, listener);
    }

    /**
     * 带 owner 的订阅。{@link #unsubscribeOwner(Object)} 可一次性清理该 owner 的全部订阅。
     */
    public <T extends Event> SubscriptionToken subscribe(
            Object owner, Class<T> eventType, EventListener listener) {
        if (eventType == null || listener == null) {
            LOGGER.error("EventBus: 无法订阅，事件类型或监听器为null");
            return InactiveToken.INSTANCE;
        }

        CopyOnWriteArrayList<EventListener> list =
            listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());

        if (list.contains(listener)) {
            LOGGER.warn("EventBus: 忽略重复订阅 {} / {}",
                eventType.getSimpleName(), listener.getClass().getName());
            return InactiveToken.INSTANCE;
        }

        list.add(listener);
        TokenImpl token = new TokenImpl(eventType, listener, owner);
        if (owner != null) {
            ownerSubscriptions.computeIfAbsent(owner, k -> new CopyOnWriteArrayList<>()).add(token);
        }

        LOGGER.debug("EventBus: 订阅事件 {} 监听器: {} (owner={})",
            eventType.getSimpleName(),
            listener.getClass().getName(),
            owner != null ? owner.getClass().getSimpleName() : "null");
        return token;
    }

    /**
     * 按 listener 引用取消订阅。对每次新建的 method reference / lambda 无效，请改用 token。
     */
    public <T extends Event> void unsubscribe(Class<T> eventType, EventListener listener) {
        if (eventType == null || listener == null) {
            return;
        }
        List<EventListener> eventListeners = listeners.get(eventType);
        if (eventListeners != null) {
            eventListeners.remove(listener);
        }
    }

    /**
     * 取消某 owner 下的全部订阅。
     */
    public void unsubscribeOwner(Object owner) {
        if (owner == null) {
            return;
        }
        List<SubscriptionToken> tokens = ownerSubscriptions.remove(owner);
        if (tokens == null) {
            return;
        }
        for (SubscriptionToken token : new ArrayList<>(tokens)) {
            token.unsubscribe();
        }
    }

    /**
     * 清空全部订阅（运行时关闭时调用）。
     */
    public void clear() {
        listeners.clear();
        ownerSubscriptions.clear();
        LOGGER.info("EventBus: cleared all subscriptions");
    }

    private void removeTokenFromOwner(Object owner, SubscriptionToken token) {
        if (owner == null) {
            return;
        }
        CopyOnWriteArrayList<SubscriptionToken> tokens = ownerSubscriptions.get(owner);
        if (tokens == null) {
            return;
        }
        tokens.remove(token);
        if (tokens.isEmpty()) {
            ownerSubscriptions.remove(owner, tokens);
        }
    }

    private final class TokenImpl implements SubscriptionToken {
        private final Class<? extends Event> eventType;
        private final EventListener listener;
        private final Object owner;
        private volatile boolean active = true;

        private TokenImpl(Class<? extends Event> eventType, EventListener listener, Object owner) {
            this.eventType = eventType;
            this.listener = listener;
            this.owner = owner;
        }

        @Override
        public void unsubscribe() {
            if (!active) {
                return;
            }
            active = false;
            List<EventListener> eventListeners = listeners.get(eventType);
            if (eventListeners != null) {
                eventListeners.remove(listener);
            }
            removeTokenFromOwner(owner, this);
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }

    private enum InactiveToken implements SubscriptionToken {
        INSTANCE;

        @Override
        public void unsubscribe() {
        }

        @Override
        public boolean isActive() {
            return false;
        }
    }
}
