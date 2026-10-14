package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlopeSurfaceConsolidatorTest {

    private static final SlopeRayId RAY = new SlopeRayId(1L, SlopeRayId.ShoulderSide.LEFT);

    @Test
    void consolidatesCutRayWithMonotonicSteps() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        List<SlopeProfileSample> samples = List.of(
            new SlopeProfileSample(new ColumnKey(11, 0), new Vec2d(11, 0), 94, false, "minecraft:stone", RAY, 1, 1.0f),
            new SlopeProfileSample(new ColumnKey(12, 0), new Vec2d(12, 0), 95, false, "minecraft:stone", RAY, 2, 1.0f),
            new SlopeProfileSample(new ColumnKey(13, 0), new Vec2d(13, 0), 97, false, "minecraft:stone", RAY, 3, 1.0f));

        SlopeSurfaceConsolidator.apply(plan, samples, new FlatTerrainSampler(100));

        assertTrue(plan.slopeCell(new ColumnKey(11, 0)).hasCut());
        assertEquals(95, plan.slopeCell(new ColumnKey(12, 0)).cutSurfaceY());
        assertEquals(96, plan.slopeCell(new ColumnKey(13, 0)).cutSurfaceY(),
            "monotonic enforcement should limit jump toward angle target");
    }

    @Test
    void needsRetainingWallRayStillEmitsPartialSlope() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey key = new ColumnKey(20, 0);
        SlopeSurfaceConsolidator.apply(plan, List.of(
            new SlopeProfileSample(
                key, new Vec2d(20, 0), 70, false, "minecraft:stone", RAY, 8, 1.0f,
                SlopeConstructionOutcome.NEEDS_RETAINING_WALL)),
            new FlatTerrainSampler(120));

        assertEquals(70, plan.slopeCell(key).cutSurfaceY());
        assertEquals(1, plan.needsRetainingWallCount());
        assertFalse(plan.isSlopeColumnIncomplete(key));
    }

    @Test
    void unresolvedRayDoesNotEmitSlopeCells() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey key = new ColumnKey(20, 0);
        SlopeSurfaceConsolidator.apply(plan, List.of(
            new SlopeProfileSample(
                key, new Vec2d(20, 0), 70, false, "minecraft:stone", RAY, 8, 1.0f,
                SlopeConstructionOutcome.UNRESOLVED)),
            new FlatTerrainSampler(120));

        assertNull(plan.slopeCell(key));
        assertTrue(plan.isSlopeColumnIncomplete(key));
    }

    @Test
    void ratioOneCutProducesOneBlockStepHeights() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        SlopeRayId ray = new SlopeRayId(2L, SlopeRayId.ShoulderSide.RIGHT);
        SlopeSurfaceConsolidator.apply(plan, List.of(
            new SlopeProfileSample(new ColumnKey(5, 1), new Vec2d(5, 1), 63, false, "dirt", ray, 1, 1.0f),
            new SlopeProfileSample(new ColumnKey(5, 2), new Vec2d(5, 2), 64, false, "dirt", ray, 2, 1.0f)),
            new FlatTerrainSampler(70));

        assertFalse(plan.slopeCell(new ColumnKey(5, 1)).hasFill());
        assertEquals(63, plan.slopeCell(new ColumnKey(5, 1)).cutSurfaceY());
        assertEquals(64, plan.slopeCell(new ColumnKey(5, 2)).cutSurfaceY());
    }

    @Test
    void differentStationRaysDoNotCrossAdjustMonotonicSteps() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        SlopeRayId rayA = new SlopeRayId(10L, SlopeRayId.ShoulderSide.LEFT);
        SlopeRayId rayB = new SlopeRayId(11L, SlopeRayId.ShoulderSide.LEFT);

        SlopeSurfaceConsolidator.apply(plan, List.of(
            new SlopeProfileSample(new ColumnKey(11, 0), new Vec2d(11, 0), 94, false, "stone", rayA, 1, 1.0f),
            new SlopeProfileSample(new ColumnKey(12, 0), new Vec2d(12, 0), 95, false, "stone", rayA, 2, 1.0f),
            new SlopeProfileSample(new ColumnKey(13, 0), new Vec2d(13, 0), 97, false, "stone", rayA, 3, 1.0f),
            new SlopeProfileSample(new ColumnKey(21, 0), new Vec2d(21, 0), 70, false, "stone", rayB, 1, 1.0f),
            new SlopeProfileSample(new ColumnKey(22, 0), new Vec2d(22, 0), 71, false, "stone", rayB, 2, 1.0f),
            new SlopeProfileSample(new ColumnKey(23, 0), new Vec2d(23, 0), 97, false, "stone", rayB, 3, 1.0f)),
            new FlatTerrainSampler(100));

        assertEquals(96, plan.slopeCell(new ColumnKey(13, 0)).cutSurfaceY());
        assertEquals(72, plan.slopeCell(new ColumnKey(23, 0)).cutSurfaceY());
    }

    @Test
    void unresolvedRayDoesNotRemoveCompleteRayOnSameColumn() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey key = new ColumnKey(30, 0);
        SlopeRayId completeRay = new SlopeRayId(20L, SlopeRayId.ShoulderSide.LEFT);
        SlopeRayId unresolvedRay = new SlopeRayId(21L, SlopeRayId.ShoulderSide.LEFT);

        SlopeSurfaceConsolidator.apply(plan, List.of(
            new SlopeProfileSample(key, new Vec2d(30, 0), 68, false, "stone", completeRay, 2, 1.0f),
            new SlopeProfileSample(
                key, new Vec2d(30, 0), 55, false, "stone", unresolvedRay, 1, 1.0f,
                SlopeConstructionOutcome.UNRESOLVED)),
            new FlatTerrainSampler(80));

        assertEquals(68, plan.slopeCell(key).cutSurfaceY());
        assertFalse(plan.isSlopeColumnIncomplete(key));
    }

    @Test
    void overlappingColumnsPreferClosestShoulderOffset() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey key = new ColumnKey(40, 0);
        SlopeRayId nearRay = new SlopeRayId(30L, SlopeRayId.ShoulderSide.LEFT);
        SlopeRayId farRay = new SlopeRayId(31L, SlopeRayId.ShoulderSide.LEFT);

        SlopeSurfaceConsolidator.apply(plan, List.of(
            new SlopeProfileSample(key, new Vec2d(40, 0), 72, false, "stone", nearRay, 2, 1.0f),
            new SlopeProfileSample(key, new Vec2d(40, 0), 60, false, "stone", farRay, 8, 1.0f)),
            new FlatTerrainSampler(80));

        assertEquals(72, plan.slopeCell(key).cutSurfaceY());
    }
}
