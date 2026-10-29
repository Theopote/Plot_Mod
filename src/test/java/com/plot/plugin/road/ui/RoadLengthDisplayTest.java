package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.SnapshotCoordinateService;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadLengthDisplayTest {

    @Test
    void identityProjectionKeepsCanvasLengthAsBlocks() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r");
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(100, 0));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        assertEquals(
            100.0,
            RoadLengthDisplay.roadBlocks(network, road, IdentityCoordinateService.INSTANCE),
            1e-6);
        assertEquals("100.00 m", RoadLengthDisplay.formatRoadMeters(
            network, road, IdentityCoordinateService.INSTANCE));
    }

    @Test
    void scaledProjectionConvertsCanvasArcToWorldBlocks() {
        // 1 canvas unit = 2 blocks → 画布长 50 应显示为 100 m（格）
        var coordinates = SnapshotCoordinateService.uniformScale(2.0);
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r");
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(50, 0));
        var edge = network.createEdge(
            a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), road.getId());

        assertEquals(50.0, edge.getLength(), 1e-6);
        assertEquals(100.0, RoadLengthDisplay.edgeBlocks(edge, coordinates), 1e-6);
        assertEquals(100.0, RoadLengthDisplay.roadBlocks(network, road, coordinates), 1e-6);
        assertEquals(2.0, RoadLengthDisplay.canvasToWorldScale(network, road, coordinates), 1e-6);
        assertEquals(
            40.0,
            RoadLengthDisplay.canvasToBlocks(20.0, network, road, coordinates),
            1e-6);
        assertEquals(
            20.0,
            RoadLengthDisplay.blocksToCanvas(40.0, network, road, coordinates),
            1e-6);
        assertEquals("100.00 m", RoadLengthDisplay.formatEdgeMeters(edge, coordinates));
    }
}
