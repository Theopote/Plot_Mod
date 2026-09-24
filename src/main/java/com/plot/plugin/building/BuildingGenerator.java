package com.plot.plugin.building;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.building.generation.BuildingBlockWriter;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.generation.DistrictMassingGenerator;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collection;

/**
 * 建筑生成器门面。实际生成由 {@link BuildingGenerationPipeline} 完成。
 */
public class BuildingGenerator {
    private final ICoordinateService coordinateTransformer;
    private final IBlockProjectionService projectionHandler;
    private final BuildingGenerationPipeline defaultPipeline;
    private final BuildingGenerationPipeline framePipeline;

    public BuildingGenerator(
            ICoordinateService coordinateTransformer,
            IBlockProjectionService projectionHandler) {
        this.coordinateTransformer = java.util.Objects.requireNonNull(
            coordinateTransformer, "coordinateTransformer");
        this.projectionHandler = java.util.Objects.requireNonNull(
            projectionHandler, "projectionHandler");
        this.defaultPipeline = BuildingGenerationPipeline.createDefault();
        this.framePipeline = BuildingGenerationPipeline.createFrameOnly();
    }

    public BuildingGenerationResult generate(BuildingFootprint footprint, World world) {
        return generate(footprint, world, false);
    }

    public BuildingGenerationResult generate(BuildingFootprint footprint, World world, boolean frameOnly) {
        BuildingGenerationContext context = BuildingGenerationContext.create(
            footprint, world, coordinateTransformer, projectionHandler);
        BuildingGenerationPipeline pipeline = frameOnly ? framePipeline : defaultPipeline;
        return pipeline.generate(context);
    }

    /**
     * 片区批量生成（fail-soft）：单栋失败记入 skipped，其余继续。
     */
    public DistrictGenerationResult generateDistrict(
            Collection<BuildingFootprint> buildings,
            World world) {
        return DistrictMassingGenerator.generate(
            buildings,
            footprint -> generate(footprint, world));
    }

    /** package-private for unit tests of override semantics */
    static void recordBlockForTest(
            BuildingGenerationResult result,
            BlockPos pos,
            String previousBlockId,
            String newBlockId) {
        BuildingBlockWriter.recordBlockWithPrevious(result, pos, previousBlockId, newBlockId);
    }
}
