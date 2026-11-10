package com.plot.plugin.road.tunnel;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;

/**
 * 隧道世界列施工形态：横断面生成、洞顶稳定、走廊清障与路口连通共用同一判定，
 * 避免「一侧跳过开挖、另一侧又掏空」的浅覆土失稳。
 */
public final class TunnelColumnConstruction {
    private TunnelColumnConstruction() {
    }

    /**
     * 单列施工形态。
     * <ul>
     *   <li>{@link #ENCLOSED}：允许封闭洞腔、侧墙、顶衬与内部清障</li>
     *   <li>{@link #PORTAL_TRANSITION}：洞口过渡，按露天净空处理，不得掏空自然地表支撑</li>
     *   <li>{@link #OPEN}：开敞道路，不生成地下封闭衬砌开挖</li>
     * </ul>
     */
    public enum Mode {
        ENCLOSED,
        PORTAL_TRANSITION,
        OPEN
    }

    /**
     * 封闭洞腔前提：设计顶衬最高格必须严格低于自然工程地表。
     */
    public static boolean allowsClosedCavity(
            TerrainSampler terrain,
            Vec2d planPoint,
            int designedRoofTopY) {
        if (terrain == null || planPoint == null || designedRoofTopY == Integer.MIN_VALUE) {
            return false;
        }
        return designedRoofTopY < terrain.sampleSurfaceY(planPoint);
    }

    /**
     * @param portalCell 该列是否来自 PORTAL 施工模式（否则浅覆土视为 {@link Mode#OPEN}）
     */
    public static Mode resolve(
            boolean portalCell,
            TerrainSampler terrain,
            Vec2d planPoint,
            int designedRoofTopY) {
        if (!allowsClosedCavity(terrain, planPoint, designedRoofTopY)) {
            return portalCell ? Mode.PORTAL_TRANSITION : Mode.OPEN;
        }
        return Mode.ENCLOSED;
    }
}
