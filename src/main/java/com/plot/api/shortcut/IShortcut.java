package com.plot.api.shortcut;

/**
 * 快捷键接口
 */
public interface IShortcut {
    /**
     * 获取快捷键ID
     * @return 快捷键ID
     */
    String getId();

    /**
     * 获取快捷键名称
     * @return 快捷键名称
     */
    String getName();

    /**
     * 获取快捷键描述
     * @return 快捷键描述
     */
    String getDescription();

    /**
     * 获取快捷键组
     * @return 快捷键组
     */
    String getGroup();

    /**
     * 获取按键代码
     * @return 按键代码
     */
    int getKeyCode();

    /**
     * 设置按键代码
     * @param keyCode 按键代码
     */
    void setKeyCode(int keyCode);

    /**
     * 获取修饰键
     * @return 修饰键
     */
    int getModifiers();

    /**
     * 设置修饰键
     * @param modifiers 修饰键
     */
    void setModifiers(int modifiers);

    /**
     * 检查快捷键是否匹配
     * @param keyCode 按键代码
     * @param modifiers 修饰键
     * @return 是否匹配
     */
    boolean matches(int keyCode, int modifiers);

    /**
     * 执行快捷键操作
     * @return 是否执行成功
     */
    boolean execute();

    /**
     * 获取快捷键是否启用
     * @return 是否启用
     */
    boolean isEnabled();

    /**
     * 设置快捷键是否启用
     * @param enabled 是否启用
     */
    void setEnabled(boolean enabled);

    /**
     * 获取快捷键显示文本
     * @return 显示文本
     */
    String getDisplayText();

    /**
     * 克隆快捷键
     * @return 快捷键副本
     */
    IShortcut clone();
}
