package com.plot.core.geometry;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.SnapshotCoordinateService;
import com.plot.api.world.WorldProjectionUnavailableException;
import com.plot.api.world.WorldViewBounds;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldProjectionMathTest {

    @Test
    void canvasUnitsPerWorldBlockIsIdentityForOneToOneProjection() {
        Vec2d origin = new Vec2d(10, 20);
        Vec2d lateral = new Vec2d(0, 1);
        double units = WorldProjectionMath.canvasUnitsPerWorldBlock(
            IdentityCoordinateService.INSTANCE, origin, lateral);
        assertEquals(1.0, units, 1e-6);
    }

    @Test
    void canvasUnitsPerWorldBlockScalesWithUniformProjection() {
        Vec2d origin = new Vec2d(10, 20);
        Vec2d lateral = new Vec2d(0, 1);
        double units = WorldProjectionMath.canvasUnitsPerWorldBlock(
            SnapshotCoordinateService.uniformScale(4.0), origin, lateral);
        assertEquals(0.25, units, 1e-6);
    }

    @Test
    void canvasUnitsPerWorldBlockRequiresCoordinates() {
        Vec2d origin = new Vec2d(0, 0);
        assertThrows(NullPointerException.class, () ->
            WorldProjectionMath.canvasUnitsPerWorldBlock(null, origin, new Vec2d(1, 0)));
    }

    @Test
    void canvasUnitsPerWorldBlockFailsWhenProjectionIsUnavailable() {
        ICoordinateService broken = new ICoordinateService() {
            @Override
            public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
                return null;
            }

            @Override
            public WorldViewBounds getMinecraftWorldViewBounds() {
                return null;
            }
        };
        assertThrows(WorldProjectionUnavailableException.class, () ->
            WorldProjectionMath.canvasUnitsPerWorldBlock(
                broken, new Vec2d(0, 0), new Vec2d(1, 0)));
    }
}
