package com.plot.api.world;

import com.plot.api.geometry.Vec2d;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 一次预览/生成固定的 Canvas → World 投影上下文。
 * <p>
 * 所有插件生成入口应通过 {@link #capture(ICoordinateService)} 获取，
 * 投影无效时立即失败，不得继续几何计算。
 */
public final class PluginProjectionContext {
    private final WorldProjectionSnapshot snapshot;
    private final SnapshotCoordinateService coordinates;

    private PluginProjectionContext(WorldProjectionSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.coordinates = new SnapshotCoordinateService(snapshot);
    }

    public static PluginProjectionContext capture(ICoordinateService coordinates) {
        Objects.requireNonNull(coordinates, "coordinates");
        WorldProjectionSnapshot snapshot = coordinates.captureProjection();
        if (!snapshot.isValid()) {
            throw new WorldProjectionUnavailableException("World projection is unavailable");
        }
        return new PluginProjectionContext(snapshot);
    }

    public static Optional<PluginProjectionContext> tryCapture(ICoordinateService coordinates) {
        if (coordinates == null) {
            return Optional.empty();
        }
        WorldProjectionSnapshot snapshot = coordinates.captureProjection();
        if (!snapshot.isValid()) {
            return Optional.empty();
        }
        return Optional.of(new PluginProjectionContext(snapshot));
    }

    /** 冻结后的坐标服务，生成全程应只使用此实例。 */
    public ICoordinateService coordinates() {
        return coordinates;
    }

    public WorldProjectionSnapshot snapshot() {
        return snapshot;
    }

    public Vec2d toWorld(Vec2d canvas) {
        return snapshot.toWorld(canvas);
    }

    public double distance(Vec2d canvasA, Vec2d canvasB) {
        return snapshot.projectedDistance(canvasA, canvasB);
    }

    public double pathLength(List<Vec2d> pathPoints) {
        return snapshot.pathWorldLength(pathPoints);
    }
}
