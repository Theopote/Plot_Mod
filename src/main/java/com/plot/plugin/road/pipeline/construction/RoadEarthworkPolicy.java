package com.plot.plugin.road.pipeline.construction;

import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelFeasibility;

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
     * 开敞放坡允许的最大侧向/路中覆土（格）。
     * <p>
     * 取隧道结构高度（净高+衬砌）再加分类覆土阈值：低于此高度按路堑放坡；
     * 达到或超过则相当于「已够一座隧道外壳+盖层」，不再露天削山。
     * 仅用净高+衬砌（默认约 6）会过严，普通路堑也会被压成垂直壁。
     */
    public static int deepDaylightCoverBlocks(ResolvedTunnelStyle tunnelStyle, int tunnelThreshold) {
        ResolvedTunnelStyle style = tunnelStyle != null ? tunnelStyle : ResolvedTunnelStyle.defaults();
        int structure = Math.max(1, style.structureHeight());
        int coverMargin = tunnelThreshold > 0
            ? tunnelThreshold
            : TunnelFeasibility.MINIMUM_COVER_BLOCKS;
        return structure + Math.max(TunnelFeasibility.MINIMUM_COVER_BLOCKS, coverMargin);
    }

    public static int deepDaylightCoverBlocks(int tunnelThreshold) {
        return deepDaylightCoverBlocks(ResolvedTunnelStyle.defaults(), tunnelThreshold);
    }

    /**
     * 是否抑制开敞切坡：隧道族/邻接隧道/棚洞，或覆土已达隧道结构高度+盖层。
     *
     * @param coverAboveDesign 路中/外缘地形标高 − 设计路面标高（格）
     * @param tunnelThreshold  隧道覆土分类阈值
     */
    public static boolean suppressOpenDaylightBatter(
            RoadConstructionType type,
            boolean nearTunnelFamily,
            int coverAboveDesign,
            int tunnelThreshold) {
        return suppressOpenDaylightBatter(
            type, nearTunnelFamily, coverAboveDesign, ResolvedTunnelStyle.defaults(), tunnelThreshold);
    }

    public static boolean suppressOpenDaylightBatter(
            RoadConstructionType type,
            boolean nearTunnelFamily,
            int coverAboveDesign,
            ResolvedTunnelStyle tunnelStyle,
            int tunnelThreshold) {
        if (nearTunnelFamily) {
            return true;
        }
        if (!allowsCorridorBatter(type)) {
            return true;
        }
        return coverAboveDesign >= deepDaylightCoverBlocks(tunnelStyle, tunnelThreshold);
    }

    /**
     * 单侧路肩外缘：切方且外缘覆土已达隧道结构高度+盖层时，该侧不开敞放坡（挂壁靠山侧）。
     * 普通路堑外缘覆土低于此值时仍正常放坡。
     */
    public static boolean suppressShoulderCutBatter(boolean isFill, int edgeCoverAboveDesign, int tunnelThreshold) {
        return suppressShoulderCutBatter(
            isFill, edgeCoverAboveDesign, ResolvedTunnelStyle.defaults(), tunnelThreshold);
    }

    public static boolean suppressShoulderCutBatter(
            boolean isFill,
            int edgeCoverAboveDesign,
            ResolvedTunnelStyle tunnelStyle,
            int tunnelThreshold) {
        if (isFill) {
            return false;
        }
        return edgeCoverAboveDesign >= deepDaylightCoverBlocks(tunnelStyle, tunnelThreshold);
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
