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
    void softGroundTipSurvivesMonotonicCorrection() {
        // Soft-ground raised tip to natural ground; monotonic would otherwise snap it back to ratio Y.
        RoadCorridorPlan plan = new RoadCorridorPlan();
        SlopeSurfaceConsolidator.apply(plan, List.of(
            new SlopeProfileSample(
                new ColumnKey(10, 0), new Vec2d(10, 0), 94, false, "minecraft:stone", RAY, 1, 1.0f),
            new SlopeProfileSample(
                new ColumnKey(11, 0), new Vec2d(11, 0), 95, false, "minecraft:stone", RAY, 2, 1.0f),
            new SlopeProfileSample(
                new ColumnKey(12, 0), new Vec2d(12, 0), 98, false, "minecraft:stone", RAY, 3, 1.0f,
                SlopeConstructionOutcome.COMPLETE)),
            new FlatTerrainSampler(98));

        SlopeColumnCell tip = plan.slopeCell(new ColumnKey(12, 0));
        assertEquals(98, tip.cutSurfaceY(), "soft-ground tip must remain tied to terrain after consolidator");
        assertFalse(tip.needsRetainingFinish(), "grounded tip must not request retaining finish");
        assertEquals(0, plan.needsRetainingWallCount());
    }

    @Test
    void softGroundFailureAfterMonotonicRequestsRetaining() {
        // Tip claims COMPLETE but remains far from terrain after ratio snap → retaining.
        RoadCorridorPlan plan = new RoadCorridorPlan();
        SlopeSurfaceConsolidator.apply(plan, List.of(
            new SlopeProfileSample(
                new ColumnKey(10, 0), new Vec2d(10, 0), 90, false, "minecraft:stone", RAY, 1, 1.0f,
                SlopeConstructionOutcome.COMPLETE),
            new SlopeProfileSample(
                new ColumnKey(20, 0), new Vec2d(20, 0), 91, false, "minecraft:stone", RAY, 11, 1.0f,
                SlopeConstructionOutcome.COMPLETE)),
            new FlatTerrainSampler(120));

        SlopeColumnCell tip = plan.slopeCell(new ColumnKey(20, 0));
        assertTrue(tip.needsRetainingFinish(),
            "COMPLETE tip that cannot meet terrain after monotonic must request retaining");
        assertEquals(1, plan.needsRetainingWallCount());
    }

    @Test
    void groundedTipClearsStaleRetainingDiagnostic() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        SlopeSurfaceConsolidator.apply(plan, List.of(
            new SlopeProfileSample(
                new ColumnKey(10, 0), new Vec2d(10, 0), 96, false, "minecraft:stone", RAY, 1, 1.0f,
                SlopeConstructionOutcome.NEEDS_RETAINING_WALL),
            new SlopeProfileSample(
                new ColumnKey(11, 0), new Vec2d(11, 0), 97, false, "minecraft:stone", RAY, 2, 1.0f,
                SlopeConstructionOutcome.NEEDS_RETAINING_WALL),
            new SlopeProfileSample(
                new ColumnKey(12, 0), new Vec2d(12, 0), 98, false, "minecraft:stone", RAY, 3, 1.0f,
                SlopeConstructionOutcome.NEEDS_RETAINING_WALL)),
            new FlatTerrainSampler(98));

        assertFalse(plan.slopeCell(new ColumnKey(12, 0)).needsRetainingFinish());
        assertEquals(0, plan.needsRetainingWallCount(),
            "tip that meets terrain after monotonic must not keep retaining diagnostic");
    }

    @Test
    void waterEmbankmentAuthFollowsWinningColumnOnly() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey shared = new ColumnKey(40, 0);
        SlopeRayId causewayRay = new SlopeRayId(100L, SlopeRayId.ShoulderSide.LEFT);
        SlopeRayId ordinaryRay = new SlopeRayId(101L, SlopeRayId.ShoulderSide.LEFT);

        SlopeSurfaceConsolidator.apply(plan, List.of(
            new SlopeProfileSample(
                shared, new Vec2d(40, 0), 72, true, "dirt", causewayRay, 8, 1.0f,
                SlopeConstructionOutcome.COMPLETE, true),
            new SlopeProfileSample(
                shared, new Vec2d(40, 0), 70, true, "dirt", ordinaryRay, 2, 1.0f,
                SlopeConstructionOutcome.COMPLETE, false)),
            new FlatTerrainSampler(64));

        // Closest shoulder offset (ordinary ray offset 2) wins over causeway offset 8.
        assertEquals(70, plan.slopeCell(shared).fillSurfaceY());
        assertFalse(plan.allowsSlopeWaterFill(shared),
            "losing causeway candidate must not leave slope water auth on the column");
        assertFalse(plan.allowsRoadbedWaterFill(shared),
            "slope consolidator must not grant roadbed water auth");
    }

    @Test
    void roadbedWaterAuthDoesNotAuthorizeOrdinarySlopeFill() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey key = new ColumnKey(41, 0);
        plan.markAllowRoadbedWaterFill(key);
        assertTrue(plan.allowsRoadbedWaterFill(key));
        assertFalse(plan.allowsSlopeWaterFill(key),
            "roadbed causeway clearance must not authorize slope embankment");
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
        assertTrue(plan.slopeCell(key).needsRetainingFinish(),
            "outermost width-capped column must request retaining finish");
    }

    @Test
    void retainingFinishOnlyOnTipOfWidthCappedRay() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        SlopeSurfaceConsolidator.apply(plan, List.of(
            new SlopeProfileSample(
                new ColumnKey(10, 0), new Vec2d(10, 0), 68, false, "minecraft:stone", RAY, 1, 1.0f,
                SlopeConstructionOutcome.NEEDS_RETAINING_WALL),
            new SlopeProfileSample(
                new ColumnKey(12, 0), new Vec2d(12, 0), 70, false, "minecraft:stone", RAY, 3, 1.0f,
                SlopeConstructionOutcome.NEEDS_RETAINING_WALL)),
            new FlatTerrainSampler(120));

        assertFalse(plan.slopeCell(new ColumnKey(10, 0)).needsRetainingFinish());
        assertTrue(plan.slopeCell(new ColumnKey(12, 0)).needsRetainingFinish());
    }

    @Test
    void unresolvedRayStillEmitsPartialSlopeCells() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey key = new ColumnKey(20, 0);
        SlopeSurfaceConsolidator.apply(plan, List.of(
            new SlopeProfileSample(
                key, new Vec2d(20, 0), 70, false, "minecraft:stone", RAY, 8, 1.0f,
                SlopeConstructionOutcome.UNRESOLVED)),
            new FlatTerrainSampler(120));

        assertEquals(70, plan.slopeCell(key).cutSurfaceY());
        assertEquals(1, plan.unresolvedSlopeConstructionCount());
        assertFalse(plan.isSlopeColumnIncomplete(key));
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

    @Test
    void bandInterpolationMidCellsDoNotBecomeRetainingTips() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        // 环带中间格：相对地面高差大，若被当成完整射线末端会误判挡墙。
        ColumnKey midA = new ColumnKey(50, -4);
        ColumnKey midB = new ColumnKey(51, -5);
        SlopeRayId bandA = new SlopeRayId(-100L, SlopeRayId.ShoulderSide.RIGHT);
        SlopeRayId bandB = new SlopeRayId(-101L, SlopeRayId.ShoulderSide.RIGHT);

        SlopeSurfaceConsolidator.apply(plan, List.of(
            new SlopeProfileSample(
                midA, new Vec2d(50, -4), 64, false, "minecraft:stone", bandA, 5, 1.0f,
                SlopeConstructionOutcome.COMPLETE, false, true),
            new SlopeProfileSample(
                midB, new Vec2d(51, -5), 64, false, "minecraft:stone", bandB, 6, 1.0f,
                SlopeConstructionOutcome.COMPLETE, false, true)),
            new FlatTerrainSampler(72));

        assertEquals(0, plan.needsRetainingWallCount(),
            "band interpolation cells must not count as retaining tips");
        assertFalse(plan.slopeCell(midA).needsRetainingFinish());
        assertFalse(plan.slopeCell(midB).needsRetainingFinish());
        assertEquals(64, plan.slopeCell(midA).cutSurfaceY());
    }

    @Test
    void shoulderRetainingTipBeatsOverlappingBandComplete() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey tip = new ColumnKey(60, 0);
        SlopeRayId shoulder = new SlopeRayId(40L, SlopeRayId.ShoulderSide.RIGHT);
        SlopeRayId band = new SlopeRayId(-200L, SlopeRayId.ShoulderSide.RIGHT);

        // 真实宽度截断射线末端需要挡墙；同柱环带 COMPLETE 不得取消。
        SlopeSurfaceConsolidator.apply(plan, List.of(
            new SlopeProfileSample(
                tip, new Vec2d(60, 0), 70, false, "minecraft:stone", shoulder, 8, 1.0f,
                SlopeConstructionOutcome.NEEDS_RETAINING_WALL, false, false),
            new SlopeProfileSample(
                tip, new Vec2d(60, 0), 68, false, "minecraft:stone", band, 8, 1.0f,
                SlopeConstructionOutcome.COMPLETE, false, true)),
            new FlatTerrainSampler(90));

        assertTrue(plan.slopeCell(tip).needsRetainingFinish(),
            "real width-cap retaining tip must survive overlapping band COMPLETE");
        assertEquals(70, plan.slopeCell(tip).cutSurfaceY());
        assertEquals(1, plan.needsRetainingWallCount());
    }

    @Test
    void competingRaysGrantDaylightOnlyInsideApprovedFootprint() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        // Roadbed seed from an approved local-daylight span (rasterizeSpan marks these).
        plan.markLocalDaylightCut(new ColumnKey(10, 0));
        plan.markLocalDaylightCut(new ColumnKey(11, 0));
        plan.markLocalDaylightCut(new ColumnKey(12, 0));

        ColumnKey inside = new ColumnKey(11, 4);
        ColumnKey outside = new ColumnKey(40, 20);
        SlopeRayId daylightRay = new SlopeRayId(1L, SlopeRayId.ShoulderSide.LEFT);
        SlopeRayId plainRay = new SlopeRayId(2L, SlopeRayId.ShoulderSide.LEFT);

        SlopeSurfaceConsolidator.apply(plan, List.of(
            // Inside footprint: unauthorized winner + daylight overlap → keep auth.
            new SlopeProfileSample(
                inside, new Vec2d(11, 4), 66, false, "minecraft:dirt", plainRay, 4, 1.0f,
                SlopeConstructionOutcome.COMPLETE, false, false, false),
            new SlopeProfileSample(
                inside, new Vec2d(11, 4), 67, false, "minecraft:stone", daylightRay, 5, 1.0f,
                SlopeConstructionOutcome.COMPLETE, false, true, false),
            // Outside footprint: same overlap pattern must NOT inherit carve auth.
            new SlopeProfileSample(
                outside, new Vec2d(40, 20), 66, false, "minecraft:dirt", plainRay, 4, 1.0f,
                SlopeConstructionOutcome.COMPLETE, false, false, false),
            new SlopeProfileSample(
                outside, new Vec2d(40, 20), 67, false, "minecraft:stone", daylightRay, 5, 1.0f,
                SlopeConstructionOutcome.COMPLETE, false, true, false)),
            new FlatTerrainSampler(80));

        assertTrue(plan.allowsLocalDaylightCut(inside),
            "overlap inside approved daylight footprint must keep carve auth");
        assertFalse(plan.allowsLocalDaylightCut(outside),
            "overlap outside approved footprint must not inherit carve auth");
        assertTrue(plan.slopeCell(inside).hasCut());
        assertTrue(plan.slopeCell(outside).hasCut());
    }
}
