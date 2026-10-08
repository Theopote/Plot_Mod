package com.plot.plugin.road.pipeline.crosssection;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.solid.RoadSolidPrimitive;
import com.plot.plugin.road.tunnel.TunnelLightingMode;
import com.plot.plugin.road.tunnel.TunnelShape;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelStructureGeneratorTest {

    @Test
    void rectangularCrossSectionCreatesAirCavityAndLining() {
        RoadSolidModel solids = new RoadSolidModel();
        TunnelStyle style = new TunnelStyle();
        style.setShape(TunnelShape.RECTANGULAR);
        style.setClearHeight(5);
        style.setSideClearance(1);
        style.setLiningThickness(1);

        TunnelStructureGenerator.placeCrossSection(
            testHost(),
            solids,
            style,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            3,
            64,
            "minecraft:stone_bricks",
            columnTerrain(100, 64, 100),
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
        TunnelStyle style = new TunnelStyle();
        style.setShape(TunnelShape.ARCH);
        style.setClearHeight(5);
        style.setSideClearance(1);
        style.setLiningThickness(1);

        TunnelStructureGenerator.placeCrossSection(
            testHost(),
            solids,
            style,
            new Vec2d(0, 0),
            new Vec2d(0, 1),
            3,
            64,
            "minecraft:stone_bricks",
            columnTerrain(100, 64, 100),
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
    void wallBandLightingPlacesLightsOnSidewallsOnly() {
        RoadSolidModel solids = new RoadSolidModel();
        TunnelStyle style = new TunnelStyle();
        style.setShape(TunnelShape.RECTANGULAR);
        style.setLightingMode(TunnelLightingMode.WALL_BANDS);
        style.setLightMaterial("minecraft:sea_lantern");
        style.setLightSpacing(6);
        style.setSideClearance(1);

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
    void wallBandLightingSkipsOffSpacingChainage() {
        RoadSolidModel solids = new RoadSolidModel();
        TunnelStyle style = new TunnelStyle();
        style.setLightingMode(TunnelLightingMode.WALL_BANDS);
        style.setLightSpacing(6);

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
