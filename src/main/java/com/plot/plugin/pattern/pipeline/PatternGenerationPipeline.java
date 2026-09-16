package com.plot.plugin.pattern.pipeline;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.pattern.PatternGenerationResult;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.space.PatternSample;
import com.plot.plugin.pattern.space.PatternSampling;
import com.plot.plugin.pattern.space.PatternSpace;
import net.minecraft.util.math.BlockPos;
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
            return result;
        }
        if (footprint.getOuterPoints().size() < 3) {
            return result;
        }

        PatternSpace space = PatternSpace.fromFootprint(footprint);
        List<PatternSample> samples = PatternSampling.collectFootprintSamples(footprint.getOuterPoints());
        if (samples.isEmpty()) {
            return result;
        }

        TerrainSurfaceProjector projector = TerrainSurfaceProjector.of(world, coordinates);
        PatternPlacementRecorder recorder = new PatternPlacementRecorder(projection, result);

        for (PatternSample sample : samples) {
            String blockId = materialResolver.resolveMaterial(space, sample);
            if (blockId == null) {
                continue;
            }
            BlockPos pos = projector.projectSurface(sample);
            recorder.record(pos, blockId);
        }
        return result;
    }
}
