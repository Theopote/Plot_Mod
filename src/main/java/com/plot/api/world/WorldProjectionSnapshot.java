package com.plot.api.world;

import com.plot.api.geometry.Vec2d;

import java.util.List;

/**
 * 冻结的 Canvas → Minecraft 投影快照。
 * <p>
 * 预览/生成应捕获一次并在同一次操作中复用；{@link #toWorld} 与距离计算仅依赖本快照，
 * 不得再访问实时 {@link ICoordinateService} / 相机状态。
 */
public record WorldProjectionSnapshot(
        WorldViewBounds worldBounds,
        float viewDistance,
        float viewScale,
        float canvasWidth,
        float canvasHeight) {

    public static final WorldProjectionSnapshot UNKNOWN =
        new WorldProjectionSnapshot(null, 0f, 0f, 0f, 0f);

    /** 投影可用于插件生成/预览。 */
    public boolean isValid() {
        return worldBounds != null
            && canvasWidth > 0f
            && canvasHeight > 0f
            && viewDistance > 0f;
    }

    /** 画布点 → Minecraft XZ 世界坐标（blocks）。 */
    public Vec2d toWorld(Vec2d canvas) {
        if (canvas == null) {
            throw new WorldProjectionUnavailableException("Canvas point cannot be null");
        }
        if (!isValid()) {
            throw new WorldProjectionUnavailableException("World projection snapshot is invalid");
        }
        double worldX = worldBounds.minX()
            + (canvas.x / canvasWidth) * (worldBounds.maxX() - worldBounds.minX());
        double worldZ = worldBounds.minZ()
            + (canvas.y / canvasHeight) * (worldBounds.maxZ() - worldBounds.minZ());
        worldX = Math.round(worldX * 100.0) / 100.0;
        worldZ = Math.round(worldZ * 100.0) / 100.0;
        return new Vec2d(worldX, worldZ);
    }

    public double projectedDistance(Vec2d canvasA, Vec2d canvasB) {
        if (canvasA == null || canvasB == null) {
            return 0.0;
        }
        return toWorld(canvasA).distance(toWorld(canvasB));
    }

    public double pathWorldLength(List<Vec2d> pathPoints) {
        if (pathPoints == null || pathPoints.size() < 2) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            total += projectedDistance(pathPoints.get(i), pathPoints.get(i + 1));
        }
        return total;
    }

    public int fingerprint() {
        int hash = 1;
        if (worldBounds != null) {
            hash = 31 * hash + Double.hashCode(worldBounds.minX());
            hash = 31 * hash + Double.hashCode(worldBounds.maxX());
            hash = 31 * hash + Double.hashCode(worldBounds.minZ());
            hash = 31 * hash + Double.hashCode(worldBounds.maxZ());
        }
        hash = 31 * hash + Float.hashCode(viewDistance);
        hash = 31 * hash + Float.hashCode(viewScale);
        hash = 31 * hash + Float.hashCode(canvasWidth);
        hash = 31 * hash + Float.hashCode(canvasHeight);
        return hash;
    }

    public static WorldProjectionSnapshot fromBounds(WorldViewBounds bounds) {
        return new WorldProjectionSnapshot(bounds, 0f, 0f, 0f, 0f);
    }
}
