package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoadJunctionOverlayControllerTest {

    @Test
    void hitTestReturnsClosestJunctionWithinRadius() {
        List<RoadJunctionOverlayEntry> entries = List.of(
            new RoadJunctionOverlayEntry("node-a", new Vec2d(0, 0), RoadJunctionOverlayKind.AT_GRADE, false),
            new RoadJunctionOverlayEntry("node-b", new Vec2d(10, 0), RoadJunctionOverlayKind.GRADE_SEPARATED, false));

        assertEquals("node-a", RoadJunctionOverlayController.hitTest(entries, 0.5, 0.0, 2.0));
        assertEquals("node-b", RoadJunctionOverlayController.hitTest(entries, 9.5, 0.0, 2.0));
        assertNull(RoadJunctionOverlayController.hitTest(entries, 5.0, 0.0, 1.0));
    }
}
