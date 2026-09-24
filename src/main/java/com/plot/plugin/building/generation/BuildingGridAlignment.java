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

    /** 1 世界方块在画布上的平均步长（仅用于向后兼容；格网采样请用 {@link #blockCellSteps}）。 */
    public static double blockCellSizeCanvas(BuildingCanvasScale scale, List<Vec2d> outerPoints) {
        return scale.uniformBlocksToCanvas(1.0, outerPoints);
    }

    /** 1 世界方块在画布 X / Z 方向的步长。 */
    public static Vec2d blockCellSteps(BuildingCanvasScale scale, List<Vec2d> outerPoints) {
        return scale.blockCellSteps(outerPoints);
    }

    /**
     * 将任意画布点吸附到与 {@link com.plot.core.geometry.PolygonRegionUtils#collectFootprintCellCenters}
     * 相同的方块格网中心。
     */
    public static Vec2d snapToBlockCellCenter(
            Vec2d canvasPos,
            List<Vec2d> outerPoints,
            double stepX,
            double stepZ) {
        if (canvasPos == null || outerPoints == null || outerPoints.size() < 3) {
            return canvasPos;
        }
        double sx = Math.max(1e-6, stepX);
        double sz = Math.max(1e-6, stepZ);
        RectBounds bounds = PolygonRegionUtils.computeBounds(outerPoints, List.of());
        double startX = Math.floor(bounds.minX() / sx) * sx + sx * 0.5;
        double startZ = Math.floor(bounds.minZ() / sz) * sz + sz * 0.5;
        long ix = Math.round((canvasPos.x - startX) / sx);
        long iz = Math.round((canvasPos.y - startZ) / sz);
        return new Vec2d(startX + ix * sx, startZ + iz * sz);
    }

    public static Vec2d snapToBlockCellCenter(Vec2d canvasPos, List<Vec2d> outerPoints, double cellSize) {
        return snapToBlockCellCenter(canvasPos, outerPoints, cellSize, cellSize);
    }

    public static Vec2d snapToBlockCellCenter(
            Vec2d canvasPos,
            BuildingCanvasScale scale,
            List<Vec2d> outerPoints) {
        Vec2d steps = blockCellSteps(scale, outerPoints);
        return snapToBlockCellCenter(canvasPos, outerPoints, steps.x, steps.y);
    }
}
