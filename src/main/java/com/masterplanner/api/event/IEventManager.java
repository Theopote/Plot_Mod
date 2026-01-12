package com.masterplanner.api.event;

import java.util.List;

/**
 * 事件管理器接口
 */
public interface IEventManager {
    /**
     * 注册事件监听器
     * @param eventType 事件类型
     * @param listener 监听器
     */
    void registerListener(EventType eventType, IEventListener listener);

    /**
     * 注销事件监听器
     * @param eventType 事件类型
     * @param listener 监听器
     */
    void unregisterListener(EventType eventType, IEventListener listener);

    /**
     * 触发事件
     * @param event 要触发的事件
     * @return 事件是否被取消
     */
    boolean fireEvent(IEvent event);

    /**
     * 异步触发事件
     * @param event 要触发的事件
     */
    void fireEventAsync(IEvent event);

    /**
     * 获取指定类型的所有监听器
     * @param eventType 事件类型
     * @return 监听器列表
     */
    List<IEventListener> getListeners(EventType eventType);

    /**
     * 移除指定类型的所有监听器
     * @param eventType 事件类型
     */
    void removeAllListeners(EventType eventType);

    /**
     * 移除所有监听器
     */
    void removeAllListeners();

    /**
     * 暂停事件分发
     */
    void pauseEventDispatch();

    /**
     * 恢复事件分发
     */
    void resumeEventDispatch();

    /**
     * 事件分发是否已暂停
     * @return 是否已暂停
     */
    boolean isEventDispatchPaused();

    /**
     * 获取事件队列大小
     * @return 队列中的事件数量
     */
    int getEventQueueSize();

    /**
     * 清空事件队列
     */
    void clearEventQueue();

    /**
     * 添加事件过滤器
     * @param filter 事件过滤器
     */
    void addEventFilter(IEventFilter filter);

    /**
     * 移除事件过滤器
     * @param filter 事件过滤器
     */
    void removeEventFilter(IEventFilter filter);
}
