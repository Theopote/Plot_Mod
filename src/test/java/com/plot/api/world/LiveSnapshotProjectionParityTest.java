package com.plot.api.world;

import com.plot.api.geometry.Vec2d;
import com.plot.test.world.IdentityCoordinateService;
import com.plot.test.world.OrthographicProjectionTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 捕获瞬间 live 投影与冻结 snapshot 必须一致（Core 回归保护）。
 */
class LiveSnapshotProjectionParityTest {

    private static final double TOLERANCE_BLOCKS = 0.01;

    @Test
    void snapshotMatchesLiveAtCaptureForOrthographicProjection() {
        ICoordinateService live = OrthographicProjectionTestFixtures.liveOrthographic();
        assertParityAtCapture(live, sampleCanvasPoints());
    }

    @Test
    void snapshotMatchesLiveAtCaptureForIdentityProjection() {
        ICoordinateService live = IdentityCoordinateService.INSTANCE;
        assertParityAtCapture(live, List.of(
            new Vec2d(0, 0),
            new Vec2d(100, 0),
            new Vec2d(50, 50),
            new Vec2d(0, 100),
            new Vec2d(99.5, 33.3)));
    }

    @Test
    void pluginProjectionContextUsesSnapshotCoordinatesAtCapture() {
        ICoordinateService live = OrthographicProjectionTestFixtures.liveOrthographic();
        PluginProjectionContext context = PluginProjectionContext.capture(live);
        WorldProjectionSnapshot snapshot = live.captureProjection();

        for (Vec2d canvas : sampleCanvasPoints()) {
            Vec2d fromContext = context.coordinates().canvasToMinecraftWorld(canvas);
            Vec2d fromSnapshot = snapshot.toWorld(canvas);
            assertWithinTolerance(fromSnapshot, fromContext);
        }
    }

    private static void assertParityAtCapture(ICoordinateService live, List<Vec2d> canvasPoints) {
        WorldProjectionSnapshot snapshot = live.captureProjection();
        for (Vec2d canvas : canvasPoints) {
            Vec2d liveWorld = live.canvasToMinecraftWorld(canvas);
            Vec2d frozenWorld = snapshot.toWorld(canvas);
            assertWithinTolerance(frozenWorld, liveWorld);
        }
    }

    private static List<Vec2d> sampleCanvasPoints() {
        float w = OrthographicProjectionTestFixtures.CANVAS_WIDTH;
        float h = OrthographicProjectionTestFixtures.CANVAS_HEIGHT;
        return List.of(
            new Vec2d(0, 0),
            new Vec2d(w, 0),
            new Vec2d(w / 2f, h / 2f),
            new Vec2d(0, h),
            new Vec2d(w, h),
            new Vec2d(123.4, 456.7));
    }

    private static void assertWithinTolerance(Vec2d expected, Vec2d actual) {
        assertEquals(expected.x, actual.x, TOLERANCE_BLOCKS, "world X");
        assertEquals(expected.y, actual.y, TOLERANCE_BLOCKS, "world Z");
    }
}
