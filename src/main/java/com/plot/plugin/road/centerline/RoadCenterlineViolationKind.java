package com.plot.plugin.road.centerline;

/**
 * 单条逻辑道路中心线形状违反类型。
 * <p>
 * 平面真交（figure-8、盘山路投影交叉、立交匝道）由
 * {@link com.plot.plugin.road.crossing.RoadStackCrossing} 处理，不再作为形状违规。
 * 仍诊断：共线折返重叠、内部分叉等破坏桩号链的情况。
 *
 * @see RoadCenterlineShapeValidator
 */
public enum RoadCenterlineViolationKind {
    /**
     * @deprecated 平面自交是合法设计（立交/盘山/环路汇合），由 StackCrossing 承接；
     *             校验器不再产出此类型，保留枚举仅兼容旧数据/文案。
     */
    @Deprecated
    SELF_INTERSECTION,
    /** 中心线折线存在共线重叠（同一走廊折返重复走，非近距平行回头弯）。 */
    SELF_OVERLAP,
    /** 道路子图非 open chain（内部分叉或隐式闭环）。 */
    NON_LINEAR_ROAD_TOPOLOGY
}
