package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.profile.RoadProfileRoadList;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纵断面目标栏 / 活跃道路：空选不得回退假装选中首条。
 */
class RoadGenerateTargetBarSelectionTest {

    private RoadNetworkManager manager;
    private RoadUiContext ctx;
    private String roadA;
    private String roadB;

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("target-bar"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road a = network.createRoad("a");
        roadA = a.getId();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(30, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(30, 0)), a.getId());
        Road b = network.createRoad("b");
        roadB = b.getId();
        RoadNode n3 = network.createNode(new Vec2d(60, 0));
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(30, 0), new Vec2d(60, 0)), b.getId());

        ApplicationContext applicationContext = ApplicationContext.getInstance();
        PluginContext pluginContext = new PluginContext(
            applicationContext.getAppState(),
            applicationContext.getCommandService(),
            applicationContext.getEventBus(),
            applicationContext.getToolManager(),
            IdentityCoordinateService.INSTANCE,
            null,
            null,
            null);
        ctx = new RoadUiContext(
            manager, null, null, null, new RoadProjectStatus(), pluginContext);
    }

    @Test
    void resolveActiveRoadReturnsNullWithoutSelection() {
        assertTrue(manager.getSelectedRoadIds().isEmpty());
        assertNull(RoadProfileRoadList.resolveActiveRoad(manager.getNetwork(), ctx));
        assertEquals(Set.of(), manager.getSelectedRoadIds());
    }

    @Test
    void overviewListsAllRoadsWhenNoneSelected() {
        List<Road> overview = RoadProfileRoadList.listOverviewRoads(manager.getNetwork(), ctx);
        assertEquals(2, overview.size());
    }

    @Test
    void overviewListsOnlySelectedRoadsWhenMultiSelected() {
        manager.selectRoad(roadA, false);
        manager.selectRoad(roadB, true);
        List<Road> overview = RoadProfileRoadList.listOverviewRoads(manager.getNetwork(), ctx);
        assertEquals(2, overview.size());

        // 单选：概览仍列全部，便于浏览切换
        manager.selectRoad(roadA, false);
        overview = RoadProfileRoadList.listOverviewRoads(manager.getNetwork(), ctx);
        assertEquals(2, overview.size());
        assertEquals(roadA, RoadProfileRoadList.resolveActiveRoad(manager.getNetwork(), ctx).getId());
    }
}
