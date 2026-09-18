package com.plot.plugin.pattern.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;
import com.plot.plugin.pattern.space.PatternSpace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdvancedPatternSnapshotTest {

    @Test
    void runningBondSnapshotTwoMaterials() {
        ProceduralPatternConfig config = config(ProceduralPatternConfig.PatternType.RUNNING_BOND, 2.0, 1.0);
        assertSnapshot(config, 0, 11, 0, 5, null, """
            001100110011
            011001100110
            001100110011
            011001100110
            001100110011
            011001100110
            """);
    }

    @Test
    void crosshatchSnapshotTwoMaterials() {
        ProceduralPatternConfig config = config(ProceduralPatternConfig.PatternType.CROSSHATCH, 2.0, 1.0);
        assertSnapshot(config, 0, 7, 0, 3, null, """
            00001100
            00001100
            11111100
            11111100
            """);
    }

    @Test
    void scatterSnapshotTwoMaterials() {
        ProceduralPatternConfig config = config(ProceduralPatternConfig.PatternType.SCATTER, 2.0, 1.0);
        assertSnapshot(config, 0, 7, 0, 3, null, """
            00000000
            00000000
            00110000
            00110011
            """);
    }

    @Test
    void radialSnapshotFourMaterials() {
        ProceduralPatternConfig config = config(ProceduralPatternConfig.PatternType.RADIAL, 2.0, 1.0);
        config.setMaterials(List.of("a", "b", "c", "d"));
        config.setRadialSectorCount(4);
        config.setCenterOverride(new Vec2d(4.0, 2.0));
        assertSnapshot(config, 0, 7, 0, 3, null, """
            22223333
            22223333
            11110000
            11110000
            """);
    }

    @Test
    void windmillSnapshotTwoMaterials() {
        ProceduralPatternConfig config = config(ProceduralPatternConfig.PatternType.WINDMILL, 2.0, 1.0);
        config.setCenterOverride(new Vec2d(4.0, 2.0));
        assertSnapshot(config, 0, 7, 0, 3, null, """
            11111100
            11111100
            11000000
            11000000
            """);
    }

    @Test
    void frameSnapshotTwoMaterials() {
        ProceduralPatternConfig config = config(ProceduralPatternConfig.PatternType.FRAME, 1.0, 1.0);
        PatternSpace space = PatternSnapshotFixtures.squareSpace(0, 8, 0, 4);
        assertSnapshot(config, 0, 7, 0, 3, space, """
            00000000
            01111110
            01111110
            00000000
            """);
    }

    @Test
    void fishScaleSnapshotTwoMaterials() {
        ProceduralPatternConfig config = config(
            ProceduralPatternConfig.PatternType.FISH_SCALE,
            ProceduralPatternConfig.MIN_FISH_SCALE_TILE_SIZE,
            1.0);
        assertSnapshot(config, 0, 11, 0, 5, null, """
            001110011100
            011100111001
            001110011100
            111001110011
            001000010000
            001110011100
            """);
    }

    private static void assertSnapshot(
            ProceduralPatternConfig config,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            PatternSpace space,
            String expected) {
        assertEquals(
            expected.trim(),
            PatternGridSnapshot.render(config, minX, maxX, minZ, maxZ, 0.5, space));
    }

    private static ProceduralPatternConfig config(
            ProceduralPatternConfig.PatternType type,
            double tileSize,
            double density) {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(type);
        config.setTileSize(tileSize);
        config.setDensity(density);
        config.setMaterials(List.of("a", "b"));
        return config;
    }
}
