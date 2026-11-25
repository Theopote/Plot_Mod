package com.plot.plugin.road.pipeline.corridor;

/**
 * 清障 AIR 的三维用途。挡墙只可覆盖边坡开挖 AIR，不得侵入行车净空 / 隧道 / 立交下层。
 */
public enum ClearancePurpose {
    /** 开敞边坡挖方清障：合法截断挡墙可覆盖。 */
    SLOPE_EXCAVATION,
    /** 普通道路行车净空。 */
    ROAD_HEADROOM,
    /** 隧道洞身净空。 */
    TUNNEL_INTERIOR,
    /** 立交下层或洞门过渡净空。 */
    GRADE_SEPARATION;

    /** 仅边坡开挖 AIR 允许被截断挡墙覆盖。 */
    public boolean allowsRetainingWallOverride() {
        return this == SLOPE_EXCAVATION;
    }

    /**
     * 空气胜出时是否有权作废设计路面期望。
     * 边坡开挖 / 未标注用途不得掩盖真实缺铺；隧道/行车/立交净空开口可撤销。
     */
    public boolean authorizesSurfaceExpectationRevoke() {
        return this == TUNNEL_INTERIOR
            || this == GRADE_SEPARATION
            || this == ROAD_HEADROOM;
    }

    public static boolean authorizesSurfaceExpectationRevoke(ClearancePurpose purpose) {
        return purpose != null && purpose.authorizesSurfaceExpectationRevoke();
    }

    /** 合并同柱 AIR 时保留更强的净空保护。 */
    public static ClearancePurpose preferProtected(ClearancePurpose existing, ClearancePurpose incoming) {
        if (existing == null) {
            return incoming;
        }
        if (incoming == null) {
            return existing;
        }
        if (existing.allowsRetainingWallOverride() && !incoming.allowsRetainingWallOverride()) {
            return incoming;
        }
        if (!existing.allowsRetainingWallOverride()) {
            return existing;
        }
        return incoming;
    }
}
