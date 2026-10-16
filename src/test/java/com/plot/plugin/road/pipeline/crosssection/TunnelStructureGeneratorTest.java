package com.plot.plugin.road.pipeline.crosssection;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.construction.ConstructionRun;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.solid.RoadSolidPrimitive;
import com.plot.plugin.road.solid.RoadVoxelRasterizer;
import com.plot.plugin.road.tunnel.TunnelProfile;
import net.minecraft.util.math.BlockPos;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelLightingMode;
import com.plot.plugin.road.tunnel.TunnelPortalPlanner;
import com.plot.plugin.road.tunnel.TunnelShape;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelStructureGeneratorTest {

    @Test
    void liningDoesNotReplaceTreesOrBlocksAboveSurface() {
        RoadSolidModel solids = new RoadSolidModel();
        ResolvedTunnelStyle style = resolved(TunnelShape.RECTANGULAR, 5, 1, 1, TunnelLightingMode.NONE);
        TerrainSampler shallowWooded = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 66;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 72;
            }

            @Override
            public boolean isRoadClearableDecoration(int x, int y, int z) {
                return y >= 67 && y <= 72;
            }
        };

        TunnelStructureGenerator.placeCrossSection(
            solids,
            style,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            3,
            64,
            "minecraft:stone_bricks",
            shallowWooded,
            columnResolver(),
            1.0,
            true);

        assertTrue(solids.primitives().stream().noneMatch(p ->
                "minecraft:stone_bricks".equals(p.materialId()) && p.elevation() >= 66),
            "lining must not replace surface or tree canopy");
    }

    @Test
    void airCavityClearsEmbeddedTerrain() {
        RoadSolidModel solids = new RoadSolidModel();
        ResolvedTunnelStyle style = resolved(TunnelShape.RECTANGULAR, 5, 1, 1, TunnelLightingMode.NONE);
        TerrainSampler mountain = columnTerrain(100, 72, 72);

        TunnelStructureGenerator.placeCrossSection(
            solids,
            style,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            3,
            64,
            "minecraft:stone_bricks",
            mountain,
            columnResolver(),
            1.0,
            false);

        assertTrue(solids.primitives().stream().anyMatch(p ->
            p.elevation() == 66 && "minecraft:air".equals(p.materialId())),
            "embedded terrain inside cavity must become air");
        assertTrue(solids.primitives().stream().anyMatch(p ->
            p.elevation() == 70 && "minecraft:stone_bricks".equals(p.materialId())),
            "roof lining must still be placed");
    }

    @Test
    void liningSealsAirPocketsInShellBelowSurface() {
        RoadSolidModel solids = new RoadSolidModel();
        ResolvedTunnelStyle style = resolved(TunnelShape.RECTANGULAR, 5, 1, 1, TunnelLightingMode.NONE);
        // Roof lining Y for clearHeight=5, lining=1, roadY=64 is 70; leave Y=70 as air in terrain.
        TerrainSampler pocketed = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 80;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y != 70;
            }
        };

        TunnelStructureGenerator.placeCrossSection(
            solids,
            style,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            3,
            64,
            "minecraft:stone_bricks",
            pocketed,
            columnResolver(),
            1.0,
            false);

        assertTrue(solids.primitives().stream().anyMatch(p ->
                p.elevation() == 70 && "minecraft:stone_bricks".equals(p.materialId())),
            "air pocket in roof shell must still receive lining");
        assertTrue(solids.primitives().stream().noneMatch(p ->
                p.elevation() == 70 && "minecraft:air".equals(p.materialId())),
            "lining cells must not be left as air");
    }

    @Test
    void liningCellsNearSurfaceAreNotClearedToAirWithoutLining() {
        RoadSolidModel solids = new RoadSolidModel();
        ResolvedTunnelStyle style = resolved(TunnelShape.RECTANGULAR, 5, 1, 1, TunnelLightingMode.NONE);
        // Surface at roof lining Y: shouldPlaceLining is false, must not punch air holes.
        TerrainSampler shallow = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 70;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 70;
            }
        };

        TunnelStructureGenerator.placeCrossSection(
            solids,
            style,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            3,
            64,
            "minecraft:stone_bricks",
            shallow,
            columnResolver(),
            1.0,
            false);

        assertTrue(solids.primitives().stream().noneMatch(p ->
                p.elevation() == 70 && "minecraft:air".equals(p.materialId())),
            "near-surface lining cells must not be excavated to air without lining");
    }

    @Test
    void nonPortalStationsAlsoSealRoofGaps() {
        RoadSolidModel solids = new RoadSolidModel();
        ResolvedTunnelStyle style = resolved(TunnelShape.RECTANGULAR, 5, 1, 1, TunnelLightingMode.NONE);
        TerrainSampler sandwich = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 74;
            }

            @Override
            public int sampleColumnTopY(Vec2d point) {
                return 74;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y != 71;
            }
        };

        TunnelStructureGenerator.placeCrossSection(
            solids,
            style,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            3,
            64,
            "minecraft:stone_bricks",
            sandwich,
            columnResolver(),
            1.0,
            false);

        assertTrue(solids.primitives().stream().anyMatch(p ->
                p.elevation() == 71 && "minecraft:stone_bricks".equals(p.materialId())),
            "interior tunnel stations must seal air above roof lining");
    }

    @Test
    void rectangularCrossSectionCreatesAirCavityAndLining() {
        RoadSolidModel solids = new RoadSolidModel();
        ResolvedTunnelStyle style = resolved(TunnelShape.RECTANGULAR, 5, 1, 1, TunnelLightingMode.NONE);

        TunnelStructureGenerator.placeCrossSection(
            solids,
            style,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            3,
            64,
            "minecraft:stone_bricks",
            columnTerrain(100, 80, 100),
            columnResolver(),
            1.0,
            false);

        assertTrue(solids.primitives().stream().anyMatch(p ->
            p.materialId().equals("minecraft:air") && p.elevation() == 69));
        assertTrue(solids.primitives().stream().anyMatch(p ->
            p.materialId().equals("minecraft:stone_bricks") && p.elevation() == 70));
    }

    @Test
    void archShapeLowersCeilingNearWalls() {
        RoadSolidModel solids = new RoadSolidModel();
        ResolvedTunnelStyle style = resolved(TunnelShape.ARCH, 5, 1, 1, TunnelLightingMode.NONE);

        TunnelStructureGenerator.placeCrossSection(
            solids,
            style,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            3,
            64,
            "minecraft:stone_bricks",
            columnTerrain(100, 80, 100),
            columnResolver(),
            1.0,
            false);

        assertTrue(solids.primitives().stream().anyMatch(p ->
            p.materialId().equals("minecraft:air") && p.elevation() == 69 && Math.abs(p.planPoint().y) == 0));
        assertTrue(solids.primitives().stream().anyMatch(p ->
            p.materialId().equals("minecraft:stone_bricks")
                && p.elevation() == 69
                && Math.abs(p.planPoint().y) >= 3));
    }

    @Test
    void crossSectionThenLightingKeepsSeaLanternAtWallPositions() {
        RoadSolidModel solids = new RoadSolidModel();
        ResolvedTunnelStyle style = resolved(TunnelShape.RECTANGULAR, 5, 1, 1, TunnelLightingMode.WALL_BANDS);
        RoadTerrainClearanceUtils.BlockColumnResolver resolver = columnResolver();
        int roadY = 64;
        int sideClearance = 1;
        int envelopeWidth = 3;
        int cavityWidth = envelopeWidth + sideClearance * 2;
        int cavityMin = com.plot.plugin.road.RoadDimensionUtils.minLateralOffset(cavityWidth);
        int cavityMax = com.plot.plugin.road.RoadDimensionUtils.maxLateralOffset(cavityWidth);
        int wallLightY = roadY + 2;

        TunnelStructureGenerator.placeCrossSection(
            solids,
            style,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            envelopeWidth,
            roadY,
            "minecraft:stone_bricks",
            columnTerrain(100, 80, 100),
            resolver,
            1.0,
            false);
        TunnelStructureGenerator.placeLighting(
            testHost(),
            solids,
            style,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            envelopeWidth,
            roadY,
            0.0,
            resolver,
            1.0);

        RoadGenerationResult result = new RoadGenerationResult(0);
        RoadVoxelRasterizer.flushEdgeSolids(
            result,
            solids,
            null,
            com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance());

        assertEquals(
            "minecraft:sea_lantern",
            result.placementRecords.get(new BlockPos(0, wallLightY, cavityMin)).newBlockId);
        assertEquals(
            "minecraft:sea_lantern",
            result.placementRecords.get(new BlockPos(0, wallLightY, cavityMax)).newBlockId);
    }

    @Test
    void crossSectionThenCeilingLightingKeepsSeaLanternAtCeiling() {
        RoadSolidModel solids = new RoadSolidModel();
        ResolvedTunnelStyle style = resolved(TunnelShape.RECTANGULAR, 5, 1, 1, TunnelLightingMode.WALL_AND_CEILING);
        RoadTerrainClearanceUtils.BlockColumnResolver resolver = columnResolver();
        int roadY = 64;
        int envelopeWidth = 3;
        int halfCavityWidth = (envelopeWidth + style.sideClearance() * 2) / 2;
        int ceilingY = TunnelProfile.ceilingY(
            style.shape(), 0, halfCavityWidth, roadY, style.clearHeight());

        TunnelStructureGenerator.placeCrossSection(
            solids,
            style,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            envelopeWidth,
            roadY,
            "minecraft:stone_bricks",
            columnTerrain(100, 80, 100),
            resolver,
            1.0,
            false);
        TunnelStructureGenerator.placeLighting(
            testHost(),
            solids,
            style,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            envelopeWidth,
            roadY,
            0.0,
            resolver,
            1.0);

        RoadGenerationResult result = new RoadGenerationResult(0);
        RoadVoxelRasterizer.flushEdgeSolids(
            result,
            solids,
            null,
            com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance());

        assertEquals(
            "minecraft:sea_lantern",
            result.placementRecords.get(new BlockPos(0, ceilingY, 0)).newBlockId);
    }

    @Test
    void wallBandLightingPlacesLightsOnSidewallsOnly() {
        RoadSolidModel solids = new RoadSolidModel();
        ResolvedTunnelStyle style = resolved(TunnelShape.RECTANGULAR, 5, 1, 1, TunnelLightingMode.WALL_BANDS);

        TunnelStructureGenerator.placeLighting(
            testHost(),
            solids,
            style,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            3,
            64,
            0.0,
            columnResolver(),
            1.0);

        List<RoadSolidPrimitive> lights = solids.primitives().stream()
            .filter(p -> p.materialId().equals("minecraft:sea_lantern"))
            .toList();
        assertFalse(lights.isEmpty());
        assertTrue(lights.stream().allMatch(p -> p.elevation() == 66));
        assertTrue(lights.stream().anyMatch(p -> Math.abs(p.planPoint().y) > 0));
    }

    @Test
    void wallBandLightingSkipsOffSpacingStructureStation() {
        RoadSolidModel solids = new RoadSolidModel();
        ResolvedTunnelStyle style = resolved(TunnelShape.RECTANGULAR, 5, 1, 1, TunnelLightingMode.WALL_BANDS);

        TunnelStructureGenerator.placeLighting(
            testHost(),
            solids,
            style,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            3,
            64,
            3.0,
            columnResolver(),
            1.0);

        assertTrue(solids.primitives().stream().noneMatch(p ->
            p.materialId().equals("minecraft:sea_lantern")));
    }

    @Test
    void structureSpacingUsesTunnelRunLocalStation() {
        ConstructionRun runA = new ConstructionRun(
            RoadConstructionType.TUNNEL, 0, 1, 103.0, 140.0, 10, 10.0);
        ConstructionRun runB = new ConstructionRun(
            RoadConstructionType.TUNNEL, 2, 3, 200.0, 240.0, 10, 10.0);
        List<ConstructionRun> runs = List.of(runA, runB);

        assertEquals(0.0, TunnelStructureGenerator.structureStationAt(
            TunnelStructureGenerator.tunnelRunContaining(runs, 103.0), 103.0), 1e-9);
        assertEquals(8.0, TunnelStructureGenerator.structureStationAt(
            TunnelStructureGenerator.tunnelRunContaining(runs, 111.0), 111.0), 1e-9);
        assertEquals(0.0, TunnelStructureGenerator.structureStationAt(
            TunnelStructureGenerator.tunnelRunContaining(runs, 200.0), 200.0), 1e-9);

        // Absolute chainage 103 % 8 != 0, but run-local station 0 hits spacing 8.
        assertTrue(TunnelStructureGenerator.hitsStructureSpacing(0.0, 8));
        assertTrue(TunnelStructureGenerator.hitsStructureSpacing(8.0, 8));
        assertFalse(TunnelStructureGenerator.hitsStructureSpacing(3.0, 8));
        // Second tunnel restarts phase independently at its own start.
        assertTrue(TunnelStructureGenerator.hitsStructureSpacing(
            TunnelStructureGenerator.structureStationAt(runB, 200.0), 8));
    }

    @Test
    void entryPortalFrameExtendsTowardTunnelInterior() {
        RoadSolidModel solids = placePortalFrames(new TunnelPortalPlanner.PortalStation(0.0, true));
        assertTrue(solids.primitives().stream().anyMatch(p ->
            p.materialId().equals("minecraft:stone_bricks") && p.planPoint().x >= -0.1 && p.planPoint().x <= 1.1));
        assertTrue(solids.primitives().stream().noneMatch(p ->
            p.materialId().equals("minecraft:stone_bricks") && p.planPoint().x < -0.5));
    }

    @Test
    void exitPortalFrameExtendsTowardTunnelInterior() {
        RoadSolidModel solids = placePortalFrames(new TunnelPortalPlanner.PortalStation(40.0, false));
        assertTrue(solids.primitives().stream().anyMatch(p ->
            p.materialId().equals("minecraft:stone_bricks")
                && p.planPoint().x >= 38.5
                && p.planPoint().x <= 40.1));
        assertTrue(solids.primitives().stream().noneMatch(p ->
            p.materialId().equals("minecraft:stone_bricks") && p.planPoint().x > 40.5),
            "exit portal must not thicken outward past the tunnel end");
    }

    private static RoadSolidModel placePortalFrames(TunnelPortalPlanner.PortalStation portal) {
        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(40, 0));
        ResolvedTunnelStyle style = resolved(TunnelShape.RECTANGULAR, 5, 1, 1, TunnelLightingMode.NONE);
        RoadSolidModel solids = new RoadSolidModel();
        TunnelStructureGenerator.placePortals(
            testHost(),
            solids,
            style,
            List.of(segment),
            List.of(new SegmentHeightInfo(segment, 80, 80, 64, 64, 0.0)),
            CrossSectionBuildContext.fixed(ResolvedCrossSection.fromConfig(new RoadSystemConfig("portal-dir"))),
            columnTerrain(100, 80, 100),
            1.0,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            columnResolver(),
            List.of(portal));
        return solids;
    }

    private static ResolvedTunnelStyle resolved(
            TunnelShape shape,
            int clearHeight,
            int sideClearance,
            int liningThickness,
            TunnelLightingMode lightingMode) {
        TunnelStyle style = new TunnelStyle();
        style.setShape(shape);
        style.setClearHeight(clearHeight);
        style.setSideClearance(sideClearance);
        style.setLiningThickness(liningThickness);
        style.setLightingMode(lightingMode);
        style.setLightMaterial("minecraft:sea_lantern");
        style.setLightSpacing(6);
        style.ensureConfigDefaults();
        return ResolvedTunnelStyle.from(style);
    }

    private static TerrainSampler columnTerrain(int topY, int surfaceY, int solidBelowY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return surfaceY;
            }

            @Override
            public int sampleColumnTopY(Vec2d point) {
                return topY;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= solidBelowY;
            }
        };
    }

    private static TunnelStructureGenerator.Host testHost() {
        return new TunnelStructureGenerator.Host() {
            @Override
            public String resolveBlockId(String material) {
                return material;
            }

            @Override
            public int snapEndpointElevation(Vec2d center, int targetY) {
                return targetY;
            }
        };
    }

    private static RoadTerrainClearanceUtils.BlockColumnResolver columnResolver() {
        return new RoadTerrainClearanceUtils.BlockColumnResolver() {
            @Override
            public int worldX(Vec2d point) {
                return (int) Math.round(point.x);
            }

            @Override
            public int worldZ(Vec2d point) {
                return (int) Math.round(point.y);
            }
        };
    }
}
