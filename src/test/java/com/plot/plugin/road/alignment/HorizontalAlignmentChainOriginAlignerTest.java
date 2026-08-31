package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorizontalAlignmentChainOriginAlignerTest {

    @Test
    void alignToChainStartSnapsOriginAndBearing() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(10, 20));
        RoadNode n2 = network.createNode(new Vec2d(110, 20));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(10, 20), new Vec2d(110, 20)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 0), Math.PI / 2, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(alignment);

        assertTrue(HorizontalAlignmentChainOriginAligner.alignToChainStart(network, road));
        assertEquals(10.0, road.getHorizontalAlignment().getOrigin().x, 1e-6);
        assertEquals(20.0, road.getHorizontalAlignment().getOrigin().y, 1e-6);
        assertEquals(0.0, road.getHorizontalAlignment().getStartBearingRadians(), 1e-6);
    }

    @Test
    void alignToChainStartSkipsWhenCenterlineStartDiffersFromChainNode() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode shared = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            shared.getId(),
            end.getId(),
            List.of(new Vec2d(0, 5), new Vec2d(100, 5)),
            road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 5), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(alignment);

        assertFalse(HorizontalAlignmentChainOriginAligner.alignToChainStart(network, road));
        assertEquals(0.0, road.getHorizontalAlignment().getOrigin().x, 1e-6);
        assertEquals(5.0, road.getHorizontalAlignment().getOrigin().y, 1e-6);
    }

    @Test
    void bearingAtChainStartRespectsReversedSegment() {
        List<Vec2d> points = List.of(new Vec2d(100, 0), new Vec2d(50, 0), new Vec2d(0, 0));

        double forward = HorizontalAlignmentChainOriginAligner
            .bearingAtChainStart(points, true)
            .orElseThrow();
        double reversed = HorizontalAlignmentChainOriginAligner
            .bearingAtChainStart(points, false)
            .orElseThrow();

        assertEquals(Math.PI, forward, 1e-6);
        assertEquals(0.0, reversed, 1e-6);
    }

    @Test
    void materializeAlignsOriginToChainStart() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 5), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(alignment);

        HorizontalAlignmentCenterlineMaterializer.materialize(network, road);

        Vec2d chainOrigin = RoadStationing.chainOrigin(network, road).orElseThrow();
        assertEquals(chainOrigin.x, road.getHorizontalAlignment().getOrigin().x, 1e-6);
        assertEquals(chainOrigin.y, road.getHorizontalAlignment().getOrigin().y, 1e-6);
        assertTrue(HorizontalAlignmentCenterlineConsistency.evaluate(network, road).isConsistent());
    }
}
