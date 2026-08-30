package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationFormat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorizontalAlignmentGeometryTest {

    @Test
    void tangentAdvancesAlongBearing() {
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment();
        alignment.setOrigin(new Vec2d(0, 0));
        alignment.setStartBearingRadians(0.0);
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));

        AlignmentPose end = HorizontalAlignmentGeometry.poseAt(alignment, 100.0).orElseThrow();
        assertEquals(100.0, end.x(), 0.5);
        assertEquals(0.0, end.y(), 0.5);
    }

    @Test
    void tangentArcTangentProducesQuarterTurn() {
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment();
        alignment.setOrigin(new Vec2d(0, 0));
        alignment.setStartBearingRadians(0.0);
        alignment.addElement(HorizontalAlignmentElement.tangent(50.0));
        double arcLength = Math.PI * 25.0 / 2.0;
        alignment.addElement(HorizontalAlignmentElement.circularArc(arcLength, 25.0, TurnDirection.LEFT));
        alignment.addElement(HorizontalAlignmentElement.tangent(50.0));

        AlignmentPose afterArc = HorizontalAlignmentGeometry.poseAt(alignment, 50.0 + arcLength).orElseThrow();
        assertEquals(75.0, afterArc.x(), 1.0);
        assertEquals(25.0, afterArc.y(), 1.0);

        AlignmentPose end = HorizontalAlignmentGeometry.poseAt(alignment, alignment.totalLength()).orElseThrow();
        assertEquals(75.0, end.x(), 1.5);
        assertEquals(75.0, end.y(), 1.5);
    }

    @Test
    void spiralIncreasesCurvature() {
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment();
        alignment.setStartBearingRadians(0.0);
        alignment.addElement(HorizontalAlignmentElement.spiral(30.0, 10.0));

        AlignmentPose end = HorizontalAlignmentGeometry.poseAt(alignment, 30.0).orElseThrow();
        assertEquals(0.3, end.curvature(), 0.05);
    }

    @Test
    void samplesPointsAlongAlignment() {
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment();
        alignment.addElement(HorizontalAlignmentElement.tangent(20.0));

        List<Vec2d> points = HorizontalAlignmentGeometry.sample(alignment, 10.0);
        assertTrue(points.size() >= 3);
    }

    @Test
    void persistenceRoundTrip() {
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(5, 10), Math.PI / 2, List.of(
            HorizontalAlignmentElement.tangent(40.0),
            HorizontalAlignmentElement.circularArc(30.0, 60.0, TurnDirection.RIGHT),
            HorizontalAlignmentElement.spiral(20.0, 15.0)
        ));

        RoadHorizontalAlignment restored = HorizontalAlignmentPersistence.fromData(
            HorizontalAlignmentPersistence.toData(alignment));

        assertNotNull(restored);
        assertEquals(3, restored.getElements().size());
        assertEquals(alignment.totalLength(), restored.totalLength(), 1e-6);
    }

    @Test
    void jsonRoundTripThroughNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("aligned");
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment();
        alignment.setOrigin(new Vec2d(0, 0));
        alignment.setStartBearingRadians(0.0);
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(alignment);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadNetwork restored = RoadNetwork.parseSnapshot(network.toJson());
        Road restoredRoad = restored.getRoad("aligned");

        assertNotNull(restoredRoad.getHorizontalAlignment());
        assertEquals(1, restoredRoad.getHorizontalAlignment().getElements().size());
    }

    @Test
    void describeElementIncludesStationRange() {
        HorizontalAlignmentElement arc = HorizontalAlignmentElement.circularArc(50.0, 100.0, TurnDirection.LEFT);
        String label = HorizontalAlignmentGeometry.describeElement(arc, 100.0, RoadStationFormat.KILOMETER_PLUS);
        assertTrue(label.contains("K0+100"));
        assertTrue(label.contains("R=100"));
    }
}
