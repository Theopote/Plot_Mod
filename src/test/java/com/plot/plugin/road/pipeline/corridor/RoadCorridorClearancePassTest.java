package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
    void tunnelModeClearsCavityAirButNotAboveRoofShell() {
        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        int roofTop = RoadCorridorPlanBuilder.tunnelInteriorRoofTopY(
            CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()), 64);
        plan.putClearance(
            new ColumnKey(0, 0),
            new ClearanceCell(
                point, 64, roofTop, RoadCorridorConstructionMode.TUNNEL, null, 0));

        TerrainSampler mountain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return 72;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return 72;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= 72;
            }
        };

        RoadCorridorClearancePass.apply(
            solids, plan, mountain, CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()));

        assertTrue(solids.primitives().stream()
            .anyMatch(p -> p.elevation() == 65 && "minecraft:air".equals(p.materialId())),
            "tunnel cavity must clear native rock");
        assertTrue(solids.primitives().stream()
            .noneMatch(p -> p.elevation() > roofTop && "minecraft:air".equals(p.materialId())),
            "tunnel clearance must not carve air above roof shell");
    }

    @Test
    void openModeStripsCanopyAboveLocalSolidClearTopWithoutExcavatingRock() {
        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        // Cave/local solid clear stops at 69; canopy continues to 78
        plan.putClearance(
            new ColumnKey(0, 0),
            new ClearanceCell(point, 64, 69, RoadCorridorConstructionMode.OPEN, null));

        TerrainSampler caveWithCanopy = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return 90;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return 78;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y >= 70 && y <= 90;
            }

            @Override
            public boolean isRoadClearableDecoration(int worldX, int y, int worldZ) {
                return y >= 72 && y <= 78;
            }
        };

        RoadCorridorClearancePass.apply(
            solids, plan, caveWithCanopy, CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()));

        assertTrue(solids.primitives().stream()
                .anyMatch(p -> p.elevation() == 78 && "minecraft:air".equals(p.materialId())),
            "OPEN must strip canopy above local clear top");
        assertTrue(solids.primitives().stream()
                .anyMatch(p -> p.elevation() == 72 && "minecraft:air".equals(p.materialId())),
            "OPEN must strip mid-canopy decoration above local clear top");
        assertTrue(solids.primitives().stream()
                .noneMatch(p -> p.elevation() == 71 && "minecraft:air".equals(p.materialId())),
            "must not excavate mountain rock above local clear top");
    }

    @Test
    void gradeSepUnderpassClearsUpToDeckEvenWhenCellClearTopIsLocalOnly() {
        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorPlan plan = new RoadCorridorPlan();
        Vec2d point = new Vec2d(0, 0);
        // Cave/local shrink left clearTop at design+5, but deck underside is higher
        plan.putClearance(
            new ColumnKey(0, 0),
            new ClearanceCell(point, 64, 69, RoadCorridorConstructionMode.OPEN, null));
        GradeSeparationBuildContext underpass = new GradeSeparationBuildContext(
            false,
            List.of(new GradeSeparationBuildContext.ProtectedUnderpassZone(
                List.of(
                    new Vec2d(-2, -2), new Vec2d(2, -2),
                    new Vec2d(2, 2), new Vec2d(-2, 2)),
                72)));

        TerrainSampler filled = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return 80;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return 80;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= 80;
            }
        };

        RoadCorridorClearancePass.apply(
            solids,
            plan,
            filled,
            CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()),
            null,
            underpass);

        assertTrue(solids.primitives().stream()
            .anyMatch(p -> p.elevation() == 71 && "minecraft:air".equals(p.materialId())),
            "underpass must clear solids up to deck underside, not stop at local clearHeight");
        assertTrue(solids.primitives().stream()
            .noneMatch(p -> p.elevation() >= 72 && "minecraft:air".equals(p.materialId())),
            "must not punch into elevated deck");
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

    @Test
    void adjacentDecorationClearanceUsesCanvasPlanPointsUnderOffsetProjection() {
        var coordinates = com.plot.test.world.OrthographicProjectionTestFixtures.liveOrthographic();
        var host = CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig(), coordinates);
        Vec2d canvasCenter = new Vec2d(400, 300);
        var worldColumn = host.canvasToBlockPos(canvasCenter);
        Vec2d planPoint = host.planPointForWorldColumn(worldColumn.getX(), worldColumn.getZ());
        // Non-identity: world XZ must not equal canvas coords used for terrain sampling.
        assertTrue(Math.abs(planPoint.x - worldColumn.getX()) > 1.0
                || Math.abs(planPoint.y - worldColumn.getZ()) > 1.0,
            "fixture must use a non-identity canvas↔world transform");

        RoadCorridorPlan plan = new RoadCorridorPlan();
        plan.putClearance(
            new ColumnKey(worldColumn.getX(), worldColumn.getZ()),
            new ClearanceCell(planPoint, 64, 66, RoadCorridorConstructionMode.OPEN, null));

        ColumnKey neighbor = new ColumnKey(worldColumn.getX() + 1, worldColumn.getZ());
        Vec2d expectedNeighborPlan = host.planPointForWorldColumn(neighbor.worldX(), neighbor.worldZ());
        java.util.concurrent.atomic.AtomicReference<Vec2d> sampledTopPoint =
            new java.util.concurrent.atomic.AtomicReference<>();

        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                return 64;
            }

            @Override
            public int sampleColumnTopY(Vec2d p) {
                // Neighbor dilation is the only caller that needs column-top above solidTop.
                if (Math.abs(p.x - expectedNeighborPlan.x) < 0.5
                        && Math.abs(p.y - expectedNeighborPlan.y) < 0.5) {
                    sampledTopPoint.set(p);
                    return 70;
                }
                return 66;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= 64;
            }

            @Override
            public boolean isRoadClearableDecoration(int worldX, int y, int worldZ) {
                return worldX == neighbor.worldX()
                    && worldZ == neighbor.worldZ()
                    && y > 66
                    && y <= 70;
            }
        };

        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorClearancePass.apply(solids, plan, terrain, host);

        assertNotNull(sampledTopPoint.get(), "neighbor decoration clear must sample column top via canvas plan point");
        assertEquals(expectedNeighborPlan.x, sampledTopPoint.get().x, 0.5);
        assertEquals(expectedNeighborPlan.y, sampledTopPoint.get().y, 0.5);
        assertTrue(solids.primitives().stream().anyMatch(p ->
                "minecraft:air".equals(p.materialId())
                    && p.elevation() == 70
                    && Math.abs(p.planPoint().x - expectedNeighborPlan.x) < 0.5
                    && Math.abs(p.planPoint().y - expectedNeighborPlan.y) < 0.5),
            "cleared decoration solids must be stamped at canvas neighbor plan point, not raw world XZ");
        assertTrue(solids.primitives().stream().noneMatch(p ->
                "minecraft:air".equals(p.materialId())
                    && Math.abs(p.planPoint().x - neighbor.worldX()) < 0.01
                    && Math.abs(p.planPoint().y - neighbor.worldZ()) < 0.01),
            "must not treat world XZ as canvas coordinates when writing solids");
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
