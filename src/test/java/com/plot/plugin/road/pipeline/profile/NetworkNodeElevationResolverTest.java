package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkNodeElevationResolverTest {

    @Test
    void internalJunctionUsesDesignProfileElevation() {
        RoadNetwork network = buildTwoSegmentRoad();
        NetworkNodeElevationResolver resolver = createResolver();

        Map<String, Integer> elevations = resolver.resolve(network, new FlatTerrainSampler(64), null);

        RoadNode middle = network.getNodes().values().stream()
            .filter(node -> node.getConnectedEdgeIds().size() == 2)
            .findFirst()
            .orElseThrow();
        assertEquals(90, elevations.get(middle.getId()));
    }

    @Test
    void endpointHeightMatchesVerticalAlignmentAtNode() {
        RoadNetwork network = buildTwoSegmentRoad();
        RoadEdge firstEdge = network.getEdge(
            RoadStationing.orderedSegments(network, network.getRoad("design")).getFirst());
        RoadNode start = network.getNode(firstEdge.getStartNodeId());

        assertTrue(VerticalAlignmentEndpointHeight.atNode(network, firstEdge, start).isPresent());
        assertEquals(80, VerticalAlignmentEndpointHeight.atNode(network, firstEdge, start).getAsInt());
    }

    @Test
    void endpointHeightRespectsSegmentChainDirection() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("design");
        road.setVerticalAlignment(new RoadVerticalAlignment(java.util.List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.of(100.0, 100.0)
        )));
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), java.util.List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        RoadEdge reversedTail = network.createEdge(
            n3.getId(), n2.getId(), java.util.List.of(new Vec2d(100, 0), new Vec2d(50, 0)), road.getId());

        assertFalse(RoadStationing.segmentFlowsWithGeometry(network, road, reversedTail.getId()));
        RoadNode junction = network.getNode(n2.getId());
        assertEquals(90, VerticalAlignmentEndpointHeight.atNode(network, reversedTail, junction).getAsInt());
    }

    private static NetworkNodeElevationResolver createResolver() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        RoadGeneratorProfileContext profileContext =
            new RoadGeneratorProfileContext(config, (pathPoints, segments) -> 1.0);
        GradeSeparationPolicy gradeSeparation = new GradeSeparationPolicy(profileContext);
        NodeTargetHeightResolver nodeTargetHeights =
            new NodeTargetHeightResolver(profileContext, gradeSeparation);
        return new NetworkNodeElevationResolver(profileContext, gradeSeparation, nodeTargetHeights);
    }

    private static RoadNetwork buildTwoSegmentRoad() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("design");
        road.setVerticalAlignment(new RoadVerticalAlignment(java.util.List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.of(100.0, 100.0)
        )));
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(50, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), java.util.List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());
        network.createEdge(
            n2.getId(), n3.getId(), java.util.List.of(new Vec2d(50, 0), new Vec2d(100, 0)), road.getId());
        return network;
    }
}
