package com.plot.plugin.powerline.engineering.clearance;

import com.plot.plugin.powerline.geometry.ConductorSample;
import com.plot.core.terrain.TerrainSampler;

/** 导线采样点与地形/障碍物的净空计算。 */
final class WireClearanceMath {
    private static final int SCAN_BELOW_WIRE_BLOCKS = 32;
    private static final int SCAN_ABOVE_COLUMN_BLOCKS = 1;

    private WireClearanceMath() {
    }

    /**
     * 导线世界 Y 到其下方最高障碍体顶面 (y+1) 的垂直净空。
     * 无障碍时返回 {@link Double#MAX_VALUE}。
     */
    static double computeSampleClearance(ConductorSample sample, TerrainSampler terrain) {
        if (sample == null || terrain == null) {
            return Double.MAX_VALUE;
        }
        int blockX = floor(sample.worldX());
        int blockZ = floor(sample.worldZ());
        double wireY = sample.worldY();

        int columnTop = terrain.sampleColumnTopY(sample.planPoint());
        int scanHigh = Math.max(floor(wireY) + SCAN_ABOVE_COLUMN_BLOCKS, columnTop);
        int scanLow = Math.max(floor(wireY) - SCAN_BELOW_WIRE_BLOCKS, columnTop - 64);

        int maxObstructionTop = Integer.MIN_VALUE;
        for (int y = scanHigh; y >= scanLow; y--) {
            if (terrain.isWireObstruction(blockX, y, blockZ)) {
                maxObstructionTop = Math.max(maxObstructionTop, y + 1);
            }
        }
        if (maxObstructionTop == Integer.MIN_VALUE) {
            return Double.MAX_VALUE;
        }
        return wireY - maxObstructionTop;
    }

    static int obstructionTopY(ConductorSample sample, TerrainSampler terrain) {
        if (sample == null || terrain == null) {
            return 0;
        }
        int blockX = floor(sample.worldX());
        int blockZ = floor(sample.worldZ());
        double wireY = sample.worldY();
        int columnTop = terrain.sampleColumnTopY(sample.planPoint());
        int scanHigh = Math.max(floor(wireY) + SCAN_ABOVE_COLUMN_BLOCKS, columnTop);
        int scanLow = Math.max(floor(wireY) - SCAN_BELOW_WIRE_BLOCKS, columnTop - 64);

        int maxObstructionTop = Integer.MIN_VALUE;
        for (int y = scanHigh; y >= scanLow; y--) {
            if (terrain.isWireObstruction(blockX, y, blockZ)) {
                maxObstructionTop = Math.max(maxObstructionTop, y + 1);
            }
        }
        return maxObstructionTop == Integer.MIN_VALUE ? 0 : maxObstructionTop;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
