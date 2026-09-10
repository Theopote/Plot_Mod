package com.plot.test.world;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.api.world.WorldViewBounds;

import java.util.function.UnaryOperator;

/** 测试用坐标服务工厂。 */
public final class CoordinateTestFixtures {
    private CoordinateTestFixtures() {
    }

    public static ICoordinateService canvasToWorld(UnaryOperator<Vec2d> mapper) {
        return new ICoordinateService() {
            @Override
            public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
                if (canvasPos == null) {
                    return new Vec2d(0, 0);
                }
                return mapper.apply(canvasPos.copy());
            }

            @Override
            public WorldViewBounds getMinecraftWorldViewBounds() {
                return IdentityCoordinateService.INSTANCE.getMinecraftWorldViewBounds();
            }

            @Override
            public WorldProjectionSnapshot captureProjection() {
                return IdentityCoordinateService.INSTANCE.captureProjection();
            }
        };
    }
}
