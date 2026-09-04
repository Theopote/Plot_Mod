package com.plot.plugin.building.generation.component;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingBlockWriter;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver;
import com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver.ResolvedFloorPlate;
import com.plot.plugin.building.generation.facade.FacadeEdgeResolver;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.CanopySpec;
import com.plot.plugin.building.model.spec.EnvelopeSpec;
import com.plot.plugin.building.model.spec.MassingSpec;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Set;

/**
 * 入口雨篷：墙外水平顶板，由净高参数确定标高。
 */
public final class CanopyGenerator {
    private CanopyGenerator() {
    }

    public static void generate(BuildingGenerationContext context, CanopySpec spec) {
        if (spec == null) {
            return;
        }
        BuildingDefinition definition = context.getDefinition();
        MassingSpec massing = definition.massing();
        if (spec.floor() >= massing.floors()) {
            return;
        }

        EnvelopeSpec envelope = definition.envelope();
        ResolvedFloorPlate plate = FloorPlateGeometryResolver.resolve(
            massing.plateForFloor(spec.floor()), envelope.wallThickness());
        List<Vec2d> outerPoints = plate.outerPoints();
        int segmentIndex = FacadeEdgeResolver.resolveSegmentIndex(
            definition.facade().edgeScope(),
            spec.wallSegmentIndex(),
            massing.baseOuterPoints(),
            outerPoints);

        int floorBaseY = context.getBaseElevation() + spec.floor() * massing.floorHeight();
        int canopyY = floorBaseY + spec.clearance();
        BuildingGenerationResult result = context.getResult();
        String blockId = BuildingGeometryUtils.resolveBlockId(spec.resolvedMaterial());

        Set<BlockPos> slabPositions = WallAttachmentPlacer.placeHorizontalSlab(
            result,
            outerPoints,
            segmentIndex,
            spec.positionRatio(),
            canopyY,
            spec.width(),
            spec.depth(),
            blockId,
            context.getCoordinateService(),
            context.getProjectionService());

        placeSupportPosts(result, slabPositions, floorBaseY, blockId, context.getProjectionService());
    }

    private static void placeSupportPosts(
            BuildingGenerationResult result,
            Set<BlockPos> slabPositions,
            int floorBaseY,
            String blockId,
            com.plot.api.world.IBlockProjectionService projectionService) {
        if (slabPositions.isEmpty()) {
            return;
        }
        int minDepthX = Integer.MAX_VALUE;
        int minDepthZ = Integer.MAX_VALUE;
        for (BlockPos slab : slabPositions) {
            minDepthX = Math.min(minDepthX, slab.getX());
            minDepthZ = Math.min(minDepthZ, slab.getZ());
        }
        for (BlockPos slab : slabPositions) {
            if (slab.getX() != minDepthX && slab.getZ() != minDepthZ) {
                continue;
            }
            for (int y = floorBaseY + 1; y < slab.getY(); y++) {
                BlockPos post = new BlockPos(slab.getX(), y, slab.getZ());
                BuildingBlockWriter.recordBlock(result, post, blockId, projectionService);
            }
        }
    }
}
