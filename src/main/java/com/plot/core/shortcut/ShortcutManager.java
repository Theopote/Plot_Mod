package com.plot.core.shortcut;

import com.plot.api.shortcut.IShortcutListener;
import com.plot.core.log.LogManager;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 快捷键管理器
 * 负责管理和分发快捷键事件
 */
public class ShortcutManager {
    private static final ShortcutManager INSTANCE = new ShortcutManager();
    
    // 使用CopyOnWriteArrayList以支持并发修改
    private final List<IShortcutListener> listeners;
    private final Map<String, Set<IShortcutListener>> shortcutMap;
    private boolean enabled;
    private volatile boolean dispatchSuppressed;

    private ShortcutManager() {
        this.listeners = new CopyOnWriteArrayList<>();
        this.shortcutMap = new HashMap<>();
        this.enabled = true;
        this.dispatchSuppressed = false;
    }

    public static ShortcutManager getInstance() {
        return INSTANCE;
    }

    /**
     * 添加快捷键监听器
     * @param listener 要添加的监听器
     */
    public void addListener(IShortcutListener listener) {
        if (listener == null) return;

        listeners.add(listener);
        // 按优先级排序
        sortListeners();
        
        LogManager.getInstance().debug("Added shortcut listener: " + listener.getClass().getSimpleName());
    }

    /**
     * 移除快捷键监听器
     * @param listener 要移除的监听器
     */
    public void removeListener(IShortcutListener listener) {
        if (listener == null) return;

        listeners.remove(listener);
        LogManager.getInstance().debug("Removed shortcut listener: " + listener.getClass().getSimpleName());
    }

    /**
     * 注销特定快捷键的监听器
     * @param shortcut 快捷键
     * @param listener 监听器
     */
    public void unregisterShortcut(String shortcut, IShortcutListener listener) {
        if (shortcut == null || listener == null) return;

        Set<IShortcutListener> listeners = shortcutMap.get(shortcut);
        if (listeners != null) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                shortcutMap.remove(shortcut);
            }
            LogManager.getInstance().debug(String.format("Unregistered shortcut '%s' for %s",
                shortcut, listener.getClass().getSimpleName()));
        }
    }

    /**
     * 处理快捷键事件
     * @param shortcut 触发的快捷键
     * @return true 如果快捷键被处理，false 如果快捷键未被处理
     */
    public boolean handleShortcut(String shortcut) {
        if (!enabled || shortcut == null) return false;

        if (dispatchSuppressed) {
            LogManager.getInstance().debug("Shortcut dispatch suppressed, consumed: {}", shortcut);
            return true;
        }

        // 首先检查特定快捷键的监听器
        Set<IShortcutListener> specificListeners = shortcutMap.get(shortcut);
        if (specificListeners != null) {
            for (IShortcutListener listener : specificListeners) {
                try {
                    if (listener.isEnabled() && listener.onShortcutTriggered(shortcut)) {
                        LogManager.getInstance().debug(String.format("Shortcut '%s' handled by %s",
                            shortcut, listener.getClass().getSimpleName()));
                        return true;
                    }
                } catch (Throwable t) {
                    LogManager.getInstance().error(String.format(
                            "Shortcut '%s' listener '%s' threw exception",
                            shortcut,
                            listener.getClass().getSimpleName()
                    ), t);
                }
            }
        }

        // 然后检查全局监听器
        for (IShortcutListener listener : listeners) {
            try {
                if (listener.isEnabled() && listener.onShortcutTriggered(shortcut)) {
                    LogManager.getInstance().debug(String.format("Shortcut '%s' handled by %s",
                        shortcut, listener.getClass().getSimpleName()));
                    return true;
                }
            } catch (Throwable t) {
                LogManager.getInstance().error(String.format(
                        "Shortcut '%s' global listener '%s' threw exception",
                        shortcut,
                        listener.getClass().getSimpleName()
                ), t);
            }
        }

        LogManager.getInstance().debug(String.format("Shortcut '%s' was not handled", shortcut));
        return false;
    }

    /**
     * 检查快捷键管理器是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置快捷键分发抑制状态。抑制时会直接消费快捷键，防止事件穿透。
     */
    public void setDispatchSuppressed(boolean suppressed) {
        this.dispatchSuppressed = suppressed;
    }

    /**
     * 清除所有监听器
     */
    public void clear() {
        listeners.clear();
        shortcutMap.clear();
        LogManager.getInstance().debug("Cleared all shortcut listeners");
    }

    private void sortListeners() {
        listeners.sort((l1, l2) -> Integer.compare(l2.getPriority(), l1.getPriority()));
    }
}
