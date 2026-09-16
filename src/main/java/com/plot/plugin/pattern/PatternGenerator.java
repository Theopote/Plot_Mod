package com.plot.plugin.pattern;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.pipeline.PatternGenerationPipeline;
import com.plot.plugin.pattern.pipeline.PatternMaterialResolverFactory;
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
        PatternMaterialResolverFactory.Outcome outcome =
            PatternMaterialResolverFactory.forFootprint(footprint, pluginDataDir);
        if (!outcome.canGenerate()) {
            PatternGenerationResult failed = new PatternGenerationResult();
            failed.setIssue(outcome.issue());
            return failed;
        }
        PatternGenerationResult result = pipeline.generate(footprint, outcome.resolver(), world);
        if (!result.hasPlacements() && result.getIssue() == PatternGenerationIssue.NONE) {
            result.setIssue(outcome.issue());
        }
        return result;
    }
}
