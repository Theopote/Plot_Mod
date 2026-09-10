package com.plot.test.world;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.api.world.WorldViewBounds;

/**
 * 测试用坐标服务：1 canvas unit = 1 Minecraft block（均匀缩放）。
 */
public final class IdentityCoordinateService implements ICoordinateService {
    public static final IdentityCoordinateService INSTANCE = new IdentityCoordinateService();

    private IdentityCoordinateService() {
    }

    @Override
    public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
        return canvasPos != null ? canvasPos.copy() : new Vec2d(0, 0);
    }

    @Override
    public WorldViewBounds getMinecraftWorldViewBounds() {
        return new WorldViewBounds(-1.0e9, 1.0e9, -1.0e9, 1.0e9);
    }

    @Override
    public WorldProjectionSnapshot captureProjection() {
        return new WorldProjectionSnapshot(
            getMinecraftWorldViewBounds(),
            100f,
            1f,
            800f,
            600f);
    }
}
