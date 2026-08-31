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

class CenterlineHorizontalAlignmentSyncTest {

    @Test
    void insertPiRefitsStraightAlignment() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 0), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(alignment);

        assertTrue(RoadCenterlineEditor.insertPiAtLocalDistance(network, edge.getId(), 40.0).isSuccess());

        assertNotNull(road.getHorizontalAlignment());
        assertEquals(1, road.getHorizontalAlignment().getElements().size());
        assertEquals(100.0, road.getHorizontalAlignment().totalLength(), 0.5);
        assertTrue(HorizontalAlignmentCenterlineConsistency.evaluate(network, road).isConsistent());
    }

    @Test
    void filletRefitsCornerAlignment() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 50));
        RoadEdge edge = network.createEdge(
            n1.getId(),
            n2.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(50, 0), new Vec2d(50, 50)),
            road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 0), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(50.0));
        alignment.addElement(HorizontalAlignmentElement.tangent(50.0));
        road.setHorizontalAlignment(alignment);

        assertTrue(RoadCenterlineEditor.filletVertex(network, edge.getId(), 1, 5.0).isSuccess());

        assertNotNull(road.getHorizontalAlignment());
        assertTrue(road.getHorizontalAlignment().getElements().size() >= 2);
    }

    @Test
    void syncLeavesRoadWithoutAlignmentUntouched() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        RoadEdge edge = network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadCenterlineEditor.insertPiAtLocalDistance(network, edge.getId(), 40.0);

        assertNull(road.getHorizontalAlignment());
        assertEquals(CenterlineHorizontalAlignmentSync.Outcome.UNCHANGED,
            CenterlineHorizontalAlignmentSync.syncAfterCenterlineEdit(network, road));
    }
}
