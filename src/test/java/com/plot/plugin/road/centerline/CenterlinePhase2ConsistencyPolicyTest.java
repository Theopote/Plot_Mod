package com.plot.plugin.road.centerline;

import com.plot.plugin.road.station.RoadStationMirroring;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CenterlinePhase2ConsistencyPolicyTest {

    @Test
    void mirrorVerticalAlignmentInRangeMirrorsOnlySegmentInterval() {
        RoadVerticalAlignment source = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(10.0, 64.0),
            PointOfVerticalIntersection.of(70.0, 72.0),
            PointOfVerticalIntersection.of(95.0, 68.0)));

        RoadVerticalAlignment mirrored =
            RoadStationMirroring.mirrorVerticalAlignmentInRange(source, 50.0, 100.0);

        assertEquals(10.0, mirrored.getPvis().get(0).getStation(), 1e-6);
        assertEquals(80.0, mirrored.getPvis().get(1).getStation(), 1e-6);
        assertEquals(55.0, mirrored.getPvis().get(2).getStation(), 1e-6);
    }

    @Test
    void mirrorVerticalAlignmentReversesWholeRoad() {
        RoadVerticalAlignment source = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(100.0, 72.0)));

        RoadVerticalAlignment mirrored = RoadStationMirroring.mirrorVerticalAlignment(source, 100.0);

        assertNotNull(mirrored);
        assertEquals(100.0, mirrored.getPvis().get(1).getStation(), 1e-6);
        assertEquals(0.0, mirrored.getPvis().get(0).getStation(), 1e-6);
        assertEquals(64.0, mirrored.getPvis().get(1).getElevation(), 1e-6);
        assertEquals(72.0, mirrored.getPvis().get(0).getElevation(), 1e-6);
    }

    @Test
    void mirrorVerticalAlignmentReturnsNullForInvalidStorageOrder() {
        RoadVerticalAlignment source = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(100.0, 70.0),
            PointOfVerticalIntersection.of(60.0, 68.0)));

        assertNull(RoadStationMirroring.mirrorVerticalAlignment(source, 100.0));
    }

    @Test
    void reverseVerticalAlignmentDelegatesToMirroring() {
        RoadVerticalAlignment source = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(100.0, 72.0)));

        RoadVerticalAlignment mirrored = RoadCenterlineEditor.reverseVerticalAlignment(source, 100.0);

        assertNotNull(mirrored);
        assertEquals(100.0, mirrored.getPvis().get(1).getStation(), 1e-6);
        assertEquals(0.0, mirrored.getPvis().get(0).getStation(), 1e-6);
    }
}
