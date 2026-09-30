package com.plot.plugin.road.vertical;

import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerticalProfileEndpointDragTest {

    @Test
    void startEndpointDragKeepsStationZero() {
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(100.0, 68.0),
            PointOfVerticalIntersection.of(300.0, 70.0)
        ));
        RoadVerticalAlignment moved = VerticalProfileControlPoints.move(
            alignment, 0, 100.0, 72.0, 300.0);
        assertEquals(0.0, moved.getPvis().getFirst().getStation(), 1e-6);
        assertEquals(72.0, moved.getPvis().getFirst().getElevation(), 1e-6);
    }

    @Test
    void endEndpointDragKeepsRoadLength() {
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(100.0, 68.0),
            PointOfVerticalIntersection.of(300.0, 70.0)
        ));
        RoadVerticalAlignment moved = VerticalProfileControlPoints.move(
            alignment, 2, 50.0, 75.0, 300.0);
        assertEquals(300.0, moved.getPvis().getLast().getStation(), 1e-6);
        assertEquals(75.0, moved.getPvis().getLast().getElevation(), 1e-6);
    }
}
