package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationFormat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalAlignmentGeometryTest {

    @Test
    void linearGradeBetweenTwoPvis() {
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 100.0),
            PointOfVerticalIntersection.of(200.0, 110.0)
        ));

        assertEquals(105.0, VerticalAlignmentGeometry.elevationAt(alignment, 100.0).orElseThrow(), 0.01);
        assertEquals(5.0, VerticalAlignmentGeometry.gradeAt(alignment, 50.0).orElseThrow(), 0.01);
    }

    @Test
    void crestCurveAtMiddlePvi() {
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 100.0),
            PointOfVerticalIntersection.withCurve(100.0, 110.0, 40.0),
            PointOfVerticalIntersection.of(200.0, 105.0)
        ));

        assertEquals(109.25, VerticalAlignmentGeometry.elevationAt(alignment, 100.0).orElseThrow(), 0.05);
        assertEquals(105.0, VerticalAlignmentGeometry.elevationAt(alignment, 200.0).orElseThrow(), 0.05);
        assertEquals(VerticalCurveType.CREST, VerticalAlignmentGeometry.curveTypeAtPvi(alignment.sortedPvis(), 1));

        double incoming = VerticalAlignmentGeometry.tangentGradePercent(
            alignment.sortedPvis().get(0),
            alignment.sortedPvis().get(1));
        double outgoing = VerticalAlignmentGeometry.tangentGradePercent(
            alignment.sortedPvis().get(1),
            alignment.sortedPvis().get(2));
        assertEquals(40.0 / 15.0, VerticalAlignmentGeometry.kValue(40.0, incoming, outgoing), 0.1);
    }

    @Test
    void sagCurveDetected() {
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 100.0),
            PointOfVerticalIntersection.withCurve(100.0, 90.0, 30.0),
            PointOfVerticalIntersection.of(200.0, 100.0)
        ));

        assertEquals(VerticalCurveType.SAG, VerticalAlignmentGeometry.curveTypeAtPvi(alignment.sortedPvis(), 1));
    }

    @Test
    void curveLengthFromK() {
        assertEquals(60.0, VerticalAlignmentGeometry.curveLengthFromK(20.0, 5.0, 2.0), 0.01);
    }

    @Test
    void samplesAlongAlignment() {
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 100.0),
            PointOfVerticalIntersection.of(100.0, 110.0)
        ));

        List<VerticalAlignmentGeometry.ProfileSample> samples =
            VerticalAlignmentGeometry.sample(alignment, 25.0);
        assertTrue(samples.size() >= 5);
    }

    @Test
    void persistenceRoundTrip() {
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 50.0),
            PointOfVerticalIntersection.withCurve(80.0, 62.0, 24.0)
                .withConstraint(VerticalControlPointConstraint.USER_LOCKED),
            PointOfVerticalIntersection.of(160.0, 58.0)
        ));

        RoadVerticalAlignment restored = VerticalAlignmentPersistence.fromData(
            VerticalAlignmentPersistence.toData(alignment));

        assertNotNull(restored);
        assertEquals(3, restored.pviCount());
        assertEquals(
            alignment.sortedPvis().get(1).getCurveLength(),
            restored.sortedPvis().get(1).getCurveLength(),
            1e-6);
        assertEquals(VerticalControlPointConstraint.USER_LOCKED,
            restored.sortedPvis().get(1).getConstraint());
    }

    @Test
    void jsonRoundTripThroughNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("profiled");
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.withCurve(50.0, 70.0, 20.0),
            PointOfVerticalIntersection.of(100.0, 68.0)
        ));
        road.setVerticalAlignment(alignment);

        RoadNetwork restored = RoadNetwork.parseSnapshot(network.toJson());
        Road restoredRoad = restored.getRoad("profiled");

        assertNotNull(restoredRoad.getVerticalAlignment());
        assertEquals(3, restoredRoad.getVerticalAlignment().pviCount());
    }

    @Test
    void describeCurveIncludesKValue() {
        List<PointOfVerticalIntersection> pvis = List.of(
            PointOfVerticalIntersection.of(0.0, 100.0),
            PointOfVerticalIntersection.withCurve(100.0, 110.0, 40.0),
            PointOfVerticalIntersection.of(200.0, 105.0)
        );
        String label = VerticalAlignmentGeometry.describeCurveAtPvi(pvis, 1, RoadStationFormat.KILOMETER_PLUS);
        assertTrue(label.contains("K="));
        assertTrue(label.contains("Crest"));
    }
}
