package com.plot.plugin.pattern.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.pattern.model.PatternConfigSanitizer;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConcentricRingsPatternTest {

    @Test
    void offsetShiftsRingCenterWithCustomCenter() {
        ProceduralPatternConfig baseline = ringConfig(new Vec2d(5.0, 5.0), new Vec2d(0.0, 0.0));
        ProceduralPatternConfig shifted = ringConfig(new Vec2d(5.0, 5.0), new Vec2d(2.0, 0.0));

        Vec2d centroid = new Vec2d(0, 0);
        int withoutOffset = ProceduralPatternMaterialResolver.resolveMaterialIndex(
            baseline, 7.0, 5.0, centroid, "seed");
        int withOffset = ProceduralPatternMaterialResolver.resolveMaterialIndex(
            shifted, 9.0, 5.0, centroid, "seed");

        assertEquals(withoutOffset, withOffset);
    }

    @Test
    void residualAngleDoesNotRotateRings() {
        ProceduralPatternConfig noAngle = ringConfig(new Vec2d(0.0, 0.0), new Vec2d(0.0, 0.0));
        ProceduralPatternConfig withAngle = ringConfig(new Vec2d(0.0, 0.0), new Vec2d(0.0, 0.0));
        withAngle.setAngleDegrees(45.0);

        Vec2d centroid = new Vec2d(0, 0);
        int baseline = ProceduralPatternMaterialResolver.resolveMaterialIndex(
            noAngle, 3.0, 1.0, centroid, "seed");
        int rotated = ProceduralPatternMaterialResolver.resolveMaterialIndex(
            withAngle, 3.0, 1.0, centroid, "seed");

        assertEquals(baseline, rotated);
    }

    @Test
    void switchingToRingsClearsUnsupportedAngle() {
        ProceduralPatternConfig pattern = new ProceduralPatternConfig();
        pattern.setType(ProceduralPatternConfig.PatternType.CHECKERBOARD);
        pattern.setAngleDegrees(45.0);
        pattern.setDensity(2.0);

        pattern.setType(ProceduralPatternConfig.PatternType.CONCENTRIC_RINGS);
        PatternConfigSanitizer.sanitizeForType(pattern);

        assertEquals(0.0, pattern.getAngleDegrees(), 1e-6);
        assertEquals(1.0, pattern.getDensity(), 1e-6);
    }

    private static ProceduralPatternConfig ringConfig(Vec2d centerOverride, Vec2d offset) {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.CONCENTRIC_RINGS);
        config.setMaterials(List.of("a", "b", "c"));
        config.setTileSize(2.0);
        config.setCenterOverride(centerOverride);
        config.setOffset(offset);
        return config;
    }
}
