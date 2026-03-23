package com.plot.ui.component;

/**
 * UI组件基础接口
 * 提供基础的位置、尺寸和渲染功能
 */
public interface UIComponent extends AutoCloseable {
    /**
     * 初始化组件
     */
    void init();

    /**
     * 渲染组件
     */
    void render();
    
    /**
     * 获取X坐标
     */
    default int getX() { return 0; }
    
    /**
     * 获取Y坐标
     */
    default int getY() { return 0; }
    
    /**
     * 获取宽度
     */
    default int getWidth() { return 0; }
    
    /**
     * 获取高度
     */
    default int getHeight() { return 0; }
    
    /**
     * 设置位置
     */
    default void setPosition(int x, int y) {}
    
    /**
     * 设置尺寸
     */
    default void setSize(int width, int height) {}
    
    /**
     * 设置可见性
     */
    default void setVisible(boolean visible) {}
    
    /**
     * 是否可见
     */
    default boolean isVisible() { return true; }
    
    /**
     * 设置启用状态
     */
    default void setEnabled(boolean enabled) {}
    
    /**
     * 是否启用
     */
    default boolean isEnabled() { return true; }
    
    /**
     * 检查点是否在组件内
     */
    default boolean isInside(int mouseX, int mouseY) {
        return mouseX >= getX() && mouseX <= getX() + getWidth() &&
               mouseY >= getY() && mouseY <= getY() + getHeight();
    }
    
    /**
     * 清理资源
     */
    @Override
    void close() throws Exception;
} 