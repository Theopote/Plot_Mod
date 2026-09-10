package com.plot.api.world;

import com.plot.api.geometry.Vec2d;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class WorldProjectionSnapshotTest {

    @Test
    void frozenSnapshotIgnoresLiveCoordinateChanges() {
        WorldProjectionSnapshot snapshot = IdentityCoordinateService.INSTANCE.captureProjection();
        Vec2d worldAtCapture = snapshot.toWorld(new Vec2d(100, 0));

        MutableCoordinateService live = new MutableCoordinateService(snapshot);
        live.scale = 5.0;

        assertEquals(worldAtCapture.x, snapshot.toWorld(new Vec2d(100, 0)).x, 1e-6);
        assertNotEquals(
            snapshot.toWorld(new Vec2d(100, 0)).x,
            live.canvasToMinecraftWorld(new Vec2d(100, 0)).x,
            1e-6);
    }

    @Test
    void pluginProjectionContextUsesFrozenSnapshotOnly() {
        MutableCoordinateService live = new MutableCoordinateService(
            IdentityCoordinateService.INSTANCE.captureProjection());
        PluginProjectionContext context = PluginProjectionContext.capture(live);
        double frozenLength = context.pathLength(List.of(new Vec2d(0, 0), new Vec2d(40, 0)));

        live.scale = 10.0;
        assertEquals(frozenLength, context.pathLength(List.of(new Vec2d(0, 0), new Vec2d(40, 0))), 1e-6);
    }

    private static final class MutableCoordinateService implements ICoordinateService {
        private final WorldProjectionSnapshot base;
        private double scale = 1.0;

        private MutableCoordinateService(WorldProjectionSnapshot base) {
            this.base = base;
        }

        @Override
        public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
            Vec2d frozen = base.toWorld(canvasPos);
            return new Vec2d(frozen.x * scale, frozen.y * scale);
        }

        @Override
        public WorldViewBounds getMinecraftWorldViewBounds() {
            return base.worldBounds();
        }

        @Override
        public WorldProjectionSnapshot captureProjection() {
            return base;
        }
    }
}
