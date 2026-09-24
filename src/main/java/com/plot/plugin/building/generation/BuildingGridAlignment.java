package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.shapes.Polygon;

import java.util.ArrayList;
import java.util.List;

/**
 * 建筑体素格网对齐：墙体 / 楼板 / 屋顶 / 开洞共用同一方块步长与格心吸附规则，
 * 避免投影缩放或弧长采样导致整体平移一格。
 * <p>
 * 格点以<strong>世界方块列</strong>为权威：遍历 footprint 世界包围盒内的整数列，
 * 再反投影到画布做包含测试，保证相邻世界列不会被画布格网相位误差跳过。
 */
public final class BuildingGridAlignment {
    private BuildingGridAlignment() {
    }

    /** 1 世界方块在画布 X / Z 方向的步长。 */
    public static Vec2d blockCellSteps(BuildingCanvasScale scale, List<Vec2d> outerPoints) {
        return scale.blockCellSteps(outerPoints);
    }

    /**
     * 收集 footprint 内所有世界方块列对应的画布格心。
     */
    public static List<Vec2d> collectWorldAlignedCellCenters(
            List<Vec2d> outerPoints,
            Polygon polygon,
            BuildingCanvasScale scale) {
        if (outerPoints == null || outerPoints.size() < 3 || polygon == null || scale == null) {
            return List.of();
        }
        ICoordinateService coords = scale.resolveCoordinates(null);
        BlockBounds bounds = worldBlockBounds(outerPoints, coords);
        if (bounds.empty()) {
            return List.of();
        }
        List<Vec2d> centers = new ArrayList<>();
        for (int blockX = bounds.minX(); blockX <= bounds.maxX(); blockX++) {
            for (int blockZ = bounds.minZ(); blockZ <= bounds.maxZ(); blockZ++) {
                Vec2d canvas = scale.worldBlockCenterToCanvas(blockX, blockZ);
                if (polygon.contains(canvas)) {
                    centers.add(canvas);
                }
            }
        }
        return centers;
    }

    /**
     * 将任意画布点吸附到其所在世界方块列的中心（画布坐标）。
     */
    public static Vec2d snapToBlockCellCenter(
            Vec2d canvasPos,
            BuildingCanvasScale scale,
            List<Vec2d> outerPoints) {
        if (canvasPos == null || scale == null) {
            return canvasPos;
        }
        ICoordinateService coords = scale.resolveCoordinates(null);
        Vec2d world = coords.canvasToMinecraftWorld(canvasPos);
        if (world == null) {
            return canvasPos;
        }
        int blockX = (int) Math.floor(world.x);
        int blockZ = (int) Math.floor(world.y);
        return scale.worldBlockCenterToCanvas(blockX, blockZ);
    }

    private static BlockBounds worldBlockBounds(List<Vec2d> points, ICoordinateService coords) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Vec2d point : points) {
            if (point == null) {
                continue;
            }
            Vec2d world = coords.canvasToMinecraftWorld(point);
            if (world == null) {
                continue;
            }
            int blockX = (int) Math.floor(world.x);
            int blockZ = (int) Math.floor(world.y);
            minX = Math.min(minX, blockX);
            maxX = Math.max(maxX, blockX);
            minZ = Math.min(minZ, blockZ);
            maxZ = Math.max(maxZ, blockZ);
        }
        if (minX == Integer.MAX_VALUE) {
            return BlockBounds.EMPTY;
        }
        // 轮廓边界的方块中心可能落在顶点投影范围之外
        return new BlockBounds(minX - 1, maxX + 1, minZ - 1, maxZ + 1);
    }

    private record BlockBounds(int minX, int maxX, int minZ, int maxZ) {
        private static final BlockBounds EMPTY = new BlockBounds(0, -1, 0, -1);

        boolean empty() {
            return minX > maxX || minZ > maxZ;
        }
    }
}
