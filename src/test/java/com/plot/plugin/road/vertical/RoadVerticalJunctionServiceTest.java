package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadVerticalJunctionServiceTest {

    private static final RoadSystemConfig CONFIG = new RoadSystemConfig("test");

    @Test
    void setAtGradeSharedElevationSyncsNodeAndFlatOverride() {
        RoadNetwork network = flatCross(70.0);
        Road flat = network.getRoad("flat");
        String nodeId = network.getNodes().values().stream()
            .filter(node -> Math.abs(node.getPosition().x) < 1e-6
                && Math.abs(node.getPosition().y) < 1e-6)
            .findFirst()
            .orElseThrow()
            .getId();

        assertEquals(1, RoadVerticalJunctionService.setAtGradeSharedElevation(
            network, nodeId, 76.0, CONFIG));

        RoadNode node = network.getNode(nodeId);
        assertEquals(76.0, node.getManualElevation(), 1e-6);
        FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, flat);
        assertEquals(76.0, intent.getIntersectionOverride(nodeId), 1e-6);
        assertEquals(RoadVerticalMode.FLAT, flat.getVerticalMode());
    }

    @Test
    void clearAtGradeSharedElevationRemovesNodeLockAndFlatOverride() {
        RoadNetwork network = flatCross(70.0);
        Road flat = network.getRoad("flat");
        String nodeId = network.getNodes().values().stream()
            .filter(node -> Math.abs(node.getPosition().x) < 1e-6
                && Math.abs(node.getPosition().y) < 1e-6)
            .findFirst()
            .orElseThrow()
            .getId();
        RoadVerticalJunctionService.setAtGradeSharedElevation(network, nodeId, 76.0, CONFIG);

        RoadVerticalJunctionService.clearAtGradeSharedElevation(network, nodeId, CONFIG);

        assertNull(network.getNode(nodeId).getManualElevation());
        FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, flat);
        assertTrue(intent.getIntersectionOverrides().isEmpty());
    }

    private static RoadNetwork flatCross(double base) {
        RoadNetwork network = new RoadNetwork();
        Road flat = network.createRoad("flat");
        Road side = network.createRoad("side");
        RoadNode center = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 50));
        RoadNode south = network.createNode(new Vec2d(0, -100));
        network.createEdge(center.getId(), north.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, 50)), side.getId());
        network.createEdge(center.getId(), south.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, -100)), flat.getId());
        FlatVerticalIntentSupport.enableFlatWithBase(network, flat, CONFIG, base);
        return network;
    }
}
