package com.plot.plugin.building.generation.component;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingBlockWriter;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.api.world.IBlockProjectionService;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 沿墙段外挑放置水平构件（阳台板、雨篷等）。
 */
public final class WallAttachmentPlacer {
    private WallAttachmentPlacer() {
    }

    public static Set<BlockPos> placeHorizontalSlab(
            BuildingGenerationResult result,
            List<Vec2d> outerPoints,
            int wallSegmentIndex,
            double positionRatio,
            int floorY,
            int width,
            int depth,
            String blockId,
            ICoordinateService coordinateService,
            IBlockProjectionService projectionService) {
        Set<BlockPos> placed = new LinkedHashSet<>();
        if (result == null || outerPoints == null || outerPoints.size() < 3 || depth <= 0 || width <= 0) {
            return placed;
        }
        Vec2d anchor = BuildingGeometryUtils.pointOnWallSegment(outerPoints, wallSegmentIndex, positionRatio);
        if (anchor == null) {
            return placed;
        }
        Vec2d tangent = BuildingGeometryUtils.wallSegmentTangent(outerPoints, wallSegmentIndex);
        Vec2d outward = BuildingGeometryUtils.outwardNormal(outerPoints, wallSegmentIndex);

        int halfWidth = width / 2;
        for (int w = -halfWidth; w < width - halfWidth; w++) {
            for (int d = 1; d <= depth; d++) {
                Vec2d center = anchor
                    .add(tangent.multiply(w))
                    .add(outward.multiply(d));
                BlockPos column = BuildingGeometryUtils.canvasToBlockXZ(center, coordinateService);
                BlockPos pos = new BlockPos(column.getX(), floorY, column.getZ());
                BuildingBlockWriter.recordBlock(result, pos, blockId, projectionService);
                placed.add(pos);
            }
        }
        return placed;
    }

    public static void placeRailingAround(
            BuildingGenerationResult result,
            Set<BlockPos> slabPositions,
            int railingY,
            String railingBlockId,
            IBlockProjectionService projectionService) {
        if (slabPositions == null || slabPositions.isEmpty()) {
            return;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : slabPositions) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos slab = new BlockPos(x, railingY - 1, z);
                if (!slabPositions.contains(slab)) {
                    continue;
                }
                boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
                if (!edge) {
                    continue;
                }
                BlockPos railingPos = new BlockPos(x, railingY, z);
                BuildingBlockWriter.recordBlock(result, railingPos, railingBlockId, projectionService);
            }
        }
    }
}
