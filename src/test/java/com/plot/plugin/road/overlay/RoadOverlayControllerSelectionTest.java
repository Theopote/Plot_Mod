package com.plot.plugin.road.overlay;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadOverlayControllerSelectionTest {

    @Test
    void hitTestEntry_includesShapeCandidates() {
        List<RoadOverlayEntry> entries = List.of(
            entry("road-a", square(0, 0, 10, 10), RoadOverlayState.REGISTERED),
            entry("shape:path-1", square(20, 0, 30, 10), RoadOverlayState.PICK_ACTIVE));

        RoadOverlayEntry hit = RoadOverlayController.hitTestEntry(entries, 25, 5);
        assertNotNull(hit);
        assertEquals("shape:path-1", hit.roadId());
    }

    @Test
    void hitTestRoad_skipsShapeCandidates() {
        List<RoadOverlayEntry> entries = List.of(
            entry("shape:path-1", square(0, 0, 10, 10), RoadOverlayState.PICK_ACTIVE));

        assertNull(RoadOverlayController.hitTestRoad(entries, 5, 5));
        assertNotNull(RoadOverlayController.hitTestEntry(entries, 5, 5));
    }

    @Test
    void collectRoadsInBox_usesWindowAndCrossingModes() {
        List<RoadOverlayEntry> entries = List.of(
            entry("inside", square(2, 2, 4, 4), RoadOverlayState.REGISTERED),
            entry("partial", square(8, 2, 12, 4), RoadOverlayState.REGISTERED));

        LinkedHashSet<String> window = RoadOverlayController.collectRoadsInBox(
            entries, 0, 0, 10, 10, true);
        LinkedHashSet<String> crossing = RoadOverlayController.collectRoadsInBox(
            entries, 0, 0, 10, 10, false);

        assertEquals(1, window.size());
        assertTrue(window.contains("inside"));
        assertEquals(2, crossing.size());
        assertTrue(crossing.contains("inside"));
        assertTrue(crossing.contains("partial"));
    }

    @Test
    void collectShapeIdsInBox_returnsShapeIdsWithoutPrefix() {
        List<RoadOverlayEntry> entries = List.of(
            entry("shape:path-a", square(1, 1, 3, 3), RoadOverlayState.PICK_ACTIVE),
            entry("road-a", square(20, 20, 30, 30), RoadOverlayState.REGISTERED));

        LinkedHashSet<String> shapeIds = RoadOverlayController.collectShapeIdsInBox(
            entries, 0, 0, 10, 10, true);

        assertEquals(1, shapeIds.size());
        assertTrue(shapeIds.contains("path-a"));
    }

    private static RoadOverlayEntry entry(
            String id,
            List<Vec2d> corridor,
            RoadOverlayState state) {
        return new RoadOverlayEntry(id, id, corridor, corridor, state);
    }

    private static List<Vec2d> square(double minX, double minY, double maxX, double maxY) {
        return List.of(
            new Vec2d(minX, minY),
            new Vec2d(maxX, minY),
            new Vec2d(maxX, maxY),
            new Vec2d(minX, maxY));
    }
}
