package com.plot.plugin.road.model;

import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkVerticalModeMigrationTest {

    @Test
    void schemaV1MissingVerticalModeMigratesToFitTerrain() throws Exception {
        Road road = loadSingleRoad("""
            {
              "schemaVersion": 1,
              "nodes": [
                {"id":"n1","position":{"x":0,"y":0},"connectedEdgeIds":["e1"]},
                {"id":"n2","position":{"x":100,"y":0},"connectedEdgeIds":["e1"]}
              ],
              "edges": [{
                "id":"e1",
                "startNodeId":"n1",
                "endNodeId":"n2",
                "centerlinePoints":[{"x":0,"y":0},{"x":100,"y":0}],
                "roadId":"road-a"
              }],
              "roads": [{
                "id":"road-a",
                "crossSection": {"carriageway": {"width": 5}},
                "segmentIds":["e1"]
              }]
            }
            """);

        assertEquals(RoadVerticalMode.FIT_TERRAIN, road.getVerticalMode());
        assertEquals(RoadVerticalMode.FIT_TERRAIN, road.getStoredVerticalMode());
    }

    @Test
    void schemaV1AutoSmoothWithoutProfileMigratesToFitTerrain() throws Exception {
        Road road = loadSingleRoad("""
            {
              "schemaVersion": 1,
              "nodes": [
                {"id":"n1","position":{"x":0,"y":0},"connectedEdgeIds":["e1"]},
                {"id":"n2","position":{"x":50,"y":0},"connectedEdgeIds":["e1"]}
              ],
              "edges": [{
                "id":"e1",
                "startNodeId":"n1",
                "endNodeId":"n2",
                "centerlinePoints":[{"x":0,"y":0},{"x":50,"y":0}],
                "roadId":"road-a"
              }],
              "roads": [{
                "id":"road-a",
                "crossSection": {"carriageway": {"width": 5}},
                "verticalMode": "AUTO_SMOOTH",
                "segmentIds":["e1"]
              }]
            }
            """);

        assertEquals(RoadVerticalMode.FIT_TERRAIN, road.getVerticalMode());
    }

    @Test
    void schemaV2ExplicitAutoSmoothIsPreserved() throws Exception {
        Road road = loadSingleRoad("""
            {
              "schemaVersion": 2,
              "nodes": [
                {"id":"n1","position":{"x":0,"y":0},"connectedEdgeIds":["e1"]},
                {"id":"n2","position":{"x":50,"y":0},"connectedEdgeIds":["e1"]}
              ],
              "edges": [{
                "id":"e1",
                "startNodeId":"n1",
                "endNodeId":"n2",
                "centerlinePoints":[{"x":0,"y":0},{"x":50,"y":0}],
                "roadId":"road-a"
              }],
              "roads": [{
                "id":"road-a",
                "crossSection": {"carriageway": {"width": 5}},
                "verticalMode": "AUTO_SMOOTH",
                "segmentIds":["e1"]
              }]
            }
            """);

        assertEquals(RoadVerticalMode.AUTO_SMOOTH, road.getVerticalMode());
    }

    @Test
    void omittedVerticalModeWithAlignmentNormalizesToManualProfile() throws Exception {
        Road road = loadSingleRoad("""
            {
              "schemaVersion": 2,
              "nodes": [
                {"id":"n1","position":{"x":0,"y":0},"connectedEdgeIds":["e1"]},
                {"id":"n2","position":{"x":80,"y":0},"connectedEdgeIds":["e1"]}
              ],
              "edges": [{
                "id":"e1",
                "startNodeId":"n1",
                "endNodeId":"n2",
                "centerlinePoints":[{"x":0,"y":0},{"x":80,"y":0}],
                "roadId":"road-a"
              }],
              "roads": [{
                "id":"road-a",
                "crossSection": {"carriageway": {"width": 5}},
                "verticalAlignment": {
                  "pvis": [
                    {"station": 0.0, "elevation": 64.0},
                    {"station": 80.0, "elevation": 70.0}
                  ]
                },
                "segmentIds":["e1"]
              }]
            }
            """);

        assertEquals(RoadVerticalMode.MANUAL_PROFILE, road.getVerticalMode());
    }

    @Test
    void flatIntentWithoutVerticalModeNormalizesToFlat() throws Exception {
        Road road = loadSingleRoad("""
            {
              "schemaVersion": 2,
              "nodes": [
                {"id":"n1","position":{"x":0,"y":0},"connectedEdgeIds":["e1"]},
                {"id":"n2","position":{"x":40,"y":0},"connectedEdgeIds":["e1"]}
              ],
              "edges": [{
                "id":"e1",
                "startNodeId":"n1",
                "endNodeId":"n2",
                "centerlinePoints":[{"x":0,"y":0},{"x":40,"y":0}],
                "roadId":"road-a"
              }],
              "roads": [{
                "id":"road-a",
                "crossSection": {"carriageway": {"width": 5}},
                "flatVerticalIntent": {"baseElevation": 72.0},
                "segmentIds":["e1"]
              }]
            }
            """);

        assertEquals(RoadVerticalMode.FLAT, road.getVerticalMode());
    }

    @Test
    void newSaveOmitsImplicitVerticalModeUntilNormalizedOnLoad() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("legacy");
        RoadNode n1 = network.createNode(new com.plot.api.geometry.Vec2d(0, 0));
        RoadNode n2 = network.createNode(new com.plot.api.geometry.Vec2d(30, 0));
        network.createEdge(
            n1.getId(),
            n2.getId(),
            java.util.List.of(n1.getPosition(), n2.getPosition()),
            road.getId());

        String json = network.toJson();
        assertFalse(json.contains("\"verticalMode\""));

        Road restored = RoadNetwork.parseSnapshot(json).getRoad("legacy");
        assertEquals(RoadVerticalMode.FIT_TERRAIN, restored.getVerticalMode());
    }

    @Test
    void migratedNetworkRoundTripWritesExplicitFitTerrain() {
        RoadNetwork network = RoadNetwork.parseSnapshot("""
            {
              "schemaVersion": 1,
              "nodes": [
                {"id":"n1","position":{"x":0,"y":0},"connectedEdgeIds":["e1"]},
                {"id":"n2","position":{"x":20,"y":0},"connectedEdgeIds":["e1"]}
              ],
              "edges": [{
                "id":"e1",
                "startNodeId":"n1",
                "endNodeId":"n2",
                "centerlinePoints":[{"x":0,"y":0},{"x":20,"y":0}],
                "roadId":"road-a"
              }],
              "roads": [{
                "id":"road-a",
                "crossSection": {"carriageway": {"width": 5}},
                "segmentIds":["e1"]
              }]
            }
            """);
        String saved = network.toJson();
        assertTrue(saved.contains("\"schemaVersion\": 2"));
        assertTrue(saved.contains("\"verticalMode\": \"FIT_TERRAIN\""));
    }

    private static Road loadSingleRoad(String json) throws RoadNetworkFormatException {
        RoadNetwork network = RoadNetwork.fromJson(json);
        return network.getRoad("road-a");
    }
}
