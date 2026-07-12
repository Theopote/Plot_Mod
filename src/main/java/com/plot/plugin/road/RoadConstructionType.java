package com.plot.plugin.road;

/**
 * 道路施工类型：用于决策统计与桥/隧道触发判断。
 */
public enum RoadConstructionType {
    /** 正常贴地铺设 */
    ROAD,
    /** 挖方（地面高于目标高度，差值不足以触发隧道） */
    CUT,
    /** 填方（地面低于目标高度，差值不足以触发架桥） */
    FILL,
    /** 架桥 */
    BRIDGE,
    /** 挖隧道 */
    TUNNEL
}
