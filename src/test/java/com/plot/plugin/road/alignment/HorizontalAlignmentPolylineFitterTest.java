package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorizontalAlignmentPolylineFitterTest {

    @Test
    void fitsStraightChainAsSingleTangent() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());

        RoadHorizontalAlignment fitted = HorizontalAlignmentPolylineFitter.fit(network, road).orElseThrow();

        assertEquals(1, fitted.getElements().size());
        assertEquals(HorizontalAlignmentElementType.TANGENT, fitted.getElements().getFirst().getType());
        assertEquals(100.0, fitted.totalLength(), 0.5);
    }

    @Test
    void fitsRightAngleWithTangentArcChain() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 50));
        network.createEdge(
            n1.getId(),
            n2.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(50, 0), new Vec2d(50, 50)),
            road.getId());

        RoadHorizontalAlignment fitted = HorizontalAlignmentPolylineFitter.fit(network, road).orElseThrow();

        assertTrue(fitted.getElements().size() >= 3);
        assertEquals(HorizontalAlignmentElementType.TANGENT, fitted.getElements().get(0).getType());
        assertEquals(HorizontalAlignmentElementType.CIRCULAR_ARC, fitted.getElements().get(1).getType());
        assertEquals(HorizontalAlignmentElementType.TANGENT, fitted.getElements().get(2).getType());
    }
}
