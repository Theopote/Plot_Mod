package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
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
        config.setSelectedMaterial("minecraft:white_concrete");
        config.setSelectedSidewalkMaterial("minecraft:gravel");

        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 10));
        RoadNode east = network.createNode(new Vec2d(10, 0));
        RoadNode west = network.createNode(new Vec2d(-10, 0));

        Road narrowRoad = network.createRoad();
        narrowRoad.setWidth(5);
        narrowRoad.setMaterial("minecraft:stone");
        network.createEdge(
            junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 10)), narrowRoad.getId());

        Road wideRoad = network.createRoad();
        wideRoad.setWidth(9);
        wideRoad.setMaterial("minecraft:oak_planks");
        wideRoad.setIncludeSidewalk(true);
        wideRoad.setSidewalkMaterial("minecraft:oak_planks");
        network.createEdge(
            junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), wideRoad.getId());

        network.createEdge(
            junction.getId(), west.getId(), List.of(new Vec2d(0, 0), new Vec2d(-10, 0)));

        assertEquals(
            "minecraft:oak_planks",
            RoadNetworkGenerator.resolveJunctionMaterial(junction, network, config, false));
        assertEquals(
            "minecraft:oak_planks",
            RoadNetworkGenerator.resolveJunctionMaterial(junction, network, config, true));
    }
}
