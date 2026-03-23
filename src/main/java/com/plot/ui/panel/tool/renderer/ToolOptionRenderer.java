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
     * 初始化工具选项
     */
    void initialize();
    
    /**
     * 清理资源
     */
    void cleanup();
} 