package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.pipeline.RoadEdgeBuildMetrics;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCorridorSolidEmitterEarthworkTest {

    @Test
    void doesNotEmitOpenFillUnderBridgeInteriorClearance() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey deck = new ColumnKey(15, 0);
        ColumnKey lateral = new ColumnKey(15, 3);
        plan.surfaceMask().put(deck, new SurfaceCell(
            new Vec2d(15, 0), 64, RoadSolidLayer.ROAD, "minecraft:stone"));
        plan.putClearance(deck, new ClearanceCell(
            new Vec2d(15, 0), 64, 72,
            RoadCorridorConstructionMode.BRIDGE,
            RoadConstructionType.BRIDGE));
        plan.putClearance(lateral, new ClearanceCell(
            new Vec2d(15, 3), 64, 72,
            RoadCorridorConstructionMode.BRIDGE,
            RoadConstructionType.BRIDGE));
        SlopeColumnCell residual = plan.slopeCellOrCreate(lateral, new Vec2d(15, 3));
        residual.mergeFill(56, "minecraft:gravel");
        // Re-assert structure clearance so stale slope on the same column is stripped.
        plan.putClearance(lateral, new ClearanceCell(
            new Vec2d(15, 3), 64, 72,
            RoadCorridorConstructionMode.BRIDGE,
            RoadConstructionType.BRIDGE));
        assertEquals(null, plan.slopeCell(lateral));

        // Residual slope without a second strip: emitter must still refuse open fill.
        SlopeColumnCell stale = plan.slopeCellOrCreate(lateral, new Vec2d(15, 3));
        stale.mergeFill(56, "minecraft:gravel");

        RoadSolidModel solids = new RoadSolidModel();
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        RoadCorridorSolidEmitter.emit(
            solids,
            plan,
            valleyTerrain(),
            CorridorTestFixtures.planHost(config),
            null,
            "minecraft:gravel");

        int gravelShoulder = 0;
        for (var primitive : solids.primitives()) {
            if (primitive.layer() == RoadSolidLayer.SHOULDER
                    || primitive.layer() == RoadSolidLayer.SUBGRADE) {
                gravelShoulder++;
            }
        }
        assertEquals(0, gravelShoulder,
            "bridge interior must not emit open shoulder/subgrade fill");
    }

    @Test
    void portalTransitionCapsFillAndCutBatterAboveTunnelRoof() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey portalColumn = new ColumnKey(20, 4);
        int designY = 64;
        plan.putClearance(portalColumn, new ClearanceCell(
            new Vec2d(20, 4), designY, 70,
            RoadCorridorConstructionMode.PORTAL,
            RoadConstructionType.TUNNEL_PORTAL));
        SlopeColumnCell slope = plan.slopeCellOrCreate(portalColumn, new Vec2d(20, 4));
        slope.mergeFill(78, "minecraft:gravel");
        slope.mergeCut(76, "minecraft:stone");

        RoadSolidModel solids = new RoadSolidModel();
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        RoadCorridorSolidEmitter.emit(
            solids,
            plan,
            mountainTerrain(),
            CorridorTestFixtures.planHost(config),
            null,
            "minecraft:gravel");

        int roofTop = RoadCorridorPlanBuilder.portalRoofTopY(CorridorTestFixtures.planHost(config), designY);
        for (var primitive : solids.primitives()) {
            if (primitive.planPoint().x == 20 && primitive.planPoint().y == 4
                    && primitive.elevation() > roofTop) {
                org.junit.jupiter.api.Assertions.fail(
                    "portal batter must not emit blocks above tunnel roof at y=" + primitive.elevation());
            }
        }
    }

    @Test
    void cutBatterFaceUsesSubgradeNotShoulderAboveRoadSurface() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey slopeColumn = new ColumnKey(12, 3);
        SlopeColumnCell slope = plan.slopeCellOrCreate(slopeColumn, new Vec2d(12, 3));
        slope.mergeCut(65, "minecraft:stone");
        plan.surfaceMask().put(new ColumnKey(12, 2), new SurfaceCell(
            new Vec2d(12, 2), 64, RoadSolidLayer.SHOULDER, "minecraft:coarse_dirt"));

        RoadSolidModel solids = new RoadSolidModel();
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        RoadCorridorSolidEmitter.emit(
            solids,
            plan,
            mountainTerrain(),
            CorridorTestFixtures.planHost(config),
            null,
            "minecraft:gravel");

        for (var primitive : solids.primitives()) {
            if (primitive.layer() != RoadSolidLayer.SHOULDER) {
                continue;
            }
            if (primitive.elevation() > 64 && primitive.planPoint().x == 12 && primitive.planPoint().y == 3) {
                org.junit.jupiter.api.Assertions.fail(
                    "cut batter must not emit shoulder-layer blocks above road surface on slope columns");
            }
        }
    }

    @Test
    void elevatedGradeSeparationEdgeDoesNotEmitFillBatter() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey key = new ColumnKey(10, 2);
        plan.surfaceMask().put(key, new SurfaceCell(
            new Vec2d(10, 2), 70, RoadSolidLayer.ROAD, "minecraft:stone"));
        plan.putClearance(key, new ClearanceCell(
            new Vec2d(10, 2), 70, 74,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.ROAD));
        SlopeColumnCell slope = plan.slopeCellOrCreate(key, new Vec2d(10, 2));
        slope.mergeFill(69, "minecraft:dirt");

        RoadSolidModel solids = new RoadSolidModel();
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        GradeSeparationBuildContext elevated = new GradeSeparationBuildContext(
            true,
            java.util.List.of(new GradeSeparationBuildContext.ProtectedUnderpassZone(
                java.util.List.of(
                    new Vec2d(0, 0), new Vec2d(20, 0), new Vec2d(20, 10), new Vec2d(0, 10)),
                64,
                66)));
        RoadCorridorSolidEmitter.emit(
            solids,
            plan,
            valleyTerrain(),
            CorridorTestFixtures.planHost(config),
            null,
            "minecraft:dirt",
            elevated);

        long dirtFill = solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.SUBGRADE)
            .filter(p -> "minecraft:dirt".equals(p.materialId()))
            .count();
        assertEquals(0, dirtFill, "elevated grade-sep edge must not emit open fill batter");
    }

    @Test
    void lakeColumnDoesNotEmitOrdinaryFillBatter() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey key = new ColumnKey(10, 0);
        plan.surfaceMask().put(key, new SurfaceCell(
            new Vec2d(10, 0), 64, RoadSolidLayer.ROAD, "minecraft:white_concrete"));
        plan.putClearance(key, new ClearanceCell(
            new Vec2d(10, 0), 64, 68,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.ROAD));
        SlopeColumnCell slope = plan.slopeCellOrCreate(key, new Vec2d(10, 0));
        slope.mergeFill(63, "minecraft:dirt");

        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorSolidEmitter.emit(
            solids,
            plan,
            lakeTerrain(60, 62),
            CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()),
            null,
            "minecraft:dirt");

        long dirt = solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.SUBGRADE)
            .filter(p -> "minecraft:dirt".equals(p.materialId()))
            .count();
        assertEquals(0, dirt, "ordinary road must not embank into lake columns");
    }

    @Test
    void causewayFillMayEmbankOverWater() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey key = new ColumnKey(10, 0);
        plan.surfaceMask().put(key, new SurfaceCell(
            new Vec2d(10, 0), 64, RoadSolidLayer.ROAD, "minecraft:white_concrete"));
        plan.putClearance(key, new ClearanceCell(
            new Vec2d(10, 0), 64, 68,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.FILL));
        SlopeColumnCell slope = plan.slopeCellOrCreate(key, new Vec2d(10, 0));
        slope.mergeFill(63, "minecraft:dirt");

        RoadSolidModel solids = new RoadSolidModel();
        var metrics = new com.plot.plugin.road.pipeline.RoadEdgeBuildMetrics();
        RoadCorridorSolidEmitter.emit(
            solids,
            plan,
            lakeTerrain(60, 62),
            CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()),
            metrics,
            "minecraft:dirt");

        long dirt = solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.SUBGRADE)
            .filter(p -> "minecraft:dirt".equals(p.materialId()))
            .count();
        org.junit.jupiter.api.Assertions.assertTrue(dirt > 0, "causeway FILL may embank over water");
        org.junit.jupiter.api.Assertions.assertTrue(metrics.fillVolume > 0,
            "slope fill path must count fillVolume");
    }

    @Test
    void widthCapTipEmitsVerticalRetainingFinish() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey tip = new ColumnKey(20, 0);
        SlopeColumnCell slope = plan.slopeCellOrCreate(tip, new Vec2d(20, 0));
        slope.mergeCut(70, "minecraft:stone");
        slope.markRetainingFinish();

        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorSolidEmitter.emit(
            solids,
            plan,
            mountainTerrain(),
            CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()),
            null,
            "minecraft:stone");

        long wall = solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.RETAINING_WALL)
            .filter(p -> Math.round(p.planPoint().x) == 20 && Math.round(p.planPoint().y) == 0)
            .count();
        org.junit.jupiter.api.Assertions.assertTrue(wall > 0,
            "capped cut tip must emit vertical retaining finish up to ground");
    }

    @Test
    void cutVolumeNotDoubleCountedWhenClearanceAlreadyCoversSlope() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey key = new ColumnKey(12, 4);
        plan.putClearance(key, new ClearanceCell(
            new Vec2d(12, 4), 65, 80,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.ROAD));
        SlopeColumnCell slope = plan.slopeCellOrCreate(key, new Vec2d(12, 4));
        slope.mergeCut(65, "minecraft:stone");

        var metrics = new com.plot.plugin.road.pipeline.RoadEdgeBuildMetrics();
        RoadCorridorSolidEmitter.emit(
            new RoadSolidModel(),
            plan,
            mountainTerrain(),
            CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig()),
            metrics,
            "minecraft:stone");

        assertEquals(0, metrics.cutVolume,
            "emitter must not re-count cut when SlopeEnvelopeClearance already owns the column");
    }

    @Test
    void fillOnlySlopeColumnWithoutSurfaceDoesNotThrowOnDeepCoverCheck() {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        ColumnKey fillColumn = new ColumnKey(8, 2);
        plan.putClearance(fillColumn, new ClearanceCell(
            new Vec2d(8, 2), 64, 68,
            RoadCorridorConstructionMode.OPEN,
            RoadConstructionType.FILL));
        SlopeColumnCell slope = plan.slopeCellOrCreate(fillColumn, new Vec2d(8, 2));
        slope.mergeFill(64, "minecraft:gravel");

        RoadSolidModel solids = new RoadSolidModel();
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        RoadCorridorSolidEmitter.emit(
            solids,
            plan,
            valleyTerrain(),
            CorridorTestFixtures.planHost(config),
            null,
            "minecraft:gravel");

        assertEquals(64, RoadCorridorSolidEmitter.resolveDesignY(null, slope, 50));
    }

    private static TerrainSampler mountainTerrain() {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(com.plot.api.geometry.Vec2d p) {
                return 80;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 80;
            }
        };
    }

    private static TerrainSampler valleyTerrain() {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                return p.x >= 8 && p.x <= 22 ? 50 : 64;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= sampleSurfaceY(new Vec2d(x, z));
            }
        };
    }

    /** Lake bed groundY, exposed water surface above bed. */
    private static TerrainSampler lakeTerrain(int bedY, int waterY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                return bedY;
            }

            @Override
            public java.util.OptionalInt findExposedWaterSurface(Vec2d planPoint) {
                return java.util.OptionalInt.of(waterY);
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= bedY;
            }
        };
    }
}
