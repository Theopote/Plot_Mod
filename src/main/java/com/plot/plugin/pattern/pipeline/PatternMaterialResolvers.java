package com.plot.plugin.pattern.pipeline;

import com.plot.plugin.pattern.image.BlockColorMatcher;
import com.plot.plugin.pattern.image.ImagePatternRaster;
import com.plot.plugin.pattern.image.PatternImageStore;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternSource;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 根据 footprint 配置构造 {@link PatternMaterialResolver}。
 */
public final class PatternMaterialResolvers {
    private PatternMaterialResolvers() {
    }

    public static Optional<PatternMaterialResolver> forFootprint(
            PatternFootprint footprint,
            Path pluginDataDir) {
        if (footprint == null) {
            return Optional.empty();
        }
        if (footprint.getSource() == PatternSource.IMAGE) {
            return forImage(footprint, pluginDataDir);
        }
        return Optional.of(new ProceduralPatternMaterialResolver(footprint.getPattern()));
    }

    private static Optional<PatternMaterialResolver> forImage(
            PatternFootprint footprint,
            Path pluginDataDir) {
        if (pluginDataDir == null) {
            return Optional.empty();
        }
        return PatternImageStore.loadRaster(pluginDataDir, footprint.getImagePattern())
            .map(raster -> new ImagePatternMaterialResolver(
                footprint.getImagePattern(),
                raster,
                new BlockColorMatcher(footprint.getImagePattern().getPaletteBlocks())));
    }
}
