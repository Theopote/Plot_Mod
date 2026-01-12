package com.masterplanner.api.event;

/**
 * 事件过滤器接口
 */
public interface IEventFilter {
    /**
     * 过滤事件
     * @param event 要过滤的事件
     * @return 是否允许事件继续传播
     */
    boolean filterEvent(IEvent event);

    /**
     * 获取过滤器优先级
     * @return 过滤器优先级
     */
    EventPriority getPriority();

    /**
     * 过滤器是否已启用
     * @return 是否已启用
     */
    boolean isEnabled();

    /**
     * 设置过滤器是否启用
     * @param enabled 是否启用
     */
    void setEnabled(boolean enabled);

    /**
     * 获取过滤器描述
     * @return 过滤器描述
     */
    String getDescription();
}
