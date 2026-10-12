package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.solid.RoadSolidLayer;
import net.minecraft.util.math.BlockPos;

/**
 * Expected final world block role for corridor integrity checks.
 */
record RoadBlockExpectation(
        BlockPos position,
        ExpectedRole role,
        RoadSolidLayer expectedLayer,
        String expectedMaterialId) {

    enum ExpectedRole {
        /** Planned road / shoulder / sidewalk / bike lane surface. */
        SURFACE,
        /** Cleared overhead or design-surface obstruction (air or structural replacement). */
        CLEARANCE
    }

    static RoadBlockExpectation surface(
            ColumnKey column,
            int y,
            RoadSolidLayer layer,
            String materialId) {
        return new RoadBlockExpectation(
            new BlockPos(column.worldX(), y, column.worldZ()),
            ExpectedRole.SURFACE,
            layer,
            materialId);
    }

    static RoadBlockExpectation clearance(ColumnKey column, int y) {
        return new RoadBlockExpectation(
            new BlockPos(column.worldX(), y, column.worldZ()),
            ExpectedRole.CLEARANCE,
            null,
            "minecraft:air");
    }
}
