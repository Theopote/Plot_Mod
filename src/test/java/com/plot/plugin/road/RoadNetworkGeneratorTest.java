package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadNetworkGeneratorTest {

    @Test
    void resolveJunctionMaterialPrefersWidestConnectedEdge() {
        RoadSystemConfig config = new RoadSystemConfig("road_system");
        config.setSelectedMaterial("material.plot.concrete");
        config.setSelectedSidewalkMaterial("material.plot.gravel");

        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 10));
        RoadNode east = network.createNode(new Vec2d(10, 0));
        RoadNode west = network.createNode(new Vec2d(-10, 0));

        RoadEdge narrow = network.createEdge(
            junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 10)));
        narrow.setWidth(5);
        narrow.setMaterial("material.plot.stone");

        RoadEdge wide = network.createEdge(
            junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        wide.setWidth(9);
        wide.setMaterial("material.plot.planks");
        wide.setIncludeSidewalk(true);
        wide.setSidewalkMaterial("material.plot.planks");

        network.createEdge(
            junction.getId(), west.getId(), List.of(new Vec2d(0, 0), new Vec2d(-10, 0)));

        assertEquals(
            "material.plot.planks",
            RoadNetworkGenerator.resolveJunctionMaterial(junction, network, config, false));
        assertEquals(
            "material.plot.planks",
            RoadNetworkGenerator.resolveJunctionMaterial(junction, network, config, true));
    }
}
