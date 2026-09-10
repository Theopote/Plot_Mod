package com.plot.core.geometry;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldCoordinateUtilsTest {

    @Test
    void canvasToBlockXZRoundsWithoutTransformer() {
        BlockPos pos = WorldCoordinateUtils.canvasToBlockXZ(new Vec2d(12.7, -3.2), null);
        assertEquals(13, pos.getX());
        assertEquals(-3, pos.getZ());
    }

    @Test
    void leftNormalMatchesUnitPerpendicular() {
        Vec2d normal = WorldCoordinateUtils.leftNormal(new Vec2d(1, 0));
        assertEquals(0.0, normal.x, 1e-9);
        assertEquals(1.0, normal.y, 1e-9);
    }

    @Test
    void canvasToBlockXZUsesCoordinateService() {
        ICoordinateService transformer = new ICoordinateService() {
            @Override
            public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
                return new Vec2d(canvasPos.x + 0.4, canvasPos.y + 0.6);
            }

            @Override
            public com.plot.api.world.WorldViewBounds getMinecraftWorldViewBounds() {
                return null;
            }
        };
        BlockPos pos = WorldCoordinateUtils.canvasToBlockXZ(new Vec2d(10, 20), transformer);
        assertEquals(10, pos.getX());
        assertEquals(21, pos.getZ());
    }
}
