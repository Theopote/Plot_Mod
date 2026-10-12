package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCorridorClearancePassTest {

    @Test
    void openModeClearsSolidAndDecorationsAboveDesignSurface() {
        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.surfaceMask().put(
            new ColumnKey(0, 0),
            new SurfaceCell(point, 64, RoadSolidLayer.ROAD, "minecraft:stone"));
        plan.putClearance(
            new ColumnKey(0, 0),
            new ClearanceCell(point, 64, 70, RoadCorridorConstructionMode.OPEN, null));

        TerrainSampler terrain = woodedColumn(64, 70);
        RoadCorridorClearancePass.apply(
            solids, plan, terrain, CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()));

        long airCount = solids.primitives().stream()
            .filter(p -> "minecraft:air".equals(p.materialId()))
            .count();
        // With planned surface, clear designY+1..70 only (leave designY for ROAD emit)
        assertEquals(6, airCount);
        assertTrue(solids.primitives().stream().noneMatch(p -> p.elevation() == 64));
    }

    @Test
    void overheadSandAboveDesignSurfaceIsCleared() {
        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.surfaceMask().put(
            new ColumnKey(0, 0),
            new SurfaceCell(point, 64, RoadSolidLayer.ROAD, "minecraft:white_concrete"));
        plan.putClearance(
            new ColumnKey(0, 0),
            new ClearanceCell(point, 64, 66, RoadCorridorConstructionMode.OPEN, null));

        TerrainSampler sandAbove = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return 66;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return 66;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= 66;
            }
        };

        RoadCorridorClearancePass.apply(
            solids, plan, sandAbove, CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()));

        assertTrue(solids.primitives().stream()
            .anyMatch(p -> p.elevation() == 65 && "minecraft:air".equals(p.materialId())));
        assertTrue(solids.primitives().stream()
            .noneMatch(p -> p.elevation() == 64 && "minecraft:air".equals(p.materialId())),
            "designY must remain for ROAD emit, not TUNNEL air");
    }

    @Test
    void fillScenarioStillClearsOverheadDecorations() {
        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.putClearance(
            new ColumnKey(0, 0),
            new ClearanceCell(point, 64, 68, RoadCorridorConstructionMode.OPEN, null));

        TerrainSampler lowGroundWithTrees = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return 50;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return 68;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= 50;
            }

            @Override
            public boolean isRoadClearableDecoration(int worldX, int y, int worldZ) {
                return y >= 65 && y <= 68;
            }
        };

        RoadCorridorClearancePass.apply(
            solids, plan, lowGroundWithTrees, CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()));

        assertEquals(4, solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.TUNNEL && "minecraft:air".equals(p.materialId()))
            .count());
    }

    @Test
    void bridgeModeStillClearsOverheadAboveDeck() {
        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.surfaceMask().put(
            new ColumnKey(0, 0),
            new SurfaceCell(point, 64, RoadSolidLayer.ROAD, "minecraft:white_concrete"));
        plan.putClearance(
            new ColumnKey(0, 0),
            new ClearanceCell(point, 64, 70, RoadCorridorConstructionMode.BRIDGE, null));

        RoadCorridorClearancePass.apply(
            solids, plan, woodedColumn(64, 70), CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()));

        long airCount = solids.primitives().stream()
            .filter(p -> "minecraft:air".equals(p.materialId()))
            .count();
        assertEquals(6, airCount);
        assertTrue(solids.primitives().stream().noneMatch(p -> p.elevation() == 64));
    }

    private static TerrainSampler woodedColumn(int groundY, int topY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return groundY;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return topY;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= groundY;
            }

            @Override
            public boolean isRoadClearableDecoration(int worldX, int y, int worldZ) {
                return y > groundY && y <= topY;
            }
        };
    }
}
