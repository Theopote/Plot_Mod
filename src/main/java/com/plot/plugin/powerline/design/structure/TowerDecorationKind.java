package com.plot.plugin.powerline.design.structure;

/** 塔顶/塔身装饰类型（纯视觉）。 */
public enum TowerDecorationKind {
    BEACON,
    ANTENNA,
    PLATFORM,
    WARNING_LIGHT,
    /** 蒸汽朋克塔顶齿轮环（环体 + 外齿）。 */
    GEAR_RING,
    /** 机械腰环 / 铜质平台节点（实心环框，非细杆水平环）。 */
    MECHANICAL_RING,
    /** 外挂下垂链节（纯装饰）。 */
    HANGING_CHAIN;

    public String labelKey() {
        return "plugin.powerline.design.decoration." + name().toLowerCase();
    }
}
