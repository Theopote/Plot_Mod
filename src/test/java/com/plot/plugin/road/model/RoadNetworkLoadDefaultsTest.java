package com.plot.plugin.road.model;

import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkLoadDefaultsTest {

    @Test
    void currentSchemaOmitsVerticalModeDefaultsToFitTerrain() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-a");
        RoadNode n1 = network.createNode(new com.plot.api.geometry.Vec2d(0, 0));
        RoadNode n2 = network.createNode(new com.plot.api.geometry.Vec2d(30, 0));
        network.createEdge(
            n1.getId(),
            n2.getId(),
            java.util.List.of(n1.getPosition(), n2.getPosition()),
            road.getId());

        String json = network.toJson();
        assertFalse(json.contains("\"verticalMode\""));

        Road restored = RoadNetwork.parseSnapshot(json).getRoad("road-a");
        assertEquals(RoadVerticalMode.FIT_TERRAIN, restored.getVerticalMode());
        assertEquals(ProfileSource.AUTO, restored.getEffectiveProfileSource());
    }

    @Test
    void omittedModeWithAlignmentDefaultsToEditedFitTerrain() throws Exception {
        Road road = loadSingleRoad("""
            {
              "schemaVersion": 4,
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

        assertEquals(RoadVerticalMode.FIT_TERRAIN, road.getVerticalMode());
        assertEquals(ProfileSource.EDITED, road.getEffectiveProfileSource());
    }

    @Test
    void flatIntentWithoutVerticalModeDefaultsToFlat() throws Exception {
        Road road = loadSingleRoad("""
            {
              "schemaVersion": 4,
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
    void olderSchemaVersionsAreRejected() {
        RoadNetworkFormatException error = assertThrows(
            RoadNetworkFormatException.class,
            () -> RoadNetwork.fromJson("""
                {
                  "schemaVersion": 1,
                  "nodes": [],
                  "edges": [],
                  "roads": []
                }
                """));
        assertEquals(
            RoadNetworkFormatException.Reason.UNSUPPORTED_FORMAT_VERSION,
            error.getReason());
    }

    @Test
    void roundTripWritesCurrentSchemaVersion() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("road-a");
        RoadNode n1 = network.createNode(new com.plot.api.geometry.Vec2d(0, 0));
        RoadNode n2 = network.createNode(new com.plot.api.geometry.Vec2d(20, 0));
        network.createEdge(
            n1.getId(),
            n2.getId(),
            java.util.List.of(n1.getPosition(), n2.getPosition()),
            road.getId());

        String saved = network.toJson();
        assertTrue(saved.contains("\"schemaVersion\": " + RoadNetwork.CURRENT_SCHEMA_VERSION));
    }

    private static Road loadSingleRoad(String json) throws RoadNetworkFormatException {
        RoadNetwork network = RoadNetwork.fromJson(json);
        return network.getRoad("road-a");
    }
}
