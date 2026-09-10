package com.plot.plugin.powerline.engineering.clearance;

import com.plot.plugin.powerline.geometry.ConductorSample;
import com.plot.core.terrain.TerrainSampler;

/** 导线采样点净空（供生成阶段 warning 等复用）。 */
public final class WireClearance {
    private WireClearance() {
    }

    public static double computeSampleClearance(ConductorSample sample, TerrainSampler terrain) {
        return WireClearanceMath.computeSampleClearance(sample, terrain);
    }

    public static double computeSampleClearance(
            double worldX,
            double worldY,
            double worldZ,
            com.plot.api.geometry.Vec2d planPoint,
            TerrainSampler terrain) {
        return WireClearanceMath.computeSampleClearance(
            new ConductorSample(worldX, worldY, worldZ, planPoint),
            terrain);
    }
}
