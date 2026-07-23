package com.plot.plugin.road.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkManagerSelectionTest {
    private RoadNetworkManager manager;
    private String edgeA;
    private String edgeB;
    private String nodeId;

    @BeforeEach
    void setUp() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        manager = new RoadNetworkManager(config, new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        RoadEdge e1 = network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        RoadEdge e2 = network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)));
        edgeA = e1.getId();
        edgeB = e2.getId();
        nodeId = n2.getId();
    }

    @Test
    void multiDeselectUpdatesPrimaryToRemainingSelection() {
        manager.handleEdgeSelect(edgeA, false);
        manager.handleEdgeSelect(edgeB, true);
        assertEquals(edgeB, manager.getPrimarySelectedEdgeId());

        manager.handleEdgeSelect(edgeB, true); // deselect B
        assertEquals(1, manager.getSelectedEdgeIds().size());
        assertTrue(manager.getSelectedEdgeIds().contains(edgeA));
        assertEquals(edgeA, manager.getPrimarySelectedEdgeId());
    }

    @Test
    void selectAllEdgesClearsNodeSelection() {
        manager.handleNodeSelect(nodeId);
        assertEquals(nodeId, manager.getSelectedNodeId());

        manager.selectAllEdges();
        assertTrue(manager.getSelectedNodeId().isBlank());
        assertEquals(2, manager.getSelectedEdgeIds().size());
        assertFalse(manager.getPrimarySelectedEdgeId().isBlank());
    }

    @Test
    void applyUniformFlatElevationDoesNotWriteConfigMaxSlope() {
        RoadSystemConfig config = manager.getConfig();
        config.setMaxSlope(12f);
        var road = manager.getNetwork().createRoad(config);
        road.setMaxSlope(8f);
        manager.getNetwork().linkEdgeToRoad(road.getId(), edgeA);

        manager.applyCustomUniformFlatElevation(64);

        assertEquals(12f, config.getMaxSlope(), 0.001f);
        assertEquals(0f, road.getMaxSlope(), 0.001f);
    }
}
