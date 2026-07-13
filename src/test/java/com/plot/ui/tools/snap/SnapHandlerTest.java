package com.plot.ui.tools.snap;

import com.plot.api.geometry.Vec2d;
import com.plot.api.snap.ISnapManager;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.canvas.CanvasCamera;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapHandlerTest {

    private CanvasCamera camera;
    private RecordingSnapManager snapManager;
    private SnapHandler handler;

    @BeforeEach
    void setUp() {
        camera = new CanvasCamera();
        snapManager = new RecordingSnapManager();
        handler = new SnapHandler(AppState.getInstance(), snapManager, "test-tool");
    }

    @Test
    void disabledSnapReturnsCameraWorldPoint() {
        handler.setSnapEnabled(false);
        Vec2d screenPoint = new Vec2d(12, 8);

        Vec2d result = handler.getSnappedWorldPoint(screenPoint, camera);

        assertEquals(camera.screenToWorld(screenPoint).x, result.x, 1e-6);
        assertEquals(camera.screenToWorld(screenPoint).y, result.y, 1e-6);
        assertEquals(0, snapManager.snapCallCount);
    }

    @Test
    void enabledSnapDelegatesToSnapManager() {
        handler.setSnapEnabled(true);
        snapManager.snappedPoint = new Vec2d(100, 50);
        Vec2d screenPoint = new Vec2d(20, 15);

        Vec2d result = handler.getSnappedWorldPoint(screenPoint, camera);

        assertEquals(100.0, result.x, 1e-6);
        assertEquals(50.0, result.y, 1e-6);
        assertEquals(1, snapManager.snapCallCount);
        assertEquals(camera.screenToWorld(screenPoint).x, snapManager.lastWorldPoint.x, 1e-6);
        assertEquals(camera.screenToWorld(screenPoint).y, snapManager.lastWorldPoint.y, 1e-6);
    }

    @Test
    void clearCacheResetsHandlerState() {
        handler.setSnapEnabled(true);
        handler.clearCache();

        assertTrue(handler.isSnapEnabled());
    }

    private static final class RecordingSnapManager implements ISnapManager {
        private Vec2d snappedPoint = new Vec2d(0, 0);
        private Vec2d lastWorldPoint;
        private int snapCallCount;

        @Override
        public Vec2d snapPoint(Vec2d point, List<Shape> snapTargets) {
            lastWorldPoint = point;
            snapCallCount++;
            return snappedPoint;
        }

        @Override
        public Vec2d snapPoint(Vec2d point, Vec2d startPoint, List<Shape> snapTargets) {
            return snapPoint(point, snapTargets);
        }

        @Override
        public boolean isSnapEnabled() {
            return true;
        }

        @Override
        public void setSnapEnabled(boolean enabled) {
        }

        @Override
        public double getSnapDistance() {
            return 10;
        }

        @Override
        public void setSnapDistance(double distance) {
        }

        @Override
        public boolean isGridSnapEnabled() {
            return false;
        }

        @Override
        public boolean isObjectSnapEnabled() {
            return true;
        }

        @Override
        public String getConfigInfo() {
            return "recording";
        }

        @Override
        public void reset() {
        }

        @Override
        public void dispose() {
        }
    }
}
