package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadGenerationIntegrityValidatorTest {

    @Test
    void cleanPlanAndSolidsPassValidation() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.surfaceMask().put(
            new ColumnKey(0, 0),
            new SurfaceCell(point, 64, RoadSolidLayer.ROAD, "minecraft:stone"));

        RoadSolidModel solids = new RoadSolidModel();
        solids.add(point, 64, RoadSolidLayer.ROAD, "minecraft:stone");

        RoadGenerationIntegrityValidator.IntegrityReport report =
            RoadGenerationIntegrityValidator.validate(plan, solids, new FlatTerrainSampler(64));

        assertTrue(report.isClean());
        assertEquals(0, report.missingRoadSurfaceCount());
        assertEquals(0, report.unclearedObstructionCount());
    }

    @Test
    void missingSurfaceIsDetected() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.surfaceMask().put(
            new ColumnKey(0, 0),
            new SurfaceCell(point, 64, RoadSolidLayer.ROAD, "minecraft:stone"));

        RoadGenerationIntegrityValidator.IntegrityReport report =
            RoadGenerationIntegrityValidator.validate(plan, new RoadSolidModel(), new FlatTerrainSampler(64));

        assertEquals(1, report.missingRoadSurfaceCount());
    }

    @Test
    void unclearedObstructionIsDetected() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.putClearance(
            new ColumnKey(0, 0),
            new ClearanceCell(point, 64, 66, RoadCorridorConstructionMode.OPEN, null));

        TerrainSampler terrain = elevatedColumn(66);

        RoadGenerationIntegrityValidator.IntegrityReport report =
            RoadGenerationIntegrityValidator.validate(plan, new RoadSolidModel(), terrain);

        assertEquals(2, report.unclearedObstructionCount());
    }

    @Test
    void clearedObstructionPassesValidation() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.putClearance(
            new ColumnKey(0, 0),
            new ClearanceCell(point, 64, 66, RoadCorridorConstructionMode.OPEN, null));

        TerrainSampler terrain = elevatedColumn(66);

        RoadSolidModel solids = new RoadSolidModel();
        solids.add(point, 65, RoadSolidLayer.TUNNEL, "minecraft:air", RoadSolidMaterialPriority.AIR);
        solids.add(point, 66, RoadSolidLayer.TUNNEL, "minecraft:air", RoadSolidMaterialPriority.AIR);

        RoadGenerationIntegrityValidator.IntegrityReport report =
            RoadGenerationIntegrityValidator.validate(plan, solids, terrain);

        assertEquals(0, report.unclearedObstructionCount());
    }

    private static TerrainSampler elevatedColumn(int topY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return topY;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return topY;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= topY;
            }
        };
    }
}
