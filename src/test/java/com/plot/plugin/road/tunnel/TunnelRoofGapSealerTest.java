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
    void sealsSmallAirPocketButLeavesLargeCaveUnfilled() {
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
        int roofTop = mask.maxY();
        int largeGap = TunnelOverburdenPolicy.SMALL_AIR_POCKET_MAX_BLOCKS + 2;
        RoadSolidModel solids = new RoadSolidModel();
        TerrainSampler largeCave = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                return roofTop + largeGap + 3;
            }

            @Override
            public int sampleColumnTopY(Vec2d p) {
                return roofTop + largeGap + 3;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y > roofTop + largeGap && y <= roofTop + largeGap + 3;
            }
        };
        seal(solids, mask, style, envelopeWidth, largeCave);
        long filledInCave = solids.primitives().stream()
            .filter(p -> p.elevation() > roofTop && p.elevation() <= roofTop + largeGap)
            .filter(p -> "minecraft:stone_bricks".equals(p.materialId())
                || "minecraft:stone".equals(p.materialId()))
            .count();
        assertTrue(filledInCave == 0,
            "large cave above roof must not be mass-filled with shell/restore");
    }

    @Test
    void sealsSmallFluidPocketAboveRoofAsNormalGround() {
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
        int roofTop = mask.maxY();
        RoadSolidModel solids = new RoadSolidModel();
        TerrainSampler fluidPocket = new TerrainSampler() {
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
                return y == roofTop + 3 || y == roofTop + 4 || y == roofTop + 5;
            }

            @Override
            public boolean isFluidBlock(int x, int y, int z) {
                return y == roofTop + 1 || y == roofTop + 2;
            }
        };
        seal(solids, mask, style, envelopeWidth, fluidPocket);
        assertTrue(solids.primitives().stream().anyMatch(p ->
                p.elevation() == roofTop + 1
                    && "minecraft:stone_bricks".equals(p.materialId())),
            "small water/lava pocket above roof must be sealed as normal ground");
        assertTrue(solids.primitives().stream().anyMatch(p ->
                p.elevation() == roofTop + 2
                    && "minecraft:stone_bricks".equals(p.materialId())),
            "second fluid cell in small pocket must also be sealed");
    }

    private static void seal(
            RoadSolidModel solids,
            TunnelCrossSectionMask mask,
            ResolvedTunnelStyle style,
            int envelopeWidth,
            TerrainSampler terrain) {
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
    }

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
                return 73;
            }

            @Override
            public int sampleColumnTopY(Vec2d p) {
                return 73;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y != 71;
            }

            @Override
            public boolean isGravityAffectedBlock(int x, int y, int z) {
                // Underground gravel above the air sandwich — must be stabilized below surface.
                return y == 72;
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
            "underground gravel layer must be restored below natural surface");
        assertTrue(solids.primitives().stream().noneMatch(
            primitive -> primitive.elevation() >= 73
                && !"minecraft:air".equals(primitive.materialId())),
            "must not rewrite the natural surface itself");
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
    void sealRewritesUndergroundGravelSittingInRoofCell() {
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
        int roofTop = mask.maxY();
        RoadSolidModel solids = new RoadSolidModel();
        TerrainSampler gravelRoof = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                return roofTop + 4;
            }

            @Override
            public int sampleColumnTopY(Vec2d p) {
                return roofTop + 4;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= roofTop + 4;
            }

            @Override
            public boolean isGravityAffectedBlock(int x, int y, int z) {
                return y == roofTop;
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
            new Vec2d(0, 0),
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
            gravelRoof,
            "minecraft:stone_bricks",
            "minecraft:stone");
        assertTrue(solids.primitives().stream().anyMatch(p ->
                p.elevation() == roofTop
                    && ("minecraft:stone_bricks".equals(p.materialId())
                        || "minecraft:stone".equals(p.materialId()))),
            "underground gravel occupying the roof cell must be rewritten before cavity dig");
    }

    @Test
    void sealDoesNotRewriteGravelThatIsTheNaturalSurface() {
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
        int roofTop = mask.maxY();
        RoadSolidModel solids = new RoadSolidModel();
        TerrainSampler surfaceGravel = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                return roofTop;
            }

            @Override
            public int sampleColumnTopY(Vec2d p) {
                return roofTop;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= roofTop;
            }

            @Override
            public boolean isGravityAffectedBlock(int x, int y, int z) {
                return y == roofTop;
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
            new Vec2d(0, 0),
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
            surfaceGravel,
            "minecraft:stone_bricks",
            "minecraft:stone");
        assertTrue(solids.primitives().stream().noneMatch(p ->
                p.elevation() >= roofTop
                    && ("minecraft:stone_bricks".equals(p.materialId())
                        || "minecraft:stone".equals(p.materialId()))),
            "natural-surface gravel must not be rewritten into lining or restore stone");
    }

    @Test
    void stabilizeGravityColumnRewritesGravelWithoutShellFill() {
        RoadSolidModel solids = new RoadSolidModel();
        int roofTop = 70;
        TerrainSampler gravel = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                return roofTop + 3;
            }

            @Override
            public int sampleColumnTopY(Vec2d p) {
                return roofTop + 3;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y > roofTop && y <= roofTop + 3;
            }

            @Override
            public boolean isGravityAffectedBlock(int x, int y, int z) {
                return y == roofTop + 1 || y == roofTop + 2;
            }
        };
        TunnelRoofGapSealer.stabilizeGravityColumn(
            solids, new Vec2d(5, 0), 5, 0, roofTop, gravel, "minecraft:stone");
        assertTrue(solids.primitives().stream().anyMatch(p ->
                p.elevation() == roofTop + 1 && "minecraft:stone".equals(p.materialId())),
            "corridor-cleared column must stabilize gravel above roof");
        assertTrue(solids.primitives().stream().noneMatch(p ->
                p.elevation() == roofTop + 3 && "minecraft:stone_bricks".equals(p.materialId())),
            "gravity-only stabilize must not invent lining shell fill");
    }

    @Test
    void shallowCoverDoesNotInventLiningAboveNaturalSurface() {
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
        int roofTop = mask.maxY();
        int surfaceY = roofTop - 4; // 洞口浅覆：自然地表远低于设计洞顶
        RoadSolidModel solids = new RoadSolidModel();
        TerrainSampler shallow = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                return surfaceY;
            }

            @Override
            public int sampleColumnTopY(Vec2d p) {
                return surfaceY;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y < surfaceY;
            }

            @Override
            public boolean isGravityAffectedBlock(int x, int y, int z) {
                return false;
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
            shallow,
            "minecraft:stone_bricks",
            "minecraft:stone");

        assertTrue(solids.primitives().stream().noneMatch(p ->
                p.elevation() >= surfaceY
                    && !"minecraft:air".equals(p.materialId())),
            "shallow portal must not invent lining/restore at or above natural surface");
        assertTrue(solids.primitives().stream().noneMatch(p ->
                p.elevation() == roofTop + 1),
            "must not force-scan to roofTop+1 when cover is below the design roof");
    }

    @Test
    void stabilizesGravityStacksButLeavesStableSolids() {
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

            @Override
            public boolean isGravityAffectedBlock(int x, int y, int z) {
                // First three overburden layers are gravel; layers above are stable dirt.
                return y > roofTop && y <= roofTop + 3;
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
            "sealer must rewrite gravity stacks above roof");
        assertTrue(solids.primitives().stream().noneMatch(p ->
                "minecraft:stone".equals(p.materialId())
                    && p.elevation() > roofTop + 3
                    && p.elevation() <= roofTop + 5),
            "stable dirt/stone overburden must not be mass-replaced with stone");
    }

    @Test
    void doesNotRewriteGravityAboveStableBearingLayer() {
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
        int roofTop = mask.maxY();
        RoadSolidModel solids = new RoadSolidModel();
        // 洞顶 → 稳定石头 → 更高处沙砾：上层沙砾已有承托，不得改写
        TerrainSampler stoneThenGravel = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d p) {
                return roofTop + 4;
            }

            @Override
            public int sampleColumnTopY(Vec2d p) {
                return roofTop + 4;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y > roofTop && y <= roofTop + 4;
            }

            @Override
            public boolean isGravityAffectedBlock(int x, int y, int z) {
                return y == roofTop + 2 || y == roofTop + 3;
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
            stoneThenGravel,
            "minecraft:stone_bricks",
            "minecraft:stone");

        assertTrue(solids.primitives().stream().noneMatch(p ->
                "minecraft:stone".equals(p.materialId())
                    && (p.elevation() == roofTop + 2 || p.elevation() == roofTop + 3)),
            "gravel above a stable bearing layer must not be rewritten");
    }
}
