package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadNetworkBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadNetworkTest {

    @Test
    void adoptSinglePolylineCreatesTwoNodesAndOneEdge() {
        RoadNetwork network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        RoadSystemConfig config = new RoadSystemConfig("road_system");

        PolylineShape shape = new PolylineShape(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(20, 0)
        ), false);

        builder.adoptShape(network, shape, config);

        assertEquals(2, network.getNodes().size());
        assertEquals(1, network.getEdges().size());
    }

    @Test
    void intersectionSplitIsIdempotent() {
        RoadNetwork network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        RoadSystemConfig config = new RoadSystemConfig("road_system");

        PolylineShape a = new PolylineShape(List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false);
        PolylineShape b = new PolylineShape(List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false);

        builder.adoptShape(network, a, config);
        builder.adoptShape(network, b, config);

        int edgesAfterFirstSplit = network.getEdges().size();
        int nodesAfterFirstSplit = network.getNodes().size();

        builder.detectAndSplitIntersections(network);
        int edgesAfterSecondSplit = network.getEdges().size();
        int nodesAfterSecondSplit = network.getNodes().size();

        assertEquals(4, edgesAfterFirstSplit);
        assertEquals(5, nodesAfterFirstSplit);
        assertEquals(edgesAfterFirstSplit, edgesAfterSecondSplit);
        assertEquals(nodesAfterFirstSplit, nodesAfterSecondSplit);
    }

    @Test
    void jsonRoundTripPreservesData() {
        RoadNetwork network = new RoadNetwork();
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(10, 0));
        RoadEdge edge = network.createEdge(start.getId(), end.getId(), List.of(
            new Vec2d(0, 0), new Vec2d(10, 0)
        ));
        edge.setWidth(7);
        edge.setMaxSlope(5.0f);

        RoadNetwork restored = RoadNetwork.fromJson(network.toJson());
        RoadEdge restoredEdge = restored.getEdges().values().iterator().next();

        assertEquals(2, restored.getNodes().size());
        assertEquals(1, restored.getEdges().size());
        assertEquals(7, restoredEdge.getWidth());
        assertEquals(5.0f, restoredEdge.getMaxSlope());
    }

    @Test
    void classifyJunctionTypes() {
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        RoadNetwork network = new RoadNetwork();
        RoadNode t = network.createNode(new Vec2d(0, 0));
        t.addEdge("e1");
        t.addEdge("e2");
        t.addEdge("e3");

        assertEquals(RoadNetworkBuilder.JunctionType.T_JUNCTION, builder.classify(t));

        t.addEdge("e4");
        assertEquals(RoadNetworkBuilder.JunctionType.CROSSROAD, builder.classify(t));
    }

    @Test
    void splitSlopeOverridesRemapsMileage() {
        List<RoadEdge.SlopeOverride> overrides = List.of(
            new RoadEdge.SlopeOverride(10, 20, 3.0f)
        );

        List<RoadEdge.SlopeOverride> first = RoadNetworkBuilder.splitSlopeOverrides(overrides, 15, 30, true);
        assertEquals(1, first.size());
        assertEquals(10, first.getFirst().startDistance, 1e-6);
        assertEquals(15, first.getFirst().endDistance, 1e-6);
        assertEquals(3.0f, first.getFirst().maxSlope);

        List<RoadEdge.SlopeOverride> second = RoadNetworkBuilder.splitSlopeOverrides(overrides, 15, 30, false);
        assertEquals(1, second.size());
        assertEquals(0, second.getFirst().startDistance, 1e-6);
        assertEquals(5, second.getFirst().endDistance, 1e-6);
        assertEquals(3.0f, second.getFirst().maxSlope);
    }

    @Test
    void splitSlopeOverridesDropsOutOfRangeSegments() {
        List<RoadEdge.SlopeOverride> overrides = List.of(
            new RoadEdge.SlopeOverride(0, 5, 2.0f),
            new RoadEdge.SlopeOverride(25, 30, 4.0f)
        );

        List<RoadEdge.SlopeOverride> first = RoadNetworkBuilder.splitSlopeOverrides(overrides, 15, 30, true);
        assertEquals(1, first.size());
        assertEquals(0, first.getFirst().startDistance, 1e-6);
        assertEquals(5, first.getFirst().endDistance, 1e-6);

        List<RoadEdge.SlopeOverride> second = RoadNetworkBuilder.splitSlopeOverrides(overrides, 15, 30, false);
        assertEquals(1, second.size());
        assertEquals(10, second.getFirst().startDistance, 1e-6);
        assertEquals(15, second.getFirst().endDistance, 1e-6);
    }
}
