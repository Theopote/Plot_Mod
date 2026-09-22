package com.plot.plugin.powerline.ui;

/**
 * 杆塔设计器编辑范围。
 * <p>
 * {@link #LINE_INSTANCE}：从线路插件打开（{@link PoleDesignerPanel#openLineInstance}），
 * 调整选中线路杆塔实例参数，不可切换分层模式/参数化或 Profile。
 * {@link #DESIGN_TEMPLATE}：完整造型编辑（{@link PoleDesignerPanel#openTemplate} /
 * {@link PoleDesignerPanel#createTemplate}）。
 */
public enum PoleDesignerEditScope {
    LINE_INSTANCE,
    DESIGN_TEMPLATE
}
