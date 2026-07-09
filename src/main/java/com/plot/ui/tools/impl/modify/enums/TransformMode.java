package com.plot.ui.tools.impl.modify.enums;

import com.plot.utils.PlotI18n;

/**
 * 变换模式枚举
 * 定义变换工具支持的各种变换模式
 */
public enum TransformMode {
    
    FREE("FREE", "mode.plot.transform.free", "mode.plot.transform.free.desc"),
    HORIZONTAL("HORIZONTAL", "mode.plot.transform.horizontal", "mode.plot.transform.horizontal.desc"),
    VERTICAL("VERTICAL", "mode.plot.transform.vertical", "mode.plot.transform.vertical.desc"),
    UNIFORM("UNIFORM", "mode.plot.transform.uniform", "mode.plot.transform.uniform.desc"),
    ROTATION("ROTATION", "mode.plot.transform.rotation", "mode.plot.transform.rotation.desc");
    
    private final String value;
    private final String nameKey;
    private final String descKey;
    
    TransformMode(String value, String nameKey, String descKey) {
        this.value = value;
        this.nameKey = nameKey;
        this.descKey = descKey;
    }
    
    public String getValue() {
        return value;
    }
    
    public String getDisplayName() {
        return PlotI18n.modeLabel(nameKey);
    }
    
    public String getDescription() {
        return PlotI18n.modeLabel(descKey);
    }

    public static TransformMode fromValue(String value) {
        for (TransformMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("无效的变换模式: " + value);
    }
    
    @Override
    public String toString() {
        return getDisplayName();
    }
}
