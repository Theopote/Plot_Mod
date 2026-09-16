package com.plot.plugin.pattern.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;
import com.plot.plugin.pattern.pipeline.ProceduralPatternMaterialResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PatternCoordinateTransformTest {

    @Test
    void offsetAffectsCheckerboardMaterialIndex() {
        ProceduralPatternConfig baseline = new ProceduralPatternConfig();
        baseline.setType(ProceduralPatternConfig.PatternType.CHECKERBOARD);
        baseline.setMaterials(java.util.List.of("a", "b"));
        baseline.setTileSize(2.0);

        ProceduralPatternConfig offset = baseline.copy();
        offset.setOffset(new Vec2d(2.0, 0.0));

        int withoutOffset = ProceduralPatternMaterialResolver.resolveMaterialIndex(
            baseline, 1.0, 1.0, new Vec2d(0, 0), "seed");
        int withOffset = ProceduralPatternMaterialResolver.resolveMaterialIndex(
            offset, 1.0, 1.0, new Vec2d(0, 0), "seed");

        assertNotEquals(withoutOffset, withOffset);
    }

    @Test
    void hexDensityChangesEffectiveTileSize() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.HEXAGONAL);
        config.setDensity(2.0);
        config.setTileSize(4.0);

        assertEquals(2.0, PatternCoordinateTransform.effectiveTileSize(config), 1e-6);
    }
}
