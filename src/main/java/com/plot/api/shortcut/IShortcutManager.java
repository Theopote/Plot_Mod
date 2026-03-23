package com.plot.api.shortcut;

import java.util.List;

/**
 * 快捷键管理器接口
 */
public interface IShortcutManager {
    /**
     * 注册快捷键
     * @param shortcut 快捷键
     * @return 是否注册成功
     */
    boolean registerShortcut(IShortcut shortcut);

    /**
     * 注销快捷键
     * @param shortcut 快捷键
     */
    void unregisterShortcut(IShortcut shortcut);

    /**
     * 根据ID获取快捷键
     * @param id 快捷键ID
     * @return 快捷键
     */
    IShortcut getShortcut(String id);

    /**
     * 获取所有快捷键
     * @return 快捷键列表
     */
    List<IShortcut> getAllShortcuts();

    /**
     * 处理按键事件
     * @param keyCode 按键代码
     * @param scanCode 扫描码
     * @param modifiers 修饰键
     * @return 是否处理了事件
     */
    boolean handleKeyEvent(int keyCode, int scanCode, int modifiers);

    /**
     * 启用快捷键
     * @param id 快捷键ID
     */
    void enableShortcut(String id);

    /**
     * 禁用快捷键
     * @param id 快捷键ID
     */
    void disableShortcut(String id);

    /**
     * 检查快捷键是否启用
     * @param id 快捷键ID
     * @return 是否启用
     */
    boolean isShortcutEnabled(String id);

    /**
     * 获取快捷键冲突
     * @param shortcut 快捷键
     * @return 冲突的快捷键列表
     */
    List<IShortcut> getConflicts(IShortcut shortcut);

    /**
     * 保存快捷键配置
     */
    void saveShortcuts();

    /**
     * 加载快捷键配置
     */
    void loadShortcuts();

    /**
     * 重置快捷键到默认值
     */
    void resetToDefault();

    /**
     * 添加快捷键监听器
     * @param listener 监听器
     */
    void addShortcutListener(IShortcutListener listener);

    /**
     * 移除快捷键监听器
     * @param listener 监听器
     */
    void removeShortcutListener(IShortcutListener listener);
}
