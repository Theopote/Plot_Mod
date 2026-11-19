package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalPlacementAcceptanceValidatorTest {

    @Test
    void mergeRepairClearsStaleEdgeMissingCount() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        // 边级中间态曾记过缺口，但合并后路口已补铺。
        aggregate.missingRoadSurfaceCount = 4;
        BlockPos hole = new BlockPos(2, 64, 0);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            hole, "minecraft:stone", RoadSolidLayer.ROAD, "edge"));
        aggregate.recordPlacementOverride(hole, "minecraft:air", "minecraft:stone");
        aggregate.placementLayers.put(hole, RoadSolidLayer.ROAD);

        FinalPlacementAcceptanceValidator.Report report =
            FinalPlacementAcceptanceValidator.applyToAggregate(aggregate);

        assertEquals(0, report.missingRoadSurfaceCount());
        assertEquals(0, aggregate.missingRoadSurfaceCount);
        assertFalse(report.hasBlockingIssues());
    }

    @Test
    void mergeDamageReportsFinalMissingSurface() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.missingRoadSurfaceCount = 0;
        BlockPos hole = new BlockPos(3, 64, 1);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            hole, "minecraft:stone", RoadSolidLayer.ROAD, "edge"));
        // 合并/接缝后设计位置变空气。
        aggregate.recordPlacementOverride(hole, "minecraft:stone", "minecraft:air");
        aggregate.placementLayers.put(hole, RoadSolidLayer.ROAD);

        FinalPlacementAcceptanceValidator.Report report =
            FinalPlacementAcceptanceValidator.applyToAggregate(aggregate);

        assertTrue(report.missingRoadSurfaceCount() >= 1);
        assertTrue(aggregate.missingRoadSurfaceCount >= 1);
        assertTrue(FinalPlacementAcceptanceValidator.hasBlockingIssues(aggregate));
    }

    @Test
    void wrongElevationCountsAsBlockingSurfaceDefect() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos expected = new BlockPos(0, 64, 0);
        BlockPos elsewhere = new BlockPos(0, 66, 0);
        aggregate.recordExpectedRoadSurface(new ExpectedRoadSurface(
            expected, "minecraft:stone", RoadSolidLayer.ROAD, "edge"));
        aggregate.recordPlacementOverride(elsewhere, "minecraft:air", "minecraft:stone");
        aggregate.placementLayers.put(elsewhere, RoadSolidLayer.ROAD);

        FinalPlacementAcceptanceValidator.Report report =
            FinalPlacementAcceptanceValidator.validate(aggregate);

        assertEquals(0, report.missingRoadSurfaceCount());
        assertEquals(1, report.wrongElevationOrMaterialCount());
        assertTrue(report.hasBlockingIssues());
    }
}
