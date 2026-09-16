package com.plot.plugin.pattern.pipeline;

import com.plot.plugin.pattern.image.BlockColorMatcher;
import com.plot.plugin.pattern.image.ImagePatternRaster;
import com.plot.plugin.pattern.image.ImagePatternResolver;
import com.plot.plugin.pattern.model.ImagePatternConfig;
import com.plot.plugin.pattern.space.PatternSample;
import com.plot.plugin.pattern.space.PatternSpace;

import java.util.Objects;

/**
 * 图片图案材质解析：Pattern Space 采样点 → 像素 → 调色盘方块。
 */
public final class ImagePatternMaterialResolver implements PatternMaterialResolver {
    private final ImagePatternConfig config;
    private final ImagePatternRaster raster;
    private final BlockColorMatcher matcher;

    public ImagePatternMaterialResolver(
            ImagePatternConfig config,
            ImagePatternRaster raster,
            BlockColorMatcher matcher) {
        this.config = Objects.requireNonNull(config, "config").copy();
        this.raster = Objects.requireNonNull(raster, "raster");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
    }

    @Override
    public String resolveMaterial(PatternSpace space, PatternSample sample) {
        if (space == null || sample == null) {
            return null;
        }
        return ImagePatternResolver.resolveBlockId(
            config,
            raster,
            matcher,
            sample.toCanvas(),
            space.regionBounds());
    }
}
