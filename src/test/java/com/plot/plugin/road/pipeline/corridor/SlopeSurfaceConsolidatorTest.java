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

    @Test
    void consolidatesCutRayWithMonotonicSteps() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        String ray = SlopeSurfaceConsolidator.rayId(new Vec2d(10, 0), new Vec2d(1, 0));
        List<SlopeProfileSample> samples = List.of(
            new SlopeProfileSample(new ColumnKey(11, 0), new Vec2d(11, 0), 94, false, "minecraft:stone", ray, 1),
            new SlopeProfileSample(new ColumnKey(12, 0), new Vec2d(12, 0), 95, false, "minecraft:stone", ray, 2),
            new SlopeProfileSample(new ColumnKey(13, 0), new Vec2d(13, 0), 97, false, "minecraft:stone", ray, 3));

        SlopeSurfaceConsolidator.apply(plan, samples, new FlatTerrainSampler(100));

        assertTrue(plan.slopeCell(new ColumnKey(11, 0)).hasCut());
        assertEquals(95, plan.slopeCell(new ColumnKey(12, 0)).cutSurfaceY());
        assertEquals(96, plan.slopeCell(new ColumnKey(13, 0)).cutSurfaceY(),
            "monotonic enforcement should limit jump to one block per step");
    }

    @Test
    void incompleteSamplesDoNotEmitSlopeCells() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey key = new ColumnKey(20, 0);
        String ray = SlopeSurfaceConsolidator.rayId(new Vec2d(18, 0), new Vec2d(1, 0));
        SlopeSurfaceConsolidator.apply(plan, List.of(
            new SlopeProfileSample(
                key, new Vec2d(20, 0), 70, false, "minecraft:stone", ray, 32, true)),
            new FlatTerrainSampler(120));

        assertNull(plan.slopeCell(key));
        assertTrue(plan.isSlopeColumnIncomplete(key));
    }

    @Test
    void ratioOneCutProducesOneBlockStepHeights() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        String ray = SlopeSurfaceConsolidator.rayId(new Vec2d(5, 0), new Vec2d(0, 1));
        SlopeSurfaceConsolidator.apply(plan, List.of(
            new SlopeProfileSample(new ColumnKey(5, 1), new Vec2d(5, 1), 63, false, "dirt", ray, 1),
            new SlopeProfileSample(new ColumnKey(5, 2), new Vec2d(5, 2), 64, false, "dirt", ray, 2)),
            new FlatTerrainSampler(70));

        assertFalse(plan.slopeCell(new ColumnKey(5, 1)).hasFill());
        assertEquals(63, plan.slopeCell(new ColumnKey(5, 1)).cutSurfaceY());
        assertEquals(64, plan.slopeCell(new ColumnKey(5, 2)).cutSurfaceY());
    }
}
