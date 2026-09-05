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
        int structureConflictCount) {

    public boolean hasWater() {
        return waterSurfaceY != null && waterSurfaceY.isPresent();
    }
}
