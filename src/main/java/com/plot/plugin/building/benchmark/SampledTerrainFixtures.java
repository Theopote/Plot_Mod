package com.plot.plugin.building.benchmark;

import com.plot.plugin.building.site.BuildingSiteColumnSample;

import java.util.OptionalInt;

/**
 * 片区 benchmark 用合成地形列采样（无 Minecraft World）。
 */
public final class SampledTerrainFixtures {
    private SampledTerrainFixtures() {
    }

    /**
     * 轻微起伏 + 棋盘格扰动，覆盖 site analysis / terrain elevation 路径。
     */
    public static BuildingSiteColumnSample sampleColumn(int blockX, int blockZ) {
        int groundY = 64 + (blockX + blockZ) % 5 - 2 + (blockX / 12 + blockZ / 10) % 3;
        return new BuildingSiteColumnSample(groundY, groundY, OptionalInt.empty(), 0, 0, true);
    }
}
