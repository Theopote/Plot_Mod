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
    void setSelectedNodeClearsExistingEdgeSelection() {
        manager.handleEdgeSelect(edgeA, false);

        manager.setSelectedNodeId(nodeId);

        assertEquals(nodeId, manager.getSelectedNodeId());
        assertTrue(manager.getSelectedEdgeIds().isEmpty());
        assertTrue(manager.getPrimarySelectedEdgeId().isBlank());
    }

    @Test
    void applyUniformFlatElevationDoesNotWriteConfigMaxSlope() {
        RoadSystemConfig config = manager.getConfig();
        config.setMaxSlope(12f);
        var road = manager.getNetwork().createRoad(config);
        road.setMaxSlope(8f);
        manager.getNetwork().assignEdgeToRoad(edgeA, road.getId());

        manager.applyCustomUniformFlatElevation(64);

        assertEquals(12f, config.getMaxSlope(), 0.001f);
        assertEquals(0f, road.getMaxSlope(), 0.001f);
    }

    @Test
    void batchApplyUpdatesSlopeBatterOnSelectedRoads() {
        var roadA = manager.getNetwork().createRoad(manager.getConfig());
        roadA.setIncludeSlopeBatter(false);
        manager.getNetwork().assignEdgeToRoad(edgeA, roadA.getId());
        var roadB = manager.getNetwork().createRoad(manager.getConfig());
        roadB.setIncludeSlopeBatter(false);
        manager.getNetwork().assignEdgeToRoad(edgeB, roadB.getId());

        manager.handleEdgeSelect(edgeA, false);
        manager.handleEdgeSelect(edgeB, true);

        RoadNetworkManager.BatchEditDefaults base = manager.loadBatchEditDefaults();
        RoadNetworkManager.BatchEditDefaults draft = new RoadNetworkManager.BatchEditDefaults(
            base.width(),
            base.laneCount(),
            base.material(),
            base.includeShoulder(),
            base.shoulderWidth(),
            base.includeSidewalk(),
            base.sidewalkWidth(),
            base.sidewalkMaterial(),
            base.includeDrainage(),
            base.includeBikeLane(),
            base.bikeLaneWidth(),
            base.includeMedian(),
            base.medianWidth(),
            base.streetlightSpacing(),
            base.laneDividers(),
            base.centerLineStyle(),
            base.markingMaterial(),
            true,
            2.0f,
            1.5f,
            "minecraft:gravel",
            "minecraft:dirt",
            base.maxSlope());
        manager.applyBatchEdit(draft);

        assertTrue(roadA.getIncludeSlopeBatter());
        assertEquals(2.0f, roadA.getFillSlopeRatio(), 0.001f);
        assertEquals(1.5f, roadA.getCutSlopeRatio(), 0.001f);
        assertTrue(roadB.getIncludeSlopeBatter());
        assertEquals(2.0f, roadB.getFillSlopeRatio(), 0.001f);
    }

    @Test
    void batchDraftToCrossSectionPreservesSlopeIndependentlyOfShoulder() {
        RoadNetworkManager.BatchEditDefaults draft = new RoadNetworkManager.BatchEditDefaults(
            7,
            2,
            com.plot.core.material.MaterialMix.single("minecraft:stone"),
            false,
            1,
            false,
            1,
            "minecraft:stone",
            false,
            false,
            1,
            false,
            1,
            0,
            true,
            com.plot.plugin.road.model.section.CenterLineStyle.SINGLE_DASHED,
            "minecraft:white_concrete",
            true,
            2.0f,
            1.5f,
            "minecraft:gravel",
            "minecraft:dirt",
            8f);

        var section = draft.toCrossSection();
        var resolved = section.resolve(manager.getConfig());

        assertFalse(resolved.includeShoulder);
        assertTrue(resolved.includeSlopeBatter);
        assertEquals(2.0f, resolved.fillSlopeRatio, 0.001f);
        assertEquals(1.5f, resolved.cutSlopeRatio, 0.001f);
        assertEquals(7, resolved.carriagewayWidth);
        assertEquals(2, resolved.laneCount);
    }

    @Test
    void batchDraftReloadsWhenSwitchingToDifferentSelectionOfSameSize() {
        var flatRoad = manager.getNetwork().createRoad(manager.getConfig());
        flatRoad.setMaxSlope(0f);
        manager.getNetwork().assignEdgeToRoad(edgeA, flatRoad.getId());
        var gradedRoad = manager.getNetwork().createRoad(manager.getConfig());
        gradedRoad.setMaxSlope(12f);
        manager.getNetwork().assignEdgeToRoad(edgeB, gradedRoad.getId());

        manager.handleEdgeSelect(edgeA, false);
        assertEquals(0f, manager.loadBatchEditDefaults().maxSlope(), 0.001f);

        // 选择数量仍为 1，但草稿必须从新道路重载，不能沿用上一条平路的 0%。
        manager.handleEdgeSelect(edgeB, false);
        assertEquals(12f, manager.loadBatchEditDefaults().maxSlope(), 0.001f);
    }
}
