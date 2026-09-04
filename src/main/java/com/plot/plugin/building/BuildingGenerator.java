package com.plot.plugin.building;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.building.generation.BuildingBlockWriter;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 建筑生成器门面。实际生成由 {@link BuildingGenerationPipeline} 完成。
 */
public class BuildingGenerator {
    private final ICoordinateService coordinateTransformer;
    private final IBlockProjectionService projectionHandler;
    private final BuildingGenerationPipeline pipeline;

    public BuildingGenerator(
            ICoordinateService coordinateTransformer,
            IBlockProjectionService projectionHandler) {
        this.coordinateTransformer = java.util.Objects.requireNonNull(
            coordinateTransformer, "coordinateTransformer");
        this.projectionHandler = java.util.Objects.requireNonNull(
            projectionHandler, "projectionHandler");
        this.pipeline = BuildingGenerationPipeline.createDefault();
    }

    public BuildingGenerationResult generate(BuildingFootprint footprint, World world) {
        BuildingGenerationContext context = BuildingGenerationContext.create(
            footprint, world, coordinateTransformer, projectionHandler);
        return pipeline.generate(context);
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
