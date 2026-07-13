package com.plot.ui.panel.tool.renderer;


/**
 * 工具选项渲染器接口
 * 定义了所有工具选项渲染器必须实现的方法
 */
public interface ToolOptionRenderer {
    /**
     * 渲染工具选项
     * @return 渲染内容的总高度
     */
    float render();

    /**
     * 在所有 Dock 窗口渲染完成后显示模态弹窗。
     */
    default void renderDeferredModals() {
    }
    
    /**
     * 初始化工具选项
     */
    void initialize();
    
    /**
     * 清理资源
     */
    void cleanup();
} 