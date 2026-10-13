package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlopeShoulderTerrainTraceValidatorTest {

    @Test
    void continuousSlopeMaskHasNoShoulderTraceGap() {
        RoadCorridorPlan plan = cutPlanWithContinuousSlope();
        int gaps = SlopeShoulderTerrainTraceValidator.countShoulderToTerrainGaps(
            plan, descendingGround());
        assertEquals(0, gaps);
    }

    @Test
    void missingSlopeColumnOnCutTraceIsDetected() {
        RoadCorridorPlan plan = cutPlanWithContinuousSlope();
        plan.slopeEntries(); // ensure built
        // Remove one interior slope column to simulate a break in the batter mask.
        ColumnKey gap = new ColumnKey(12, 6);
        assertTrue(plan.slopeCell(gap) != null);
        plan = planWithoutSlopeColumn(plan, gap);

        int gaps = SlopeShoulderTerrainTraceValidator.countShoulderToTerrainGaps(
            plan, descendingGround());
        assertTrue(gaps > 0);
    }

    private static RoadCorridorPlan cutPlanWithContinuousSlope() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        for (int x = 0; x <= 20; x++) {
            for (int z = -1; z <= 1; z++) {
                plan.surfaceMask().put(
                    new ColumnKey(x, z),
                    new SurfaceCell(new Vec2d(x, z), 64,
                        com.plot.plugin.road.solid.RoadSolidLayer.ROAD, "minecraft:stone"));
            }
            for (int z = -20; z <= -2; z++) {
                plan.slopeCellOrCreate(new ColumnKey(x, z), new Vec2d(x, z))
                    .mergeCut(64 - z, "minecraft:stone");
            }
            for (int z = 2; z <= 20; z++) {
                plan.slopeCellOrCreate(new ColumnKey(x, z), new Vec2d(x, z))
                    .mergeCut(64 + z, "minecraft:stone");
            }
        }
        return plan;
    }

    private static RoadCorridorPlan planWithoutSlopeColumn(RoadCorridorPlan plan, ColumnKey remove) {
        RoadCorridorPlan copy = new RoadCorridorPlan();
        for (var entry : plan.surfaceMask().entries()) {
            copy.surfaceMask().put(entry.getKey(), entry.getValue());
        }
        for (var entry : plan.slopeEntries()) {
            if (!entry.getKey().equals(remove)) {
                SlopeColumnCell cell = entry.getValue();
                SlopeColumnCell created = copy.slopeCellOrCreate(entry.getKey(), cell.planPoint());
                if (cell.hasCut()) {
                    created.mergeCut(cell.cutSurfaceY(), cell.cutMaterialId());
                }
                if (cell.hasFill()) {
                    created.mergeFill(cell.fillSurfaceY(), cell.fillMaterialId());
                }
            }
        }
        return copy;
    }

    /** Ground drops with +Z so a finite batter reaches natural terrain in tests. */
    private static TerrainSampler descendingGround() {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(com.plot.api.geometry.Vec2d planPoint) {
                return (int) Math.max(64, 90 - Math.abs(planPoint.y) * 3);
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= sampleSurfaceY(new com.plot.api.geometry.Vec2d(x, z));
            }
        };
    }
}
