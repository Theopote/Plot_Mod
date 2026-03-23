package com.plot.api.event;

/**
 * 事件监听器接口
 */
public interface IEventListener {
    /**
     * 处理事件
     * @param event 要处理的事件
     */
    void handleEvent(IEvent event);

    /**
     * 获取监听器优先级
     * @return 监听器优先级
     */
    EventPriority getPriority();

    /**
     * 获取监听器是否已启用
     * @return 是否已启用
     */
    boolean isEnabled();

    /**
     * 设置监听器是否启用
     * @param enabled 是否启用
     */
    void setEnabled(boolean enabled);

    /**
     * 获取监听器是否只监听一次
     * 如果为true，则在处理一次事件后自动移除
     * @return 是否只监听一次
     */
    boolean isOneTime();

    /**
     * 设置监听器是否只监听一次
     * @param oneTime 是否只监听一次
     */
    void setOneTime(boolean oneTime);

    /**
     * 获取监听器是否异步处理事件
     * @return 是否异步处理
     */
    boolean isAsync();

    /**
     * 设置监听器是否异步处理事件
     * @param async 是否异步处理
     */
    void setAsync(boolean async);
}
