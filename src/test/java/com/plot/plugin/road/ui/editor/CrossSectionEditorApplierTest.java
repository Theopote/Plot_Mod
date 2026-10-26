package com.plot.plugin.road.ui.editor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossSectionEditorApplierTest {

    private RoadNetworkManager manager;
    private RoadNetwork live;
    private RoadSystemConfig config;
    private String roadId;

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("test"), new RoadProjectStatus());
        live = manager.getNetwork();
        config = manager.getConfig();
        Road road = live.createRoad("main");
        roadId = road.getId();
        road.setWidth(10);
        RoadNode a = live.createNode(new Vec2d(0, 0));
        RoadNode b = live.createNode(new Vec2d(10, 0));
        live.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), roadId);
    }

    @Test
    void hasChangesDetectsCrossSectionEdits() {
        RoadNetwork openSnapshot = live.snapshot();
        RoadNetwork working = openSnapshot.snapshot();
        assertFalse(CrossSectionEditorApplier.hasChanges(openSnapshot, working, roadId, config));

        working.getRoad(roadId).setWidth(14);
        assertTrue(CrossSectionEditorApplier.hasChanges(openSnapshot, working, roadId, config));
    }

    @Test
    void applyToLiveCopiesCrossSectionFields() {
        RoadNetwork openSnapshot = live.snapshot();
        RoadNetwork working = openSnapshot.snapshot();
        working.getRoad(roadId).setWidth(16);
        working.getRoad(roadId).setLaneCount(3);

        CrossSectionEditorApplier.applyToLive(live, working, roadId, config);

        assertEquals(16, live.getRoad(roadId).getWidth());
        assertEquals(3, live.getRoad(roadId).getLaneCount());
    }
}
