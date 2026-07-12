package com.plot.plugin.road.model;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.RoadCrossSection;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.style.RoadStyle;
import com.plot.plugin.road.style.RoadStyleCatalog;
import com.plot.core.geometry.shapes.PolylineShape;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        RoadNetworkBuilder.AdoptResult result = builder.adoptShape(network, shape, config);

        assertEquals(2, network.getNodes().size());
        assertEquals(1, network.getEdges().size());
        assertEquals(1, network.getRoads().size());
        assertEquals(1, result.edges().size());
        assertEquals(0, result.junctionCount());
    }

    @Test
    void adoptCrossingRoadReturnsProducedSegments() {
        RoadNetwork network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        RoadSystemConfig config = new RoadSystemConfig("road_system");

        PolylineShape a = new PolylineShape(List.of(new Vec2d(0, 5), new Vec2d(10, 5)), false);
        PolylineShape b = new PolylineShape(List.of(new Vec2d(5, 0), new Vec2d(5, 10)), false);

        builder.adoptShape(network, a, config);
        RoadNetworkBuilder.AdoptResult result = builder.adoptShape(network, b, config);

        assertEquals(4, network.getEdges().size());
        assertEquals(2, network.getRoads().size());
        assertEquals(2, result.edges().size());
        assertEquals(1, result.junctionCount());
        assertNotNull(result.edges().getFirst());
        assertFalse(result.edges().stream().anyMatch(edge -> network.getEdge(edge.getId()) == null));
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
    void jsonRoundTripPreservesJunctionMarkingSettings() {
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setStopLines(JunctionMarkingSetting.OFF);
        junction.setContinuedMarkings(JunctionMarkingSetting.ON);
        junction.setCrosswalks(JunctionMarkingSetting.ON);
        junction.setTurnArrows(JunctionMarkingSetting.OFF);

        RoadNetwork restored = RoadNetwork.fromJson(network.toJson());
        RoadNode restoredNode = restored.getNode(junction.getId());

        assertEquals(JunctionMarkingSetting.OFF, restoredNode.getStopLines());
        assertEquals(JunctionMarkingSetting.ON, restoredNode.getContinuedMarkings());
        assertEquals(JunctionMarkingSetting.ON, restoredNode.getCrosswalks());
        assertEquals(JunctionMarkingSetting.OFF, restoredNode.getTurnArrows());
    }

    @Test
    void jsonRoundTripPreservesRoadProperties() {
        RoadNetwork network = new RoadNetwork();
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(10, 0));
        Road road = network.createRoad();
        road.setWidth(7);
        road.setMaxSlope(5.0f);
        RoadEdge edge = network.createEdge(start.getId(), end.getId(), List.of(
            new Vec2d(0, 0), new Vec2d(10, 0)
        ), road.getId());

        RoadNetwork restored = RoadNetwork.fromJson(network.toJson());
        Road restoredRoad = restored.getRoad(road.getId());
        RoadEdge restoredEdge = restored.getEdge(edge.getId());

        assertEquals(2, restored.getNodes().size());
        assertEquals(1, restored.getEdges().size());
        assertEquals(1, restored.getRoads().size());
        assertNotNull(restoredRoad);
        assertEquals(7, restoredRoad.getWidth());
        assertEquals(5.0f, restoredRoad.getMaxSlope());
        assertEquals(road.getId(), restoredEdge.getRoadId());
    }

    @Test
    void jsonRoundTripPreservesRoadId() {
        RoadNetwork network = new RoadNetwork();
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(10, 0));
        Road road = network.createRoad("adopt-group-123");
        RoadEdge edge = network.createEdge(start.getId(), end.getId(), List.of(
            new Vec2d(0, 0), new Vec2d(10, 0)
        ), road.getId());

        RoadNetwork restored = RoadNetwork.fromJson(network.toJson());
        RoadEdge restoredEdge = restored.getEdges().values().iterator().next();

        assertEquals("adopt-group-123", restoredEdge.getRoadId());
        assertNotNull(restored.getRoad("adopt-group-123"));
    }

    @Test
    void adoptShapeAssignsRoad() {
        RoadNetwork network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        RoadSystemConfig config = new RoadSystemConfig("road_system");

        PolylineShape shape = new PolylineShape(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0)
        ), false);

        builder.adoptShape(network, shape, config);
        RoadEdge edge = network.getEdges().values().iterator().next();

        assertNotNull(edge.getRoadId());
        assertFalse(edge.getRoadId().isBlank());
        assertNotNull(network.getRoad(edge.getRoadId()));
    }

    @Test
    void sameRoadIdSkipsIntersectionSplit() {
        RoadNetwork network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        Road sharedRoad = network.createRoad("same-adopt-group");

        RoadNode a1 = network.createNode(new Vec2d(0, 0));
        RoadNode a2 = network.createNode(new Vec2d(20, 0));
        RoadNode b1 = network.createNode(new Vec2d(10, -10));
        RoadNode b2 = network.createNode(new Vec2d(10, 10));

        network.createEdge(a1.getId(), a2.getId(), List.of(
            new Vec2d(0, 0), new Vec2d(20, 0)
        ), sharedRoad.getId());
        network.createEdge(b1.getId(), b2.getId(), List.of(
            new Vec2d(10, -10), new Vec2d(10, 10)
        ), sharedRoad.getId());

        int nodesBefore = network.getNodes().size();
        int edgesBefore = network.getEdges().size();
        builder.detectAndSplitIntersections(network);

        assertEquals(nodesBefore, network.getNodes().size());
        assertEquals(edgesBefore, network.getEdges().size());
    }

    @Test
    void differentRoadIdsStillSplitAtIntersection() {
        RoadNetwork network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();

        RoadNode a1 = network.createNode(new Vec2d(0, 5));
        RoadNode a2 = network.createNode(new Vec2d(10, 5));
        RoadNode b1 = network.createNode(new Vec2d(5, 0));
        RoadNode b2 = network.createNode(new Vec2d(5, 10));

        network.createEdge(a1.getId(), a2.getId(), List.of(
            new Vec2d(0, 5), new Vec2d(10, 5)
        ), network.createRoad("road-a").getId());
        network.createEdge(b1.getId(), b2.getId(), List.of(
            new Vec2d(5, 0), new Vec2d(5, 10)
        ), network.createRoad("road-b").getId());

        builder.detectAndSplitIntersections(network);

        assertEquals(4, network.getEdges().size());
        assertEquals(5, network.getNodes().size());
    }

    @Test
    void legacyJsonMigratesEdgePropertiesIntoRoad() {
        String legacyJson = """
            {
              "nodes": [
                {"id":"n1","position":{"x":0,"y":0},"connectedEdgeIds":["e1"]},
                {"id":"n2","position":{"x":10,"y":0},"connectedEdgeIds":["e1"]}
              ],
              "edges": [
                {
                  "id":"e1",
                  "startNodeId":"n1",
                  "endNodeId":"n2",
                  "centerlinePoints":[{"x":0,"y":0},{"x":10,"y":0}],
                  "width":7,
                  "maxSlope":5.0,
                  "sourceRoadId":"legacy-road-1"
                }
              ]
            }
            """;

        RoadNetwork restored = RoadNetwork.fromJson(legacyJson);
        RoadEdge edge = restored.getEdge("e1");
        Road road = restored.getRoad("legacy-road-1");

        assertNotNull(edge);
        assertNotNull(road);
        assertEquals("legacy-road-1", edge.getRoadId());
        assertEquals(7, road.getWidth());
        assertEquals(5.0f, road.getMaxSlope());
        assertEquals(List.of("e1"), List.copyOf(road.getSegmentIds()));
    }

    @Test
    void presetBuildsCrossSectionWithLaneCount() {
        RoadStyle style = RoadStyleCatalog.cityMain();
        RoadCrossSection section = RoadCrossSection.fromStyle(style);

        assertEquals(style.width, section.getCarriageway().getWidth());
        assertEquals(style.hasSidewalk, section.getSidewalk().getEnabled());
        assertEquals(style.includeShoulder, section.getShoulder().getEnabled());
    }

    @Test
    void roadApplyPresetUpdatesCrossSection() {
        Road road = new Road();
        RoadStyle style = RoadStyleCatalog.cityMain();

        road.applyStyle(style);

        assertEquals(style.width, road.getWidth());
        assertEquals(style.includeShoulder, road.getIncludeShoulder());
        assertEquals("city_main", road.getStyleId());
        if (style.maxSlope > 0f) {
            assertEquals(style.maxSlope, road.getMaxSlope());
        }
    }

    @Test
    void jsonRoundTripPreservesStyleId() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad();
        road.applyStyle(RoadStyleCatalog.mountain());

        String json = network.toJson();
        RoadNetwork restored = RoadNetwork.fromJson(json);
        Road restoredRoad = restored.getRoad(road.getId());

        assertNotNull(restoredRoad);
        assertEquals("mountain", restoredRoad.getStyleId());
        assertTrue(restoredRoad.getIncludeShoulder());
    }

    @Test
    void jsonRoundTripPreservesCenterLineStyle() {
        RoadNetwork network = new RoadNetwork();
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(10, 0));
        Road road = network.createRoad();
        road.setCenterLineStyle(com.plot.plugin.road.model.section.CenterLineStyle.DOUBLE_SOLID);
        road.setMarkingMaterial(com.plot.plugin.road.RoadMaterialUtils.DEFAULT_ROAD_BLOCK);
        road.setWidth(9);
        road.getCrossSection().getCarriageway().setLaneCount(2);
        road.getCrossSection().getCarriageway().setLaneWidthAt(0, 4);
        road.getCrossSection().getCarriageway().setLaneWidthAt(1, 5);
        network.createEdge(start.getId(), end.getId(), List.of(
            new Vec2d(0, 0), new Vec2d(10, 0)
        ), road.getId());

        RoadNetwork restored = RoadNetwork.fromJson(network.toJson());
        Road restoredRoad = restored.getRoad(road.getId());

        assertNotNull(restoredRoad);
        assertEquals(com.plot.plugin.road.model.section.CenterLineStyle.DOUBLE_SOLID,
            restoredRoad.getCenterLineStyle());
        assertEquals(com.plot.plugin.road.RoadMaterialUtils.DEFAULT_ROAD_BLOCK,
            restoredRoad.getMarkingMaterial());
        assertEquals(List.of(4, 5),
            restoredRoad.getCrossSection().getCarriageway().resolveLaneWidths(9));
    }

    @Test
    void jsonRoundTripPreservesCrossSection() {
        RoadNetwork network = new RoadNetwork();
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(10, 0));
        Road road = network.createRoad();
        road.setWidth(7);
        road.setIncludeShoulder(true);
        road.setShoulderWidth(2);
        road.setIncludeDrainage(true);
        road.setStreetlightSpacing(12);
        road.getCrossSection().getCarriageway().setLaneCount(2);
        network.createEdge(start.getId(), end.getId(), List.of(
            new Vec2d(0, 0), new Vec2d(10, 0)
        ), road.getId());

        RoadNetwork restored = RoadNetwork.fromJson(network.toJson());
        Road restoredRoad = restored.getRoad(road.getId());

        assertNotNull(restoredRoad);
        assertEquals(7, restoredRoad.getWidth());
        assertEquals(2, restoredRoad.getCrossSection().getCarriageway().getLaneCount());
        assertEquals(true, restoredRoad.getIncludeShoulder());
        assertEquals(2, restoredRoad.getShoulderWidth());
        assertEquals(true, restoredRoad.getIncludeDrainage());
        assertEquals(12, restoredRoad.getStreetlightSpacing());
    }

    @Test
    void legacyFlatRoadJsonMigratesIntoCrossSection() {
        String legacyJson = """
            {
              "nodes": [
                {"id":"n1","position":{"x":0,"y":0},"connectedEdgeIds":["e1"]},
                {"id":"n2","position":{"x":10,"y":0},"connectedEdgeIds":["e1"]}
              ],
              "edges": [
                {
                  "id":"e1",
                  "startNodeId":"n1",
                  "endNodeId":"n2",
                  "centerlinePoints":[{"x":0,"y":0},{"x":10,"y":0}],
                  "roadId":"r1"
                }
              ],
              "roads": [
                {
                  "id":"r1",
                  "width":8,
                  "includeSidewalk":true,
                  "sidewalkWidth":2,
                  "includeShoulder":true,
                  "shoulderWidth":1,
                  "includeDrainage":false,
                  "segmentIds":["e1"]
                }
              ]
            }
            """;

        RoadNetwork restored = RoadNetwork.fromJson(legacyJson);
        Road road = restored.getRoad("r1");

        assertNotNull(road);
        assertEquals(8, road.getWidth());
        assertEquals(true, road.getIncludeSidewalk());
        assertEquals(2, road.getSidewalkWidth());
        assertEquals(true, road.getIncludeShoulder());
        assertEquals(1, road.getShoulderWidth());
        assertEquals(false, road.getIncludeDrainage());
    }

    @Test
    void jsonRoundTripPreservesCornerRadius() {
        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setCornerRadius(3.5);
        junction.addEdge("e1");
        junction.addEdge("e2");
        junction.addEdge("e3");

        RoadNetwork restored = RoadNetwork.fromJson(network.toJson());
        RoadNode restoredNode = restored.getNode(junction.getId());

        assertEquals(3.5, restoredNode.getCornerRadius());
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
}
