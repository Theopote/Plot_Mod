package com.plot.plugin.building.site;

import java.util.OptionalInt;

/**
 * 单列场地采样缓存。
 */
public record BuildingSiteColumnSample(
        int groundY,
        int rawSurfaceY,
        OptionalInt waterSurfaceY,
        int naturalDecorationCount,
        int structureConflictCount,
        boolean chunkLoaded) {

    /** 兼容旧调用：默认视为已加载区块。 */
    public BuildingSiteColumnSample(
            int groundY,
            int rawSurfaceY,
            OptionalInt waterSurfaceY,
            int naturalDecorationCount,
            int structureConflictCount) {
        this(groundY, rawSurfaceY, waterSurfaceY, naturalDecorationCount, structureConflictCount, true);
    }

    public boolean hasWater() {
        return waterSurfaceY != null && waterSurfaceY.isPresent();
    }
}
