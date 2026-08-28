package com.plot.plugin.road.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.IntersectionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkManagerAdoptRepairTest {
    private RoadNetworkManager manager;

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("test"), new RoadProjectStatus());
    }

    @Test
    void repairPendingInitiallyFalse() {
        assertFalse(manager.isAdoptIntersectionRepairPending());
    }

    @Test
    void adoptClearsPreviousRepairPendingOnSuccess() throws Exception {
        setRepairPending(true);
        manager.adoptSelectedPaths(List.of(new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)), false)));
        assertFalse(manager.isAdoptIntersectionRepairPending());
    }

    @Test
    void reconcileCompleteClearsRepairPending() throws Exception {
        setRepairPending(true);
        IntersectionResult result = manager.reconcileIntersections();
        assertEquals(IntersectionResult.COMPLETE, result);
        assertFalse(manager.isAdoptIntersectionRepairPending());
    }

    private void setRepairPending(boolean pending) throws Exception {
        Field field = RoadNetworkManager.class.getDeclaredField("adoptIntersectionRepairPending");
        field.setAccessible(true);
        field.setBoolean(manager, pending);
    }
}
