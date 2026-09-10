package com.plot.test.world;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.SnapshotCoordinateService;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.api.world.WorldViewBounds;

/**
 * 测试用坐标服务：1 canvas unit = 1 Minecraft block（均匀缩放）。
 */
public final class IdentityCoordinateService implements ICoordinateService {
    public static final IdentityCoordinateService INSTANCE = new IdentityCoordinateService();
    private static final SnapshotCoordinateService DELEGATE = SnapshotCoordinateService.uniformScale(1.0);

    private IdentityCoordinateService() {
    }

    @Override
    public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
        return DELEGATE.canvasToMinecraftWorld(canvasPos);
    }

    @Override
    public WorldViewBounds getMinecraftWorldViewBounds() {
        return DELEGATE.getMinecraftWorldViewBounds();
    }

    @Override
    public WorldProjectionSnapshot captureProjection() {
        return DELEGATE.captureProjection();
    }
}
