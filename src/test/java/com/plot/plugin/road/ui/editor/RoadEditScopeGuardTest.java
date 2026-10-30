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
import com.plot.plugin.road.ui.RoadDefaultParamsPanel;
import com.plot.plugin.road.ui.RoadEditPanel;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.ui.RoadUiTab;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadEditScopeGuardTest {

    private RoadNetworkManager manager;
    private RoadUiContext ctx;
    private RoadFloatingEditorHost host;
    private RoadEditPanel editPanel;
    private String roadA;
    private String roadB;

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("scope-guard"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road a = network.createRoad("a");
        roadA = a.getId();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), roadA);

        Road b = network.createRoad("b");
        roadB = b.getId();
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        network.createEdge(n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), roadB);

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
        host = new RoadFloatingEditorHost();
        editPanel = new RoadEditPanel(ctx, new RoadDefaultParamsPanel(ctx), host);
    }

    @Test
    void reconcileRestoresSelectionWhenDirtyEditorBlocks() {
        manager.selectRoad(roadA, false);
        editPanel.reconcileRoadScope();

        host.openOrFocus(ctx, RoadFloatingEditorKind.CROSS_SECTION, roadA);
        CrossSectionDesignerWindow editor = (CrossSectionDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.CROSS_SECTION);
        editor.editorSession().workingNetwork().getRoad(roadA).setWidth(20);

        manager.selectRoad(roadB, false);
        editPanel.reconcileRoadScope();

        assertEquals(Set.of(roadA), manager.getSelectedRoadIds());
        assertTrue(host.isOpen(RoadFloatingEditorKind.CROSS_SECTION));
        assertTrue(editPanel.hasLeaveBlocked());
    }

    @Test
    void tryLeaveEditBlocksWhenDirty() {
        manager.selectRoad(roadA, false);
        host.openOrFocus(ctx, RoadFloatingEditorKind.CROSS_SECTION, roadA);
        CrossSectionDesignerWindow editor = (CrossSectionDesignerWindow) host.editorForKind(
            RoadFloatingEditorKind.CROSS_SECTION);
        editor.editorSession().workingNetwork().getRoad(roadA).setWidth(22);

        assertTrue(editPanel.tryLeaveEdit());
        assertTrue(host.isOpen(RoadFloatingEditorKind.CROSS_SECTION));
    }

    @Test
    void requestTabDoesNotFlipActiveImmediately() {
        ctx.setActiveTab(RoadUiTab.EDIT);
        ctx.requestTab(RoadUiTab.GENERATE);
        assertEquals(RoadUiTab.EDIT, ctx.activeTab());
        assertEquals(RoadUiTab.GENERATE, ctx.pendingTab());
    }
}
