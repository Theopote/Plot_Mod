package com.plot.plugin.pattern;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.pipeline.PatternGenerationPipeline;
import com.plot.plugin.pattern.pipeline.PatternMaterialResolver;
import com.plot.plugin.pattern.pipeline.PatternMaterialResolvers;
import net.minecraft.world.World;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 铺装图案生成器入口。
 */
public class PatternGenerator {
    private final PatternGenerationPipeline pipeline;
    private final Path pluginDataDir;

    public PatternGenerator(
            ICoordinateService coordinateTransformer,
            IBlockProjectionService projectionHandler,
            Path pluginDataDir) {
        this.pipeline = new PatternGenerationPipeline(
            Objects.requireNonNull(coordinateTransformer, "coordinateTransformer"),
            Objects.requireNonNull(projectionHandler, "projectionHandler"));
        this.pluginDataDir = pluginDataDir;
    }

    public PatternGenerationResult generate(PatternFootprint footprint, World world) {
        return PatternMaterialResolvers.forFootprint(footprint, pluginDataDir)
            .map(resolver -> pipeline.generate(footprint, resolver, world))
            .orElseGet(PatternGenerationResult::new);
    }
}
