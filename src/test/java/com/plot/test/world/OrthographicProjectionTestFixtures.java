package com.plot.test.world;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.api.world.WorldViewBounds;

/**
 * 模拟 {@link com.plot.infrastructure.coordinate.CoordinateTransformer} 的正交投影：
 * 画布归一化 → 相机相对视野 → 叠加玩家世界原点。
 */
public final class OrthographicProjectionTestFixtures {
    public static final float CANVAS_WIDTH = 800f;
    public static final float CANVAS_HEIGHT = 600f;
    public static final float VIEW_DISTANCE = 100f;
    public static final float VIEW_SCALE = 1f;

    public static final double PLAYER_X = 128.0;
    public static final double PLAYER_Z = -64.0;

    /** 相对玩家位置的相机视野（与 CoordinateTransformer.CameraViewBounds 一致）。 */
    public static final float RELATIVE_LEFT = -160f;
    public static final float RELATIVE_RIGHT = 160f;
    public static final float RELATIVE_BOTTOM = -120f;
    public static final float RELATIVE_TOP = 120f;

    private OrthographicProjectionTestFixtures() {
    }

    public static ICoordinateService liveOrthographic() {
        return new LiveOrthographicCoordinateService();
    }

    private static final class LiveOrthographicCoordinateService implements ICoordinateService {
        @Override
        public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
            Vec2d relative = mapCanvasToRelativeView(canvasPos);
            return new Vec2d(relative.x + PLAYER_X, relative.y + PLAYER_Z);
        }

        @Override
        public WorldViewBounds getMinecraftWorldViewBounds() {
            return new WorldViewBounds(
                PLAYER_X + RELATIVE_LEFT,
                PLAYER_X + RELATIVE_RIGHT,
                PLAYER_Z + RELATIVE_BOTTOM,
                PLAYER_Z + RELATIVE_TOP);
        }

        @Override
        public WorldProjectionSnapshot captureProjection() {
            return new WorldProjectionSnapshot(
                getMinecraftWorldViewBounds(),
                VIEW_DISTANCE,
                VIEW_SCALE,
                CANVAS_WIDTH,
                CANVAS_HEIGHT);
        }
    }

    static Vec2d mapCanvasToRelativeView(Vec2d canvasPos) {
        double normalizedX = canvasPos.x / CANVAS_WIDTH;
        double normalizedY = canvasPos.y / CANVAS_HEIGHT;
        double relativeX = RELATIVE_LEFT + (RELATIVE_RIGHT - RELATIVE_LEFT) * normalizedX;
        double relativeZ = RELATIVE_BOTTOM + (RELATIVE_TOP - RELATIVE_BOTTOM) * normalizedY;
        return new Vec2d(round2(relativeX), round2(relativeZ));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
