package com.plot.plugin.road.model;

import com.google.gson.JsonParser;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.vertical.FlatVerticalIntent;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoadRetiredExperimentCompatibilityTest {
    @Test
    void oldRoadOverrideDoesNotDiscardGeometryOrFlatElevation() throws Exception {
        var network = new RoadNetwork();
        var road = network.createRoad("legacy road");
        road.setName("legacy road");
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(80, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        road.setVerticalMode(RoadVerticalMode.FLAT);
        road.setFlatVerticalIntent(new FlatVerticalIntent(-20));
        var json = JsonParser.parseString(network.toJson()).getAsJsonObject();
        json.getAsJsonArray("roads").get(0).getAsJsonObject()
            .addProperty("oscillationReductionEnabled", true);

        var loaded = RoadNetwork.fromJson(json.toString());

        assertEquals(1, loaded.getEdges().size());
        assertEquals(2, loaded.getNodes().size());
        assertEquals("legacy road", loaded.getRoad(road.getId()).getName());
        assertEquals(-20, loaded.getRoad(road.getId()).getFlatVerticalIntent().getBaseElevation());
        assertFalse(loaded.toJson().contains("oscillationReductionEnabled"));
    }
}
