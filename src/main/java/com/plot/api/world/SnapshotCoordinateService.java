package com.plot.api.world;

import com.plot.api.geometry.Vec2d;
import java.util.List;
import java.util.Objects;

/**
 * 仅依赖 {@link WorldProjectionSnapshot} 的坐标服务，生成过程中 transform 不再变化。
 */
public final class SnapshotCoordinateService implements ICoordinateService {
    private final WorldProjectionSnapshot snapshot;

    public SnapshotCoordinateService(WorldProjectionSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        if (!snapshot.isValid()) {
            throw new WorldProjectionUnavailableException("World projection snapshot is invalid");
        }
    }

    @Override
    public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
        return snapshot.toWorld(canvasPos);
    }

    @Override
    public WorldViewBounds getMinecraftWorldViewBounds() {
        return snapshot.worldBounds();
    }

    @Override
    public WorldProjectionSnapshot captureProjection() {
        return snapshot;
    }

    @Override
    public double projectedDistance(Vec2d canvasA, Vec2d canvasB) {
        return snapshot.projectedDistance(canvasA, canvasB);
    }

    /** 均匀缩放：1 canvas unit = {@code blocksPerCanvasUnit} blocks。 */
    public static SnapshotCoordinateService uniformScale(double blocksPerCanvasUnit) {
        float canvasWidth = 800f;
        float canvasHeight = 600f;
        double scale = Math.max(1e-9, blocksPerCanvasUnit);
        WorldViewBounds bounds = new WorldViewBounds(
            0.0,
            canvasWidth * scale,
            0.0,
            canvasHeight * scale);
        return new SnapshotCoordinateService(new WorldProjectionSnapshot(
            bounds,
            (float) (100.0 * scale),
            1f,
            canvasWidth,
            canvasHeight));
    }
}
