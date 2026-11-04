package com.plot.plugin.road.pipeline.construction;

import com.plot.plugin.road.RoadConstructionType;

/**
 * Corridor earthwork rules by construction type: open batter vs structure-only spans.
 */
public enum RoadEarthworkPolicy {
    /** Shoulder and continuous batter to natural ground. */
    NORMAL_BATTER,
    /** No corridor batter or open subgrade fill (deck / tunnel cavity only). */
    STRUCTURE_ONLY,
    /** Connection segments; v1 uses same batter rules as {@link #NORMAL_BATTER}. */
    TRANSITION_BATTER;

    public static RoadEarthworkPolicy of(RoadConstructionType type) {
        if (type == null) {
            return NORMAL_BATTER;
        }
        if (type.isStructureInterior()) {
            return STRUCTURE_ONLY;
        }
        if (type.isPortalOrAbutment()) {
            return TRANSITION_BATTER;
        }
        return NORMAL_BATTER;
    }

    /**
     * 走廊放坡：洞身/洞门/棚洞一律禁止（有顶盖，放坡会掏穿山体）；
     * 洞门仍可走 {@link #allowsOpenCorridorClearance()} 做窄带净空，与放坡分离。
     */
    public static boolean allowsCorridorBatter(RoadConstructionType type) {
        if (type != null && (type.isTunnelFamily() || type.isRockShelter())) {
            return false;
        }
        return of(type).allowsCorridorBatter();
    }

    /**
     * 深埋禁开敞削坡的覆土下限：须<strong>远超</strong>隧道分类阈值。
     * 普通开敞切方（数格到十余格）仍应放坡，不能用 tunnelThreshold 本身当禁坡门槛。
     */
    public static int deepDaylightCoverBlocks(int tunnelThreshold) {
        if (tunnelThreshold <= 0) {
            return Integer.MAX_VALUE;
        }
        // 与棚洞「高崖」量级对齐：至少 16，或 3× 隧道阈值
        return Math.max(tunnelThreshold * 3, 16);
    }

    /**
     * 是否抑制开敞切坡：隧道族/邻接隧道/棚洞，或中心覆土远超隧道阈值（深埋不露天削山）。
     *
     * @param coverAboveDesign 路中地形标高 − 设计路面标高（格）
     * @param tunnelThreshold  隧道覆土阈值（与分类器一致）
     */
    public static boolean suppressOpenDaylightBatter(
            RoadConstructionType type,
            boolean nearTunnelFamily,
            int coverAboveDesign,
            int tunnelThreshold) {
        if (nearTunnelFamily) {
            return true;
        }
        if (!allowsCorridorBatter(type)) {
            return true;
        }
        return coverAboveDesign >= deepDaylightCoverBlocks(tunnelThreshold);
    }

    /**
     * 单侧路肩外缘：切方且外缘覆土<strong>远超</strong>隧道阈值时，该侧不开敞放坡（挂壁/半隧靠山侧）。
     * 普通路堑切坡外缘覆土常 ≥ tunnelThreshold，仍须正常放坡。
     */
    public static boolean suppressShoulderCutBatter(boolean isFill, int edgeCoverAboveDesign, int tunnelThreshold) {
        if (isFill) {
            return false;
        }
        return edgeCoverAboveDesign >= deepDaylightCoverBlocks(tunnelThreshold);
    }

    public boolean allowsCorridorBatter() {
        return this == NORMAL_BATTER || this == TRANSITION_BATTER;
    }

    public boolean allowsOpenSubgradeFill() {
        return this != STRUCTURE_ONLY;
    }

    /** Whether expandClearanceEnvelope may write open corridor ClearanceCell columns. */
    public boolean allowsOpenCorridorClearance() {
        return this != STRUCTURE_ONLY;
    }
}
