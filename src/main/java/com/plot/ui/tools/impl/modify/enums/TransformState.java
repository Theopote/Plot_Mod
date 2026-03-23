package com.plot.ui.tools.impl.modify.enums;

/**
 * 变换状态枚举
 * 
 * <p>定义变换工具的不同工作状态</p>
 * 
 * @author Plot Team
 * @version 1.0 - 变换状态枚举
 */
public enum TransformState {
    IDLE("空闲", "等待开始变换"),
    SHOWING_BOUNDING_BOX("显示包围盒", "显示包围盒和控制点"),
    DRAGGING_CONTROL_POINT("拖拽控制点", "正在拖拽控制点进行变换");

    private final String displayName;
    private final String description;

    TransformState(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}