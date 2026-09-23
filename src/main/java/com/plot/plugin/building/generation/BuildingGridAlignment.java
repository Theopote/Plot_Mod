package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.geometry.PolygonRegionUtils.RectBounds;

import java.util.List;

/**
 * 建筑体素格网对齐：墙体 / 楼板 / 屋顶 / 开洞共用同一方块步长与格心吸附规则，
 * 避免投影缩放或弧长采样导致整体平移一格。
 */
public final class BuildingGridAlignment {
    private BuildingGridAlignment() {
    }

    /** 1 世界方块在画布上的步长。 */
    public static double blockCellSizeCanvas(BuildingCanvasScale scale, List<Vec2d> outerPoints) {
        return scale.uniformBlocksToCanvas(1.0, outerPoints);
    }

    /**
     * 将任意画布点吸附到与 {@link com.plot.core.geometry.PolygonRegionUtils#collectFootprintCellCenters}
     * 相同的方块格网中心。
     */
    public static Vec2d snapToBlockCellCenter(Vec2d canvasPos, List<Vec2d> outerPoints, double cellSize) {
        if (canvasPos == null || outerPoints == null || outerPoints.size() < 3 || cellSize < 1e-9) {
            return canvasPos;
        }
        RectBounds bounds = PolygonRegionUtils.computeBounds(outerPoints, List.of());
        double startX = Math.floor(bounds.minX() / cellSize) * cellSize + cellSize * 0.5;
        double startZ = Math.floor(bounds.minZ() / cellSize) * cellSize + cellSize * 0.5;
        long ix = Math.round((canvasPos.x - startX) / cellSize);
        long iz = Math.round((canvasPos.y - startZ) / cellSize);
        return new Vec2d(startX + ix * cellSize, startZ + iz * cellSize);
    }

    public static Vec2d snapToBlockCellCenter(
            Vec2d canvasPos,
            BuildingCanvasScale scale,
            List<Vec2d> outerPoints) {
        return snapToBlockCellCenter(canvasPos, outerPoints, blockCellSizeCanvas(scale, outerPoints));
    }
}
