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
