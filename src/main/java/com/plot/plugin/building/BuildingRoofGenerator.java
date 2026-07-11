package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;

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
            int topFloorY,
            String roofBlockId,
            BuildingFootprint.RoofType roofType,
            CoordinateTransformer transformer,
            BlockProjectionHandler projectionHandler) {
        Polygon roofPolygon = BuildingGeometryUtils.toPolygon(outerPoints);
        BuildingGeometryUtils.RectBounds bounds = BuildingGeometryUtils.normalizedRectBounds(outerPoints);
        int pitch = Math.max(1, footprint.getRoofPitchRatio());
        boolean ridgeAlongX = bounds.width() >= bounds.depth();

        for (Vec2d center : BuildingGeometryUtils.collectFootprintCellCenters(outerPoints)) {
            if (!roofPolygon.contains(center)) {
                continue;
            }

            int rise = switch (roofType) {
                case GABLE -> computeGableRise(center.x, center.y, bounds, ridgeAlongX, pitch);
                case HIP -> computeHipRise(center.x, center.y, bounds, pitch);
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

    /**
     * 双坡顶：檐口 rise=0，屋脊 rise 最大。坡度按垂直于屋脊方向的檐口距离计算。
     */
    static int computeGableRise(
            double x,
            double z,
            BuildingGeometryUtils.RectBounds bounds,
            boolean ridgeAlongX,
            int pitch) {
        double distToEave = ridgeAlongX
            ? Math.min(z - bounds.minZ(), bounds.maxZ() - z)
            : Math.min(x - bounds.minX(), bounds.maxX() - x);
        return riseFromEaveDistance(distToEave, pitch);
    }

    /**
     * 四坡顶：四角檐口 rise=0，中心屋脊/屋脊线 rise 最大。取到四条边最近距离控制坡度。
     */
    static int computeHipRise(
            double x,
            double z,
            BuildingGeometryUtils.RectBounds bounds,
            int pitch) {
        double distToEdge = Math.min(
            Math.min(x - bounds.minX(), bounds.maxX() - x),
            Math.min(z - bounds.minZ(), bounds.maxZ() - z));
        return riseFromEaveDistance(distToEdge, pitch);
    }

    static int riseFromEaveDistance(double distToEave, int pitch) {
        if (distToEave <= 0.0) {
            return 0;
        }
        return (int) Math.floor(distToEave / pitch);
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
