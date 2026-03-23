package com.plot.api.shortcut;

/**
 * 快捷键监听器接口
 * 用于处理快捷键事件
 */
public interface IShortcutListener {
    /**
     * 当快捷键被触发时调用
     * @param shortcut 触发的快捷键
     * @return true 如果快捷键被处理，false 如果快捷键未被处理
     */
    boolean onShortcutTriggered(String shortcut);

    /**
     * 获取此监听器的优先级
     * 优先级越高的监听器越先收到快捷键事件
     * @return 优先级值，默认为0
     */
    default int getPriority() {
        return 0;
    }

    /**
     * 检查此监听器是否启用
     * @return true 如果监听器已启用，false 如果监听器已禁用
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * 获取此监听器的描述信息
     * @return 描述信息
     */
    default String getDescription() {
        return "";
    }
}
