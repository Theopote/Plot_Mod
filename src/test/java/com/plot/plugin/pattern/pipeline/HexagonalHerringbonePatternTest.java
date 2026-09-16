package com.plot.plugin.pattern.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HexagonalHerringbonePatternTest {

    @Test
    void hexagonalSnapshotTwoMaterials() {
        ProceduralPatternConfig config = hexConfig(2.0, 1.0, List.of("a", "b"));
        String grid = PatternGridSnapshot.render(config, 0, 11, 0, 7, 0.5);
        assertEquals(
            """
            001111110000
            011111100000
            111110000001
            110000001111
            100000011111
            000001111110
            000011111100
            001111110000
            """.trim(),
            grid);
    }

    @Test
    void hexagonalSnapshotThreeMaterials() {
        ProceduralPatternConfig config = hexConfig(2.0, 1.0, List.of("a", "b", "c"));
        String grid = PatternGridSnapshot.render(config, 0, 8, 0, 5, 0.5);
        assertEquals(
            """
            001111112
            011111122
            111112222
            112222220
            122222200
            222220000
            """.trim(),
            grid);
    }

    @Test
    void hexagonalThreeColorsBalanceAcrossGrid() {
        ProceduralPatternConfig config = hexConfig(2.0, 1.0, List.of("a", "b", "c"));
        int[] counts = new int[3];
        Vec2d centroid = new Vec2d(0, 0);
        for (int z = 0; z < 12; z++) {
            for (int x = 0; x < 12; x++) {
                int index = ProceduralPatternMaterialResolver.resolveMaterialIndex(
                    config, x + 0.5, z + 0.5, centroid, "seed");
                counts[index]++;
            }
        }
        int min = Math.min(counts[0], Math.min(counts[1], counts[2]));
        int max = Math.max(counts[0], Math.max(counts[1], counts[2]));
        assertTrue(max - min <= 6, "color counts should stay balanced: " + java.util.Arrays.toString(counts));
    }

    @Test
    void hexagonalNeighborsDifferForTwoMaterials() {
        ProceduralPatternConfig config = hexConfig(2.0, 1.0, List.of("a", "b"));
        Vec2d centroid = new Vec2d(0, 0);
        int center = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 3, 3, centroid, "seed");
        int east = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 5, 3, centroid, "seed");
        int southEast = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 4, 5, centroid, "seed");
        assertNotEquals(center, east);
        assertNotEquals(center, southEast);
    }

    @Test
    void hexagonalDensityShrinksTiles() {
        ProceduralPatternConfig sparse = hexConfig(4.0, 1.0, List.of("a", "b"));
        ProceduralPatternConfig dense = hexConfig(4.0, 2.0, List.of("a", "b"));
        int sparseIndex = ProceduralPatternMaterialResolver.resolveMaterialIndex(
            sparse, 1.5, 1.5, new Vec2d(0, 0), "seed");
        int denseIndex = ProceduralPatternMaterialResolver.resolveMaterialIndex(
            dense, 1.5, 1.5, new Vec2d(0, 0), "seed");
        assertNotEquals(sparseIndex, denseIndex);
        assertEquals(2.0, PatternCoordinateTransform.effectiveTileSize(dense), 1e-6);
    }

    @Test
    void herringboneSnapshotTwoMaterials() {
        ProceduralPatternConfig config = herringboneConfig(4.0, 1.0, List.of("a", "b"));
        String grid = PatternGridSnapshot.render(config, 0, 7, 0, 3, 0.5);
        assertEquals(
            """
            01111000
            00011110
            01111000
            11100001
            """.trim(),
            grid);
    }

    @Test
    void herringboneSnapshotThreeMaterials() {
        ProceduralPatternConfig config = herringboneConfig(4.0, 1.0, List.of("a", "b", "c"));
        String grid = PatternGridSnapshot.render(config, 0, 7, 0, 3, 0.5);
        assertEquals(
            """
            01111222
            00011112
            21000211
            11022210
            """.trim(),
            grid);
    }

    @Test
    void herringboneOddRowsOffsetChangesPattern() {
        ProceduralPatternConfig config = herringboneConfig(4.0, 1.0, List.of("a", "b"));
        Vec2d centroid = new Vec2d(0, 0);
        int evenRow = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 0.5, 0.5, centroid, "seed");
        int oddRow = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 2.5, 2.5, centroid, "seed");
        assertEquals(0, evenRow);
        assertEquals(1, oddRow);
    }

    @Test
    void herringboneDensityShrinksBricks() {
        ProceduralPatternConfig config = herringboneConfig(4.0, 2.0, List.of("a", "b"));
        assertEquals(2.0, PatternCoordinateTransform.effectiveTileSize(config), 1e-6);
        String dense = PatternGridSnapshot.render(config, 0, 7, 0, 3, 0.5);
        String sparse = PatternGridSnapshot.render(herringboneConfig(4.0, 1.0, List.of("a", "b")), 0, 7, 0, 3, 0.5);
        assertNotEquals(sparse, dense);
    }

    private static ProceduralPatternConfig hexConfig(double tileSize, double density, List<String> materials) {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.HEXAGONAL);
        config.setTileSize(tileSize);
        config.setDensity(density);
        config.setMaterials(materials);
        return config;
    }

    private static ProceduralPatternConfig herringboneConfig(double tileSize, double density, List<String> materials) {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.HERRINGBONE);
        config.setTileSize(tileSize);
        config.setDensity(density);
        config.setMaterials(materials);
        return config;
    }
}
