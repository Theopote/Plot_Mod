package com.plot.plugin.pattern.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;
import com.plot.plugin.pattern.space.PatternSpace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedPatternGeometryTest {

    @Test
    void runningBondStaggersRows() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.RUNNING_BOND);
        config.setMaterials(List.of("a", "b"));
        config.setTileSize(2.0);

        Vec2d centroid = new Vec2d(0, 0);
        int row0 = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 3, 0, centroid, "seed");
        int row1 = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 3, 1.0, centroid, "seed");
        assertNotEquals(row0, row1);
    }

    @Test
    void crosshatchUsesBasketWeaveGroupsNotCheckerboard() {
        ProceduralPatternConfig crosshatch = new ProceduralPatternConfig();
        crosshatch.setType(ProceduralPatternConfig.PatternType.CROSSHATCH);
        crosshatch.setMaterials(List.of("a", "b"));
        crosshatch.setTileSize(1.0);

        ProceduralPatternConfig checkerboard = crosshatch.copy();
        checkerboard.setType(ProceduralPatternConfig.PatternType.CHECKERBOARD);

        Vec2d centroid = new Vec2d(0, 0);
        int origin = ProceduralPatternMaterialResolver.resolveMaterialIndex(
            crosshatch, 0.5, 0.5, centroid, "seed");
        int horizontalNeighbor = ProceduralPatternMaterialResolver.resolveMaterialIndex(
            crosshatch, 1.5, 0.5, centroid, "seed");
        int verticalNeighbor = ProceduralPatternMaterialResolver.resolveMaterialIndex(
            crosshatch, 0.5, 1.5, centroid, "seed");

        assertEquals(origin, horizontalNeighbor, "horizontal weave block pairs adjacent X cells");
        assertNotEquals(origin, verticalNeighbor, "horizontal weave block alternates rows");

        int checkerAtOrigin = ProceduralPatternMaterialResolver.resolveMaterialIndex(
            checkerboard, 0.5, 0.5, centroid, "seed");
        int checkerHorizontalNeighbor = ProceduralPatternMaterialResolver.resolveMaterialIndex(
            checkerboard, 1.5, 0.5, centroid, "seed");
        assertNotEquals(checkerAtOrigin, checkerHorizontalNeighbor);
    }

    @Test
    void crosshatchVerticalWeaveBlockPairsAdjacentZCells() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.CROSSHATCH);
        config.setMaterials(List.of("a", "b"));
        config.setTileSize(1.0);

        Vec2d centroid = new Vec2d(0, 0);
        int top = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 2.5, 0.5, centroid, "seed");
        int bottom = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 2.5, 1.5, centroid, "seed");
        int beside = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 3.5, 0.5, centroid, "seed");

        assertEquals(top, bottom, "vertical weave block pairs adjacent Z cells");
        assertNotEquals(top, beside, "vertical weave block alternates columns");
    }

    @Test
    void scatterUsesAllMaterials() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.SCATTER);
        config.setMaterials(List.of("a", "b", "c", "d"));
        config.setTileSize(1.0);

        boolean[] seen = new boolean[4];
        Vec2d centroid = new Vec2d(0, 0);
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                int index = ProceduralPatternMaterialResolver.resolveMaterialIndex(
                    config, x, z, centroid, "scatter-seed");
                seen[index] = true;
            }
        }
        for (boolean used : seen) {
            assertTrue(used);
        }
    }

    @Test
    void radialChangesWithAngle() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.RADIAL);
        config.setMaterials(List.of("a", "b", "c", "d"));
        config.setRadialSectorCount(12);

        Vec2d centroid = new Vec2d(0, 0);
        int east = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 5, 0, centroid, "seed");
        int north = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 0, 5, centroid, "seed");
        assertNotEquals(east, north);
    }

    @Test
    void radialSectorCountControlsAngularWidth() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.RADIAL);
        config.setMaterials(List.of("a", "b"));
        config.setRadialSectorCount(4);

        Vec2d centroid = new Vec2d(0, 0);
        int east = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 5, 0, centroid, "seed");
        int north = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 0, 5, centroid, "seed");
        int northeast = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 5, 5, centroid, "seed");

        assertEquals(east, northeast);
        assertNotEquals(east, north);
        assertEquals(90.0, config.radialSectorAngleDegrees(), 1e-6);
    }

    @Test
    void frameUsesDistanceToEdge() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.FRAME);
        config.setMaterials(List.of("edge", "inner"));
        config.setTileSize(1.0);

        List<Vec2d> square = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10));
        PatternSpace space = new PatternSpace(
            "seed",
            new Vec2d(5, 5),
            PolygonRegionUtils.computeBounds(square),
            square,
            List.of());
        int edge = ProceduralPatternMaterialResolver.resolveMaterialIndex(
            config, 0.5, 5, space.regionCentroid(), "seed", space);
        int center = ProceduralPatternMaterialResolver.resolveMaterialIndex(
            config, 5, 5, space.regionCentroid(), "seed", space);
        assertNotEquals(edge, center);
    }

    @Test
    void frameFollowsTriangleBoundaryNotBoundingBox() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.FRAME);
        config.setMaterials(List.of("edge", "inner"));
        config.setTileSize(1.0);

        List<Vec2d> triangle = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(5, 5));
        PatternSpace space = new PatternSpace(
            "seed",
            new Vec2d(5, 1.5),
            PolygonRegionUtils.computeBounds(triangle),
            triangle,
            List.of());

        int nearSlope = ProceduralPatternMaterialResolver.resolveMaterialIndex(
            config, 9.0, 1.0, space.regionCentroid(), "seed", space);
        assertEquals(0, nearSlope);
    }

    @Test
    void frameTreatsHoleBoundaryAsEdge() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.FRAME);
        config.setMaterials(List.of("edge", "inner"));
        config.setTileSize(1.0);

        List<Vec2d> outer = List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10));
        List<Vec2d> hole = List.of(
            new Vec2d(4, 4),
            new Vec2d(6, 4),
            new Vec2d(6, 6),
            new Vec2d(4, 6));
        PatternSpace space = new PatternSpace(
            "seed",
            new Vec2d(5, 5),
            PolygonRegionUtils.computeBounds(outer, List.of(hole)),
            outer,
            List.of(hole));

        int nearHole = ProceduralPatternMaterialResolver.resolveMaterialIndex(
            config, 3.5, 5.0, space.regionCentroid(), "seed", space);
        assertEquals(0, nearHole);
    }

    @Test
    void fishScaleDiffersFromRunningBondAtSameTileSize() {
        ProceduralPatternConfig runningBond = new ProceduralPatternConfig();
        runningBond.setType(ProceduralPatternConfig.PatternType.RUNNING_BOND);
        runningBond.setTileSize(ProceduralPatternConfig.MIN_FISH_SCALE_TILE_SIZE);
        runningBond.setMaterials(List.of("a", "b"));

        ProceduralPatternConfig fishScale = new ProceduralPatternConfig();
        fishScale.setType(ProceduralPatternConfig.PatternType.FISH_SCALE);
        fishScale.setTileSize(ProceduralPatternConfig.MIN_FISH_SCALE_TILE_SIZE);
        fishScale.setMaterials(List.of("a", "b"));

        boolean anyDifference = false;
        for (int z = 0; z < 6; z++) {
            for (int x = 0; x < 12; x++) {
                int bond = ProceduralPatternMaterialResolver.resolveMaterialIndex(
                    runningBond, x + 0.5, z + 0.5, null, "seed", null);
                int scale = ProceduralPatternMaterialResolver.resolveMaterialIndex(
                    fishScale, x + 0.5, z + 0.5, null, "seed", null);
                if (bond != scale) {
                    anyDifference = true;
                    break;
                }
            }
            if (anyDifference) {
                break;
            }
        }
        assertTrue(anyDifference, "fish scale should not degenerate into running bond");
    }

    @Test
    void fishScaleDensityScalesTileSize() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.FISH_SCALE);
        config.setDensity(2.0);
        config.setTileSize(4.0);

        assertEquals(2.0, PatternCoordinateTransform.effectiveTileSize(config), 1e-6);
    }
}
