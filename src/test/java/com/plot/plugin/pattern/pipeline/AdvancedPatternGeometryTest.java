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
    void crosshatchAlternatesDiagonally() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.CROSSHATCH);
        config.setMaterials(List.of("a", "b"));
        config.setTileSize(1.0);

        Vec2d centroid = new Vec2d(0, 0);
        int origin = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 0, 0, centroid, "seed");
        int diagonal = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 1, 1, centroid, "seed");
        assertEquals(origin, diagonal);
        assertNotEquals(
            origin,
            ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 1, 0, centroid, "seed"));
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
        config.setTileSize(1.0);

        Vec2d centroid = new Vec2d(0, 0);
        int east = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 5, 0, centroid, "seed");
        int north = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 0, 5, centroid, "seed");
        assertNotEquals(east, north);
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
    void fishScaleDensityScalesTileSize() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.FISH_SCALE);
        config.setDensity(2.0);
        config.setTileSize(4.0);

        assertEquals(2.0, PatternCoordinateTransform.effectiveTileSize(config), 1e-6);
    }
}
