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
                return y != 71;
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

    @Test
    void doesNotSealLiningThroughTreesAboveSurface() {
        ResolvedTunnelStyle style = ResolvedTunnelStyle.defaults();
        int envelopeWidth = 9;
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            style.shape(),
            envelopeWidth,
            style.sideClearance(),
            style.clearHeight(),
            style.liningThickness(),
            ROAD_Y,
            true);
        RoadSolidModel solids = new RoadSolidModel();
        TerrainSampler wooded = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                return 66;
            }

            @Override
            public int sampleColumnTopY(Vec2d p) {
                return 74;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return true;
            }

            @Override
            public boolean isRoadClearableDecoration(int x, int y, int z) {
                return y >= 67;
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
            wooded,
            "minecraft:stone_bricks",
            "minecraft:stone");

        assertTrue(solids.primitives().stream().noneMatch(primitive ->
                primitive.elevation() >= 67
                    && ("minecraft:stone_bricks".equals(primitive.materialId())
                        || "minecraft:stone".equals(primitive.materialId()))),
            "portal sealer must not convert trees above the surface");
    }

    @Test
    void stabilizesStackedSolidsAboveRoof() {
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
        int roofTop = mask.maxY();
        TerrainSampler stacked = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                return roofTop + 5;
            }

            @Override
            public int sampleColumnTopY(Vec2d p) {
                return roofTop + 5;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y > roofTop;
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
            stacked,
            "minecraft:stone_bricks",
            "minecraft:stone");

        long stabilized = solids.primitives().stream()
            .filter(p -> "minecraft:stone".equals(p.materialId())
                && p.elevation() > roofTop
                && p.elevation() <= roofTop + 3)
            .count();
        assertTrue(stabilized >= 3,
            "sealer must stabilize multiple solid layers above roof against gravel collapse");
    }
}
