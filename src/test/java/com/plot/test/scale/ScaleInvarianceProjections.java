package com.plot.test.scale;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.WorldProjectionMath;
import com.plot.test.world.CoordinateTestFixtures;
import com.plot.test.world.IdentityCoordinateService;

/**
 * 跨插件尺度不变性测试用的标准投影对：近景 1:1 与远景 4 blocks/canvas unit。
 */
public final class ScaleInvarianceProjections {
    /** 远景：1 canvas unit = 4 Minecraft blocks。 */
    public static final double FAR_BLOCKS_PER_CANVAS_UNIT = 4.0;

    public static final ICoordinateService NEAR = IdentityCoordinateService.INSTANCE;
    public static final ICoordinateService FAR =
        CoordinateTestFixtures.uniformScale(FAR_BLOCKS_PER_CANVAS_UNIT);

    private ScaleInvarianceProjections() {
    }

    public static double canvasUnitsPerWorldBlock(
            ICoordinateService coordinates,
            Vec2d origin,
            Vec2d direction) {
        return WorldProjectionMath.canvasUnitsPerWorldBlock(coordinates, origin, direction);
    }

    public static double worldBlocksPerCanvasUnit(ICoordinateService coordinates) {
        return coordinates.projectedDistance(new Vec2d(0, 0), new Vec2d(1, 0));
    }

    /** 自 anchor 沿世界 +X 偏移 worldBlocks 后的画布点（均匀投影）。 */
    public static Vec2d canvasEastOf(Vec2d anchor, double worldBlocks, ICoordinateService coordinates) {
        double canvasOffset = worldBlocks / worldBlocksPerCanvasUnit(coordinates);
        return new Vec2d(anchor.x + canvasOffset, anchor.y);
    }
}
