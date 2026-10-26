package com.plot.plugin.road.ui.editor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadFloatingEditorHostTest {

    private RoadFloatingEditorHost host;
    private RoadUiContext ctx;
    private String roadA;
    private String roadB;

    @BeforeEach
    void setUp() {
        host = new RoadFloatingEditorHost();
        RoadNetworkManager networkManager =
            new RoadNetworkManager(new RoadSystemConfig("test"), new RoadProjectStatus());
        RoadNetwork network = networkManager.getNetwork();
        Road road = network.createRoad("a");
        roadA = road.getId();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), roadA);

        Road road2 = network.createRoad("b");
        roadB = road2.getId();
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), roadB);

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
    }

    @Test
    void openOrFocusOpensCrossSectionEditor() {
        assertFalse(host.isOpen(RoadFloatingEditorKind.CROSS_SECTION));
        host.openOrFocus(ctx, RoadFloatingEditorKind.CROSS_SECTION, roadA);
        assertTrue(host.isOpen(RoadFloatingEditorKind.CROSS_SECTION));
    }

    @Test
    void finishOrPromptOutsideScopeClosesCleanEditor() {
        host.openOrFocus(ctx, RoadFloatingEditorKind.CROSS_SECTION, roadA);
        assertFalse(host.finishOrPromptOutsideScope(ctx, Set.of(roadB)));
        assertFalse(host.isOpen(RoadFloatingEditorKind.CROSS_SECTION));
    }
}
