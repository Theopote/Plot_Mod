package com.plot.plugin.road.terrain;

import com.plot.api.geometry.Vec2d;

/**
 * 固定标高的地形采样器（测试用）。
 */
public final class FlatTerrainSampler implements TerrainSampler {
    private final int elevation;
    private final boolean solid;

    public FlatTerrainSampler(int elevation) {
        this(elevation, true);
    }

    public FlatTerrainSampler(int elevation, boolean solid) {
        this.elevation = elevation;
        this.solid = solid;
    }

    @Override
    public int sampleSurfaceY(Vec2d planPoint) {
        return elevation;
    }

    @Override
    public boolean isSolidBlock(int worldX, int y, int worldZ) {
        return solid && y <= elevation;
    }
}
