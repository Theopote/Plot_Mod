package com.plot.plugin.pattern.pipeline;

import com.plot.plugin.pattern.PatternGenerationIssue;
import com.plot.plugin.pattern.image.BlockColorMatcher;
import com.plot.plugin.pattern.image.PatternImageStore;
import com.plot.plugin.pattern.model.ImagePatternConfig;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternSource;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 根据 footprint 构造材质解析器，并在准备阶段记录失败原因。
 */
public final class PatternMaterialResolverFactory {
    public record Outcome(PatternMaterialResolver resolver, PatternGenerationIssue issue) {
        public static Outcome ok(PatternMaterialResolver resolver) {
            return new Outcome(resolver, PatternGenerationIssue.NONE);
        }

        public static Outcome failed(PatternGenerationIssue issue) {
            return new Outcome(null, issue);
        }

        public boolean canGenerate() {
            return resolver != null && issue == PatternGenerationIssue.NONE;
        }
    }

    private PatternMaterialResolverFactory() {
    }

    public static Outcome forFootprint(PatternFootprint footprint, Path pluginDataDir) {
        if (footprint == null) {
            return Outcome.failed(PatternGenerationIssue.NO_SAMPLE_POINTS);
        }
        if (footprint.getSource() == PatternSource.IMAGE) {
            return forImage(footprint, pluginDataDir);
        }
        return Outcome.ok(new ProceduralPatternMaterialResolver(footprint.getPattern()));
    }

    private static Outcome forImage(PatternFootprint footprint, Path pluginDataDir) {
        ImagePatternConfig config = footprint.getImagePattern();
        if (pluginDataDir == null) {
            return Outcome.failed(PatternGenerationIssue.IMAGE_PLUGIN_DATA_UNAVAILABLE);
        }
        if (config == null || !config.hasImage()) {
            return Outcome.failed(PatternGenerationIssue.IMAGE_NOT_IMPORTED);
        }
        Optional<com.plot.plugin.pattern.image.ImagePatternRaster> raster =
            PatternImageStore.loadRaster(pluginDataDir, config);
        if (raster.isEmpty()) {
            return Outcome.failed(PatternGenerationIssue.IMAGE_FILE_MISSING);
        }
        return Outcome.ok(new ImagePatternMaterialResolver(
            config,
            raster.get(),
            new BlockColorMatcher()));
    }
}
