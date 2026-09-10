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
    private final ICoordinateService coordinates;
    private final WorldProjectionSnapshot snapshot;

    private PluginProjectionContext(
            ICoordinateService coordinates,
            WorldProjectionSnapshot snapshot) {
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public static PluginProjectionContext capture(ICoordinateService coordinates) {
        Objects.requireNonNull(coordinates, "coordinates");
        WorldProjectionSnapshot snapshot = coordinates.captureProjection();
        if (!snapshot.isValid()) {
            throw new WorldProjectionUnavailableException("World projection is unavailable");
        }
        return new PluginProjectionContext(coordinates, snapshot);
    }

    public static Optional<PluginProjectionContext> tryCapture(ICoordinateService coordinates) {
        if (coordinates == null) {
            return Optional.empty();
        }
        WorldProjectionSnapshot snapshot = coordinates.captureProjection();
        if (!snapshot.isValid()) {
            return Optional.empty();
        }
        return Optional.of(new PluginProjectionContext(coordinates, snapshot));
    }

    public ICoordinateService coordinates() {
        return coordinates;
    }

    public WorldProjectionSnapshot snapshot() {
        return snapshot;
    }

    public Vec2d toWorld(Vec2d canvas) {
        return coordinates.canvasToMinecraftWorld(canvas);
    }

    public double distance(Vec2d canvasA, Vec2d canvasB) {
        return coordinates.projectedDistance(canvasA, canvasB);
    }

    public double pathLength(List<Vec2d> pathPoints) {
        return coordinates.pathWorldLength(pathPoints);
    }
}
