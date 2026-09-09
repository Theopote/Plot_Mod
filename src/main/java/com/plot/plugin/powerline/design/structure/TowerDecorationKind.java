package com.plot.plugin.powerline.design.structure;

/** 塔顶/塔身装饰类型（纯视觉）。 */
public enum TowerDecorationKind {
    BEACON,
    ANTENNA,
    PLATFORM,
    WARNING_LIGHT;

    public String labelKey() {
        return "plugin.powerline.design.decoration." + name().toLowerCase();
    }
}
