package com.plot.plugin.pattern.pipeline;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.pattern.PatternGenerationIssue;
import com.plot.plugin.pattern.PatternGenerationResult;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.space.PatternSample;
import com.plot.plugin.pattern.space.PatternSampling;
import com.plot.plugin.pattern.space.PatternSpace;
import net.minecraft.world.World;

import java.util.List;
import java.util.Objects;

/**
 * 图案生成管线：
 * <ol>
 *   <li>Pattern Space 采样</li>
 *   <li>材质解析</li>
 *   <li>地形投影</li>
 *   <li>放置记录</li>
 * </ol>
 */
public final class PatternGenerationPipeline {
    private final ICoordinateService coordinates;
    private final IBlockProjectionService projection;

    public PatternGenerationPipeline(
            ICoordinateService coordinates,
            IBlockProjectionService projection) {
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates");
        this.projection = Objects.requireNonNull(projection, "projection");
    }

    public PatternGenerationResult generate(
            PatternFootprint footprint,
            PatternMaterialResolver materialResolver,
            World world) {
        PatternGenerationResult result = new PatternGenerationResult();
        if (footprint == null || materialResolver == null) {
            result.setIssue(PatternGenerationIssue.NO_SAMPLE_POINTS);
            return result;
        }
        if (footprint.getOuterPoints().size() < 3) {
            result.setIssue(PatternGenerationIssue.REGION_TOO_SMALL);
            return result;
        }

        PatternSpace space = PatternSpace.fromFootprint(footprint);
        List<PatternSample> samples = PatternSampling.collectFootprintSamples(
            footprint.getOuterPoints(),
            footprint.getHoles());
        result.setSampleCount(samples.size());
        if (samples.isEmpty()) {
            result.setIssue(PatternGenerationIssue.NO_SAMPLE_POINTS);
            return result;
        }

        TerrainSurfaceProjector projector = TerrainSurfaceProjector.of(world, coordinates);
        PatternPlacementRecorder recorder = new PatternPlacementRecorder(projection, result);

        int skippedTransparent = 0;
        int fallbackElevation = 0;
        for (PatternSample sample : samples) {
            String blockId = materialResolver.resolveMaterial(space, sample);
            if (blockId == null) {
                skippedTransparent++;
                continue;
            }
            TerrainSurfaceProjector.SurfaceProjection surface = projector.projectSurface(sample);
            if (surface.usedFallbackElevation()) {
                fallbackElevation++;
            }
            recorder.record(surface.pos(), blockId);
        }

        result.setSkippedTransparentCount(skippedTransparent);
        result.setFallbackElevationCount(fallbackElevation);
        if (!result.hasPlacements()) {
            if (skippedTransparent >= samples.size()) {
                result.setIssue(PatternGenerationIssue.ALL_PIXELS_TRANSPARENT);
            } else {
                result.setIssue(PatternGenerationIssue.NO_SAMPLE_POINTS);
            }
        }
        return result;
    }
}
