package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
