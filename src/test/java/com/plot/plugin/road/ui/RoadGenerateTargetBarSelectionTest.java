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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纵断面目标栏：无选择时可用列表首条做概览，但 resolveActiveRoad 不得在 render 路径外被误用成自动选中。
 * 本测试锁定「未选择时 selectedRoadIds 保持为空」。
 */
class RoadGenerateTargetBarSelectionTest {

    private RoadNetworkManager manager;
    private RoadUiContext ctx;

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("target-bar"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road a = network.createRoad("a");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(30, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(30, 0)), a.getId());
        Road b = network.createRoad("b");
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
    void resolveActiveRoadFallsBackWithoutMutatingSelection() {
        assertTrue(manager.getSelectedRoadIds().isEmpty());
        Road overview = RoadProfileRoadList.resolveActiveRoad(manager.getNetwork(), ctx);
        assertEquals(RoadProfileRoadList.listStationableRoads(manager.getNetwork()).getFirst().getId(),
            overview.getId());
        assertEquals(Set.of(), manager.getSelectedRoadIds());
    }
}
