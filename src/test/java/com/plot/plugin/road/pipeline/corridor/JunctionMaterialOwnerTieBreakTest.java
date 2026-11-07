package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JunctionMaterialOwnerTieBreakTest {

    @Test
    void equalWidthPrefersLexicographicallySmallerEdgeId() {
        RoadSystemConfig config = new RoadSystemConfig("material-tie");
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        roadA.setWidth(7);
        roadA.setMaterial("minecraft:white_concrete");
        Road roadB = network.createRoad("road-b");
        roadB.setWidth(7);
        roadB.setMaterial("minecraft:oak_planks");

        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode n = network.createNode(new Vec2d(0, 20));
        RoadNode s = network.createNode(new Vec2d(0, -20));
        RoadNode e = network.createNode(new Vec2d(20, 0));
        RoadNode w = network.createNode(new Vec2d(-20, 0));
        RoadEdge north = network.createEdge(junction.getId(), n.getId(),
            List.of(junction.getPosition(), n.getPosition()), roadB.getId());
        RoadEdge south = network.createEdge(junction.getId(), s.getId(),
            List.of(junction.getPosition(), s.getPosition()), roadB.getId());
        RoadEdge east = network.createEdge(junction.getId(), e.getId(),
            List.of(junction.getPosition(), e.getPosition()), roadA.getId());
        RoadEdge west = network.createEdge(junction.getId(), w.getId(),
            List.of(junction.getPosition(), w.getPosition()), roadA.getId());

        RoadEdge fromList = JunctionMaterialPolicy.selectMaterialOwner(
            List.of(north, south, east, west), network, config);
        RoadEdge fromNode = JunctionMaterialPolicy.selectWidestEligibleJunctionEdge(
            junction, network, config);

        assertNotNull(fromList);
        assertNotNull(fromNode);
        assertEquals(fromList.getId(), fromNode.getId(),
            "list and node entry points must share the same tie-break");
        String expectedId = List.of(north.getId(), south.getId(), east.getId(), west.getId()).stream()
            .min(String::compareTo)
            .orElseThrow();
        // 同宽时取字典序更小 ID；两条 road 宽度相同，竞选全部四边。
        assertEquals(expectedId, fromList.getId());
    }
}
