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
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCrossSectionPreviewSectionTest {

    private RoadNetworkManager manager;
    private RoadUiContext ctx;

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("preview"), new RoadProjectStatus());
        ctx = new RoadUiContext(
            manager,
            null,
            null,
            null,
            new RoadProjectStatus(),
            pluginContext());
    }

    @Test
    void shareCheckUsesEngineeringEqualityNotPartialSignature() {
        RoadNetwork network = manager.getNetwork();
        Road a = network.createRoad("a");
        Road b = network.createRoad("b");
        a.setWidth(9);
        a.setLaneCount(2);
        b.setWidth(9);
        b.setLaneCount(2);
        // 旧签名不含路肩宽度：以前会误判为一致
        a.getCrossSection().getShoulder().setEnabled(true);
        a.getCrossSection().getShoulder().setWidth(2);
        b.getCrossSection().getShoulder().setEnabled(true);
        b.getCrossSection().getShoulder().setWidth(4);

        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), a.getId());
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), b.getId());
        manager.selectRoad(a.getId(), false);
        manager.selectRoad(b.getId(), true);

        assertFalse(RoadCrossSectionPreviewSection.selectedRoadsShareCrossSection(ctx));

        b.getCrossSection().getShoulder().setWidth(2);
        assertTrue(RoadCrossSectionPreviewSection.selectedRoadsShareCrossSection(ctx));
    }

    private static PluginContext pluginContext() {
        ApplicationContext applicationContext = ApplicationContext.getInstance();
        return new PluginContext(
            applicationContext.getAppState(),
            applicationContext.getCommandService(),
            applicationContext.getEventBus(),
            applicationContext.getToolManager(),
            IdentityCoordinateService.INSTANCE,
            null,
            null,
            null);
    }
}
