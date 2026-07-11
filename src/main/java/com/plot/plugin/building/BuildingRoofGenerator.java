package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 坡屋顶生成器（矩形建筑）
 */
public final class BuildingRoofGenerator {
    private BuildingRoofGenerator() {
    }

    public static void generate(
            BuildingGenerator.BuildingGenerationResult result,
            BuildingFootprint footprint,
            List<Vec2d> outerPoints,
            World world,
            int baseElevation,
            int topFloorY,
            String roofBlockId,
            BuildingFootprint.RoofType roofType,
            CoordinateTransformer transformer,
            BlockProjectionHandler projectionHandler) {
        BuildingGeometryUtils.RectBounds bounds = BuildingGeometryUtils.computeBounds(outerPoints);
        int pitch = Math.max(1, footprint.getRoofPitchRatio());

        int minBlockX = (int) Math.floor(bounds.minX());
        int maxBlockX = (int) Math.ceil(bounds.maxX());
        int minBlockZ = (int) Math.floor(bounds.minZ());
        int maxBlockZ = (int) Math.ceil(bounds.maxZ());

        boolean ridgeAlongX = bounds.width() >= bounds.depth();
        double ridgeCoord = ridgeAlongX ? bounds.center().y : bounds.center().x;

        for (int x = minBlockX; x <= maxBlockX; x++) {
            for (int z = minBlockZ; z <= maxBlockZ; z++) {
                Vec2d center = new Vec2d(x + 0.5, z + 0.5);
                if (!BuildingGeometryUtils.toPolygon(outerPoints).contains(center)) {
                    continue;
                }

                int rise = switch (roofType) {
                    case GABLE -> computeGableRise(x + 0.5, z + 0.5, bounds, ridgeAlongX, ridgeCoord, pitch);
                    case HIP -> computeHipRise(x + 0.5, z + 0.5, bounds, ridgeAlongX, ridgeCoord, pitch);
                    default -> 0;
                };

                if (rise <= 0) {
                    continue;
                }

                BlockPos column = BuildingGeometryUtils.canvasToBlockXZ(center, transformer);
                for (int layer = 1; layer <= rise; layer++) {
                    BlockPos pos = new BlockPos(column.getX(), topFloorY + layer, column.getZ());
                    recordBlock(result, pos, roofBlockId, projectionHandler);
                }
            }
        }
    }

    static int computeGableRise(
            double x,
            double z,
            BuildingGeometryUtils.RectBounds bounds,
            boolean ridgeAlongX,
            double ridgeCoord,
            int pitch) {
        double distanceToRidge = ridgeAlongX
            ? Math.abs(z - ridgeCoord)
            : Math.abs(x - ridgeCoord);
        return (int) Math.floor(distanceToRidge / pitch);
    }

    static int computeHipRise(
            double x,
            double z,
            BuildingGeometryUtils.RectBounds bounds,
            boolean ridgeAlongX,
            double ridgeCoord,
            int pitch) {
        double distToRidge = ridgeAlongX
            ? Math.abs(z - ridgeCoord)
            : Math.abs(x - ridgeCoord);

        double distToShortEdge;
        if (ridgeAlongX) {
            distToShortEdge = Math.min(
                Math.abs(x - bounds.minX()),
                Math.abs(bounds.maxX() - x));
        } else {
            distToShortEdge = Math.min(
                Math.abs(z - bounds.minZ()),
                Math.abs(bounds.maxZ() - z));
        }

        double controllingDistance = Math.min(distToRidge, distToShortEdge);
        return (int) Math.floor(controllingDistance / pitch);
    }

    private static void recordBlock(
            BuildingGenerator.BuildingGenerationResult result,
            BlockPos pos,
            String newBlockId,
            BlockProjectionHandler projectionHandler) {
        if (!result.placementRecords.containsKey(pos)) {
            String previous = projectionHandler.getBlockIdAt(pos);
            result.placementRecords.put(pos, new BlockRecord(pos, previous, newBlockId));
        }
    }
}
