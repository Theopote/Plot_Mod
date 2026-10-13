package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCorridorClearanceVolumeTest {

    @Test
    void clearsIsolatedLogInsideBodyClearanceVolume() {
        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.putClearance(
            new ColumnKey(0, 0),
            new ClearanceCell(point, 64, 68, RoadCorridorConstructionMode.OPEN, null));

        TerrainSampler terrain = new TerrainSampler() {
            private final Set<Long> isolatedLogs = Set.of(pack(0, 66, 0));

            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return 64;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return 68;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= 64;
            }

            @Override
            public boolean isConstructionVolumeObstruction(int worldX, int y, int worldZ) {
                return isolatedLogs.contains(pack(worldX, y, worldZ));
            }

            private static long pack(int x, int y, int z) {
                return ((long) x << 32) ^ (y << 16) ^ z;
            }
        };

        RoadCorridorClearancePass.apply(
            solids, plan, terrain, CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()));

        assertTrue(solids.primitives().stream()
            .anyMatch(p -> p.elevation() == 66 && "minecraft:air".equals(p.materialId())),
            "isolated log inside clearance volume must be cleared");
    }

    @Test
    void clearsCopperOreInsideBodyClearanceVolume() {
        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.putClearance(
            new ColumnKey(0, 0),
            new ClearanceCell(point, 64, 66, RoadCorridorConstructionMode.OPEN, null));

        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return 64;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return 66;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= 64;
            }

            @Override
            public boolean isConstructionVolumeObstruction(int worldX, int y, int worldZ) {
                return worldX == 0 && y == 65 && worldZ == 0;
            }
        };

        RoadCorridorClearancePass.apply(
            solids, plan, terrain, CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()));

        assertTrue(solids.primitives().stream()
            .anyMatch(p -> p.elevation() == 65 && "minecraft:air".equals(p.materialId())));
    }

    @Test
    void doesNotClearIsolatedLogOutsideBodyClearanceColumn() {
        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        plan.putClearance(
            new ColumnKey(0, 0),
            new ClearanceCell(point, 64, 68, RoadCorridorConstructionMode.OPEN, null));

        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return 64;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return 68;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= 64;
            }

            @Override
            public boolean isConstructionVolumeObstruction(int worldX, int y, int worldZ) {
                return worldX == 5 && y == 66 && worldZ == 0;
            }
        };

        RoadCorridorClearancePass.apply(
            solids, plan, terrain, CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()));

        assertEquals(0, solids.primitives().stream()
            .filter(p -> p.elevation() == 66)
            .count(),
            "log outside clearance column must not be touched by clearance pass");
    }
}
