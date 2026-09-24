package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadListRenameTest {

    private RoadUiContext ctx;
    private RoadNetworkManager networkManager;

    @BeforeEach
    void setUp() {
        networkManager = new RoadNetworkManager(new RoadSystemConfig("test"), new RoadProjectStatus());
        ApplicationContext applicationContext = ApplicationContext.getInstance();
        PluginContext host = new PluginContext(
            applicationContext.getAppState(),
            applicationContext.getCommandService(),
            applicationContext.getEventBus(),
            applicationContext.getToolManager(),
            IdentityCoordinateService.INSTANCE,
            null,
            null,
            null);
        ctx = new RoadUiContext(
            networkManager,
            null,
            null,
            null,
            new RoadProjectStatus(),
            host);
        networkManager.adoptSelectedPaths(List.of(new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(10, 0)), false)));
    }

    @Test
    void commitRoadNameRenameUpdatesRoadAndEndsEditing() {
        Road road = networkManager.getNetwork().getRoads().values().iterator().next();
        ctx.beginRoadNameRename(road);
        assertTrue(ctx.roadListRename().isRenaming(road.getId()));

        ctx.roadNameBuffer().set("Main Street");
        ctx.commitRoadNameRename(road);

        assertEquals("Main Street", road.getName());
        assertFalse(ctx.roadListRename().isRenaming(road.getId()));
        assertTrue(networkManager.canUndo());
    }

    @Test
    void cancelRoadNameRenameRestoresDraftWithoutHistory() {
        Road road = networkManager.getNetwork().getRoads().values().iterator().next();
        road.setName("Original");
        boolean couldUndoBefore = networkManager.canUndo();
        ctx.beginRoadNameRename(road);
        ctx.roadNameBuffer().set("Draft");
        ctx.cancelRoadNameRename(road);

        assertEquals("Original", road.getName());
        assertEquals(couldUndoBefore, networkManager.canUndo());
    }

    @Test
    void clearingNameOnCommitRemovesCustomName() {
        Road road = networkManager.getNetwork().getRoads().values().iterator().next();
        road.setName("Named");
        ctx.beginRoadNameRename(road);
        ctx.roadNameBuffer().set("   ");
        ctx.commitRoadNameRename(road);

        assertEquals(null, road.getName());
        assertTrue(networkManager.canUndo());
    }
}
