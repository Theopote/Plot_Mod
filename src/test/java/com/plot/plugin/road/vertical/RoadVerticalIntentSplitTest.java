package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.RoadSegmentOrdering;
import com.plot.plugin.road.model.RoadTopologyRoadSplitter;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.plugin.road.station.RoadStationing;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoadVerticalIntentSplitTest {

    private final RoadSystemConfig config = new RoadSystemConfig("test");

    @Test
    void splitFitTerrainRoadPreservesVerticalMode() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoadForAdopt(config);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());

        assertEquals(RoadVerticalMode.FIT_TERRAIN, road.getVerticalMode());

        String splitSegmentId = RoadSegmentOrdering.orderedSegmentIds(network, road).get(1);
        String tailId = network.splitRoadBeforeSegment(road.getId(), splitSegmentId);

        assertNotNull(tailId);
        assertEquals(RoadVerticalMode.FIT_TERRAIN, network.getRoad(road.getId()).getVerticalMode());
        assertEquals(RoadVerticalMode.FIT_TERRAIN, network.getRoad(tailId).getVerticalMode());
    }

    @Test
    void splitFlatRoadPreservesFlatIntent() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("flat-road");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());

        FlatVerticalIntentSupport.enableFlatWithBase(network, road, config, 70.0);
        road.getFlatVerticalIntent().setIntersectionOverride(n3.getId(), 72.0);
        FlatVerticalIntentSupport.syncCompiledAlignment(network, road, road.getEffectiveMaxSlope(config));

        String splitSegmentId = RoadSegmentOrdering.orderedSegmentIds(network, road).get(1);
        String tailId = network.splitRoadBeforeSegment(road.getId(), splitSegmentId);
        Road tail = network.getRoad(tailId);

        assertNotNull(tail);
        assertEquals(RoadVerticalMode.FLAT, tail.getVerticalMode());
        assertNotNull(tail.getFlatVerticalIntent());
        assertEquals(70.0, tail.getFlatVerticalIntent().getBaseElevation(), 1e-6);
        assertEquals(72.0, tail.getFlatVerticalIntent().getIntersectionOverride(n3.getId()), 1e-6);
        assertNull(tail.getFlatVerticalIntent().getIntersectionOverride(n1.getId()));
    }

    @Test
    void splitFitTerrainRoadPreservesTerrainFollowPreset() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoadForAdopt(config);
        road.setTerrainStyle(RoadTerrainStyle.FOLLOW);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());

        String splitSegmentId = RoadSegmentOrdering.orderedSegmentIds(network, road).get(1);
        String tailId = network.splitRoadBeforeSegment(road.getId(), splitSegmentId);

        assertEquals(RoadTerrainStyle.FOLLOW, network.getRoad(road.getId()).getStoredTerrainStyle());
        assertEquals(RoadTerrainStyle.FOLLOW, network.getRoad(tailId).getStoredTerrainStyle());
    }

    @Test
    void topologyRepairPreservesTerrainFollowPreset() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoadForAdopt(config);
        road.setTerrainStyle(RoadTerrainStyle.FOLLOW);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        RoadNode n4 = network.createNode(new Vec2d(110, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n3.getId(), n4.getId(), List.of(new Vec2d(100, 0), new Vec2d(110, 0)), road.getId());

        RoadTopologyRoadSplitter.repairAfterAdopt(network);

        for (Road repaired : network.getRoads().values()) {
            assertEquals(RoadTerrainStyle.FOLLOW, repaired.getStoredTerrainStyle());
        }
    }

    @Test
    void topologyRepairPreservesTerrainAdaptiveMode() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoadForAdopt(config);
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(100, 0));
        RoadNode n4 = network.createNode(new Vec2d(110, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n3.getId(), n4.getId(), List.of(new Vec2d(100, 0), new Vec2d(110, 0)), road.getId());

        RoadTopologyRoadSplitter.repairAfterAdopt(network);

        for (Road repaired : network.getRoads().values()) {
            assertEquals(RoadVerticalMode.FIT_TERRAIN, repaired.getVerticalMode());
        }
    }

    @Test
    void splitFlatRoadPreservesBoundaryOverrideOnBothSides() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("flat-split-boundary");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());

        FlatVerticalIntentSupport.enableFlatWithBase(network, road, config, 70.0);
        road.getFlatVerticalIntent().setIntersectionOverride(n2.getId(), 75.0);
        FlatVerticalIntentSupport.syncCompiledAlignment(network, road, road.getEffectiveMaxSlope(config));

        String splitSegmentId = RoadSegmentOrdering.orderedSegmentIds(network, road).get(1);
        String tailId = network.splitRoadBeforeSegment(road.getId(), splitSegmentId);
        Road head = network.getRoad(road.getId());
        Road tail = network.getRoad(tailId);

        assertNotNull(tail);
        assertEquals(75.0, head.getFlatVerticalIntent().getIntersectionOverride(n2.getId()), 1e-6);
        assertEquals(75.0, tail.getFlatVerticalIntent().getIntersectionOverride(n2.getId()), 1e-6);
    }

    @Test
    void topologyRepairPartitionsFlatOverridesPerComponent() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("fork-overrides");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));
        RoadNode d = network.createNode(new Vec2d(10, 10));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());
        network.createEdge(b.getId(), d.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());

        FlatVerticalIntentSupport.enableFlatWithBase(network, road, config, 68.0);
        road.getFlatVerticalIntent().setIntersectionOverride(c.getId(), 71.0);
        road.getFlatVerticalIntent().setIntersectionOverride(d.getId(), 74.0);
        FlatVerticalIntentSupport.syncCompiledAlignment(network, road, road.getEffectiveMaxSlope(config));

        RoadTopologyRoadSplitter.repairAfterAdopt(network);

        assertEquals(3, network.getRoads().size());

        Road trunkRoad = roadContainingNode(network, a.getId()).orElseThrow();
        Road branchCRoad = roadContainingNode(network, c.getId()).orElseThrow();
        Road branchDRoad = roadContainingNode(network, d.getId()).orElseThrow();

        assertNull(trunkRoad.getFlatVerticalIntent().getIntersectionOverride(c.getId()));
        assertNull(trunkRoad.getFlatVerticalIntent().getIntersectionOverride(d.getId()));

        assertEquals(71.0, branchCRoad.getFlatVerticalIntent().getIntersectionOverride(c.getId()), 1e-6);
        assertNull(branchCRoad.getFlatVerticalIntent().getIntersectionOverride(d.getId()));

        assertEquals(74.0, branchDRoad.getFlatVerticalIntent().getIntersectionOverride(d.getId()), 1e-6);
        assertNull(branchDRoad.getFlatVerticalIntent().getIntersectionOverride(c.getId()));
    }

    @Test
    void topologyRepairPreservesSharedForkOverrideOnAllBranches() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("fork-shared");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));
        RoadNode d = network.createNode(new Vec2d(10, 10));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());
        network.createEdge(b.getId(), d.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());

        FlatVerticalIntentSupport.enableFlatWithBase(network, road, config, 68.0);
        road.getFlatVerticalIntent().setIntersectionOverride(b.getId(), 74.0);
        FlatVerticalIntentSupport.syncCompiledAlignment(network, road, road.getEffectiveMaxSlope(config));

        RoadTopologyRoadSplitter.repairAfterAdopt(network);

        assertEquals(3, network.getRoads().size());
        for (Road repaired : roadsContainingNode(network, b.getId())) {
            assertEquals(74.0, repaired.getFlatVerticalIntent().getIntersectionOverride(b.getId()), 1e-6);
        }
        assertEquals(3, roadsContainingNode(network, b.getId()).size());
    }

    @Test
    void topologyRepairPartitionsFlatOverridesForDisconnectedComponents() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("disconnected-flat");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(100, 0));
        RoadNode d = network.createNode(new Vec2d(110, 0));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(c.getId(), d.getId(), List.of(new Vec2d(100, 0), new Vec2d(110, 0)), road.getId());

        FlatVerticalIntentSupport.enableFlatWithBase(network, road, config, 68.0);
        road.getFlatVerticalIntent().setIntersectionOverride(b.getId(), 71.0);
        road.getFlatVerticalIntent().setIntersectionOverride(d.getId(), 75.0);
        FlatVerticalIntentSupport.syncCompiledAlignment(network, road, road.getEffectiveMaxSlope(config));

        RoadTopologyRoadSplitter.repairAfterAdopt(network);

        assertEquals(2, network.getRoads().size());

        Road roadWithB = roadContainingNode(network, b.getId()).orElseThrow();
        Road roadWithD = roadContainingNode(network, d.getId()).orElseThrow();

        assertEquals(71.0, roadWithB.getFlatVerticalIntent().getIntersectionOverride(b.getId()), 1e-6);
        assertNull(roadWithB.getFlatVerticalIntent().getIntersectionOverride(d.getId()));

        assertEquals(75.0, roadWithD.getFlatVerticalIntent().getIntersectionOverride(d.getId()), 1e-6);
        assertNull(roadWithD.getFlatVerticalIntent().getIntersectionOverride(b.getId()));
    }

    @Test
    void topologyRepairFlatIntentCompilesCorrectProfiles() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("fork-compile");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));
        RoadNode d = network.createNode(new Vec2d(10, 10));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());
        network.createEdge(b.getId(), d.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());

        FlatVerticalIntentSupport.enableFlatWithBase(network, road, config, 68.0);
        road.getFlatVerticalIntent().setIntersectionOverride(c.getId(), 72.0);
        road.getFlatVerticalIntent().setIntersectionOverride(d.getId(), 75.0);
        FlatVerticalIntentSupport.syncCompiledAlignment(network, road, road.getEffectiveMaxSlope(config));

        RoadTopologyRoadSplitter.repairAfterAdopt(network);

        Road branchCRoad = roadContainingNode(network, c.getId()).orElseThrow();
        Road branchDRoad = roadContainingNode(network, d.getId()).orElseThrow();
        double maxGrade = branchCRoad.getEffectiveMaxSlope(config);

        FlatVerticalIntent branchCIntent = branchCRoad.getFlatVerticalIntent();
        FlatVerticalIntent branchDIntent = branchDRoad.getFlatVerticalIntent();
        RoadVerticalAlignment branchCAlignment =
            FlatProfileCompiler.compile(network, branchCRoad, branchCIntent, maxGrade);
        RoadVerticalAlignment branchDAlignment =
            FlatProfileCompiler.compile(network, branchDRoad, branchDIntent, maxGrade);

        double cEndpointStation = endpointStationForNode(network, branchCRoad, c.getId());
        double dEndpointStation = endpointStationForNode(network, branchDRoad, d.getId());

        assertEquals(
            72.0,
            VerticalAlignmentGeometry.elevationAt(branchCAlignment, cEndpointStation).orElse(Double.NaN),
            1e-3);
        assertEquals(
            75.0,
            VerticalAlignmentGeometry.elevationAt(branchDAlignment, dEndpointStation).orElse(Double.NaN),
            1e-3);
    }

    @Test
    void topologyRepairDoesNotConvertFlatToManualProfile() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("fork-flat");
        road.setName("Flat Fork");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));
        RoadNode d = network.createNode(new Vec2d(10, 10));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());
        network.createEdge(b.getId(), d.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());

        FlatVerticalIntentSupport.enableFlatWithBase(network, road, config, 68.0);
        FlatVerticalIntentSupport.syncCompiledAlignment(network, road, road.getEffectiveMaxSlope(config));

        RoadTopologyRoadSplitter.repairAfterAdopt(network);

        assertEquals(3, network.getRoads().size());
        for (Road repaired : network.getRoads().values()) {
            assertEquals(RoadVerticalMode.FLAT, repaired.getVerticalMode());
            assertNotNull(repaired.getFlatVerticalIntent());
            assertEquals(68.0, repaired.getFlatVerticalIntent().getBaseElevation(), 1e-6);
        }
    }

    private static Optional<Road> roadContainingNode(RoadNetwork network, String nodeId) {
        List<Road> roads = roadsContainingNode(network, nodeId);
        return roads.isEmpty() ? Optional.empty() : Optional.of(roads.getFirst());
    }

    private static List<Road> roadsContainingNode(RoadNetwork network, String nodeId) {
        List<Road> matches = new ArrayList<>();
        for (Road candidate : network.getRoads().values()) {
            for (String segmentId : candidate.getOrderedSegmentIds()) {
                RoadEdge edge = network.getEdge(segmentId);
                if (edge != null
                        && (nodeId.equals(edge.getStartNodeId()) || nodeId.equals(edge.getEndNodeId()))) {
                    matches.add(candidate);
                    break;
                }
            }
        }
        return matches;
    }

    private static double endpointStationForNode(RoadNetwork network, Road road, String nodeId) {
        return RoadStationing.orientedSegments(network, road).stream()
            .filter(segment -> nodeId.equals(segment.entryNodeId()) || nodeId.equals(segment.exitNodeId()))
            .mapToDouble(segment -> nodeId.equals(segment.exitNodeId())
                ? segment.endStation()
                : segment.startStation())
            .findFirst()
            .orElseThrow();
    }
}
