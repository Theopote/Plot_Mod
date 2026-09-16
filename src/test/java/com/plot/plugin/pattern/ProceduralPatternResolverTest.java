package com.plot.plugin.pattern;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;
import com.plot.plugin.pattern.pipeline.ProceduralPatternMaterialResolver;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProceduralPatternResolverTest {

    @Test
    void checkerboardAlternatesAdjacentCells() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.CHECKERBOARD);
        config.setMaterials(List.of("a", "b"));
        config.setTileSize(2.0);

        Vec2d centroid = new Vec2d(0, 0);
        int atOrigin = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 0, 0, centroid, "seed");
        int neighborX = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 2, 0, centroid, "seed");
        int neighborZ = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 0, 2, centroid, "seed");
        int diagonal = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 2, 2, centroid, "seed");

        assertEquals(0, atOrigin);
        assertEquals(1, neighborX);
        assertEquals(1, neighborZ);
        assertEquals(0, diagonal);
    }

    @Test
    void checkerboardIgnoresExtraMaterials() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.CHECKERBOARD);
        config.setMaterials(List.of("a", "b", "c", "d"));
        config.setTileSize(1.0);

        int index = ProceduralPatternMaterialResolver.resolveMaterialIndex(
            config, 1, 0, new Vec2d(0, 0), "seed");
        assertTrue(index == 0 || index == 1);
    }

    @Test
    void stripesRotateWithAngle() {
        ProceduralPatternConfig horizontal = new ProceduralPatternConfig();
        horizontal.setType(ProceduralPatternConfig.PatternType.STRIPES);
        horizontal.setMaterials(List.of("a", "b", "c"));
        horizontal.setTileSize(2.0);
        horizontal.setAngleDegrees(0.0);

        ProceduralPatternConfig vertical = new ProceduralPatternConfig();
        vertical.setType(ProceduralPatternConfig.PatternType.STRIPES);
        vertical.setMaterials(List.of("a", "b", "c"));
        vertical.setTileSize(2.0);
        vertical.setAngleDegrees(90.0);

        Vec2d centroid = new Vec2d(0, 0);
        int horizontalAtX = ProceduralPatternMaterialResolver.resolveMaterialIndex(horizontal, 4, 0, centroid, "seed");
        int verticalAtZ = ProceduralPatternMaterialResolver.resolveMaterialIndex(vertical, 0, 4, centroid, "seed");
        assertEquals(horizontalAtX, verticalAtZ);
    }

    @Test
    void concentricRingsIncreaseWithDistance() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.CONCENTRIC_RINGS);
        config.setMaterials(List.of("a", "b", "c"));
        config.setTileSize(2.0);

        Vec2d centroid = new Vec2d(0, 0);
        int inner = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 0, 0, centroid, "seed");
        int middle = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 3, 0, centroid, "seed");
        int outer = ProceduralPatternMaterialResolver.resolveMaterialIndex(config, 5, 0, centroid, "seed");

        assertEquals(0, inner);
        assertEquals(1, middle);
        assertEquals(2, outer);
    }

    @Test
    void mosaicMatchesMaterialMixResolver() {
        ProceduralPatternConfig config = new ProceduralPatternConfig();
        config.setType(ProceduralPatternConfig.PatternType.MOSAIC);
        config.setMaterials(List.of("primary", "accent"));
        config.setMosaicPrimaryRatio(0.7);

        MaterialMix mix = ProceduralPatternMaterialResolver.buildMosaicMix(config, config.getMaterials());
        int cellX = (int) Math.floor(12 / config.getTileSize());
        int cellZ = (int) Math.floor(-7 / config.getTileSize());
        BlockPos pos = new BlockPos(cellX, 0, cellZ);
        String expected = MaterialMixResolver.resolve(mix, pos, "footprint-1", material -> material);
        int index = ProceduralPatternMaterialResolver.resolveMaterialIndex(
            config, 12, -7, new Vec2d(0, 0), "footprint-1");
        assertEquals(expected, config.getMaterials().get(index));
    }
}
