package com.masterplanner.api.event;

import com.masterplanner.api.geometry.Vec2d;

/**
 * 事件接口，所有事件都需要实现此接口
 */
public interface IEvent {
    /**
     * 获取事件类型
     * @return 事件类型
     */
    EventType getType();

    /**
     * 获取事件源
     * @return 事件源对象
     */
    Object getSource();

    /**
     * 获取事件时间戳
     * @return 事件发生的时间戳
     */
    long getTimestamp();

    /**
     * 事件是否已被取消
     * @return 是否已被取消
     */
    boolean isCancelled();

    /**
     * 设置事件是否被取消
     * @param cancelled 是否取消
     */
    void setCancelled(boolean cancelled);

    /**
     * 事件是否可以被取消
     * @return 是否可以被取消
     */
    boolean isCancellable();

    /**
     * 获取事件优先级
     * @return 事件优先级
     */
    EventPriority getPriority();

    /**
     * 设置事件优先级
     * @param priority 事件优先级
     */
    void setPriority(EventPriority priority);

    /**
     * 获取事件位置
     */
    Vec2d getPosition();
    
    /**
     * 获取按键代码
     */
    String getKey();
    
    /**
     * 获取输入文本
     */
    String getText();
    
    /**
     * 事件是否已处理
     */
    boolean isHandled();
    
    /**
     * 设置事件是否已处理
     */
    void setHandled(boolean handled);
}
