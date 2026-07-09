package com.plot.ui.tools.impl.modify.enums;

import com.plot.utils.PlotI18n;

/**
 * 变换状态枚举
 * 
 * <p>定义变换工具的不同工作状态</p>
 */
public enum TransformState {
    IDLE("state.plot.transform.idle", "state.plot.transform.idle.desc"),
    SHOWING_BOUNDING_BOX("state.plot.transform.bounding_box", "state.plot.transform.bounding_box.desc"),
    DRAGGING_CONTROL_POINT("state.plot.transform.drag_control", "state.plot.transform.drag_control.desc");

    private final String nameKey;
    private final String descKey;

    TransformState(String nameKey, String descKey) {
        this.nameKey = nameKey;
        this.descKey = descKey;
    }

    public String getDisplayName() {
        return PlotI18n.modeLabel(nameKey);
    }

    public String getDescription() {
        return PlotI18n.modeLabel(descKey);
    }
}
