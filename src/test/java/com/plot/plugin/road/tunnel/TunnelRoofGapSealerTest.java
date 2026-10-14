package com.plot.plugin.road.tunnel;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelRoofGapSealerTest {

    private static final int ROAD_Y = 64;

    @Test
    void fillsAirSandwichBetweenRoofAndTerrain() {
        ResolvedTunnelStyle style = ResolvedTunnelStyle.defaults();
        int envelopeWidth = 9;
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            style.shape(),
            envelopeWidth,
            style.sideClearance(),
            style.clearHeight(),
            style.liningThickness(),
            ROAD_Y,
            false);
        RoadSolidModel solids = new RoadSolidModel();
        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                return 72;
            }

            @Override
            public int sampleColumnTopY(Vec2d p) {
                return 72;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 70 || y >= 72;
            }
        };
        TunnelRoofGapSealer.sealAboveRoofShell(
            solids,
            mask,
            style.shape(),
            ROAD_Y,
            style.clearHeight(),
            envelopeWidth,
            style.sideClearance(),
            style.liningThickness(),
            new Vec2d(10, 0),
            new Vec2d(0, 1),
            1.0,
            new RoadTerrainClearanceUtils.BlockColumnResolver() {
                @Override
                public int worldX(Vec2d planPoint) {
                    return (int) Math.round(planPoint.x);
                }

                @Override
                public int worldZ(Vec2d planPoint) {
                    return (int) Math.round(planPoint.y);
                }
            },
            terrain,
            "minecraft:stone_bricks",
            "minecraft:stone");

        int roofTop = mask.maxY();
        assertTrue(solids.primitives().stream().anyMatch(
            primitive -> primitive.elevation() == roofTop + 1
                && "minecraft:stone_bricks".equals(primitive.materialId())),
            "air sandwich above roof must receive shell material");
        assertTrue(solids.primitives().stream().anyMatch(
            primitive -> primitive.elevation() == 72
                && "minecraft:stone".equals(primitive.materialId())),
            "original terrain layer must be restored");
    }
}
