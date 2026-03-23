package com.plot.ui.toolbar;

/**
 * 工具面板配置类
 * 提供工具面板布局和交互的配置选项
 */
public class ToolPanelConfig {
    
    /**
     * 是否启用工具组折叠功�?
     * 设置�?true 可启用组标题和折�?展开功能
     * 设置�?false 保持传统的简洁布局
     */
    public static final boolean ENABLE_COLLAPSIBLE_GROUPS = false;
    
    /**
     * 是否启用响应式布局
     * 启用后工具栏会根据可用宽度自动调整列�?
     */
    public static final boolean ENABLE_RESPONSIVE_LAYOUT = true;
    
    /**
     * 最大列数限�?
     * 在宽屏设备上最多显示的列数
     */
    public static final int MAX_COLUMNS = 0; // 0 表示不限制，自动根据宽度计算

    /**
     * 是否显示工具提示
     */
    public static final boolean SHOW_TOOLTIPS = true;

    /**
     * 工具组分隔符样式
     * 0: 简单分隔线
     * 1: 带缩进的分隔线（默认�?
     * 2: 隐藏分隔�?
     */
    public static final int SEPARATOR_STYLE = 1;

    /**
     * 是否使用紧凑布局
     * 紧凑布局减少间距，适合小屏�?
     */
    public static final boolean COMPACT_LAYOUT = false;
}
