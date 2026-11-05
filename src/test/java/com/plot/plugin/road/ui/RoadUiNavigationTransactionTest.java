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
import com.plot.plugin.road.ui.editor.RoadFloatingEditorHost;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorKind;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导航事务：草稿检查通过前不得改选择 / 打开编辑器；取消时须清除全部待执行副作用。
 * 另覆盖 RoadUIManager 协调路径中的「二次离开检查不得关掉刚打开的干净编辑器」。
 */
class RoadUiNavigationTransactionTest {

    private RoadNetworkManager manager;
    private RoadUiContext ctx;
    private RoadFloatingEditorHost host;
    private RoadEditPanel editPanel;
    private String roadA;
    private String roadB;
    private String edgeB;

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("nav-txn"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road a = network.createRoad("a");
        roadA = a.getId();
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), roadA);

        Road b = network.createRoad("b");
        roadB = b.getId();
        RoadNode n3 = network.createNode(new Vec2d(20, 0));
        edgeB = network.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), roadB).getId();

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
    void requestNavigationDoesNotChangeSelectionBeforeCommit() {
        manager.selectRoad(roadA, false);
        ctx.setActiveTab(RoadUiTab.CROSS_SECTION);

        ctx.requestEditRoad(roadB);

        assertEquals(Set.of(roadA), manager.getSelectedRoadIds());
        assertEquals(RoadUiTab.CROSS_SECTION, ctx.activeTab());
        assertEquals(RoadUiTab.CROSS_SECTION, ctx.pendingTab());
        assertEquals(roadB, ctx.pendingNavigation().roadId());
    }

    @Test
    void rejectedNavigationClearsFullRequestWithoutChangingSelection() {
        manager.selectRoad(roadA, false);
        ctx.setActiveTab(RoadUiTab.CROSS_SECTION);
        ctx.requestEditCenterline(roadB);

        assertEquals(Set.of(roadA), manager.getSelectedRoadIds());
        ctx.clearPendingNavigation();

        assertEquals(Set.of(roadA), manager.getSelectedRoadIds());
        assertNull(ctx.pendingNavigation());
        assertNull(ctx.pendingTab());
        assertEquals(RoadUiTab.CROSS_SECTION, ctx.activeTab());
    }

    @Test
    void cancelledNavigationDoesNotLeaveResidualEditorTargetForLaterTabSwitch() {
        manager.selectRoad(roadA, false);
        ctx.requestEditCenterline(roadB);
        assertEquals(roadB, ctx.pendingNavigation().roadId());
        assertEquals(RoadFloatingEditorKind.CENTERLINE, ctx.pendingNavigation().editorKind());

        ctx.clearPendingNavigation();
        ctx.requestTab(RoadUiTab.ROUTE);

        assertNull(ctx.pendingNavigation().roadId());
        assertNull(ctx.pendingNavigation().editorKind());
        assertEquals(RoadUiTab.ROUTE, ctx.pendingTab());
        assertEquals(Set.of(roadA), manager.getSelectedRoadIds());
    }

    @Test
    void profileNavigationQueuesEdgeWithoutSelectingRoadEarly() {
        manager.selectRoad(roadA, false);
        ctx.requestViewProfile(edgeB);

        assertEquals(Set.of(roadA), manager.getSelectedRoadIds());
        assertEquals(RoadUiTab.VERTICAL_PROFILE, ctx.pendingTab());
        assertEquals(edgeB, ctx.pendingNavigation().edgeId());
        assertNull(ctx.pendingNavigation().roadId());
    }

    @Test
    void managerStyleNavigationCommitKeepsCleanEditorAfterSecondLeaveCheck() {
        manager.selectRoad(roadA, false);
        ctx.setActiveTab(RoadUiTab.ROUTE);
        ctx.requestEditCrossSection(roadB);

        // 1) 草稿门禁（干净）
        assertFalse(editPanel.tryLeaveEdit());
        // 2) 提交选择 + 打开编辑器（对应 commitNavigation）
        var nav = ctx.pendingNavigation();
        manager.selectRoad(nav.roadId(), false);
        host.openOrFocus(ctx, nav.editorKind(), nav.roadId());
        ctx.setActiveTab(nav.targetTab());
        ctx.clearPendingNavigation();
        // 3) 帧末二次离开检查不得关掉刚打开的干净窗口
        assertFalse(editPanel.tryLeaveEdit());
        assertTrue(host.isOpen(RoadFloatingEditorKind.CROSS_SECTION));
        assertEquals(Set.of(roadB), manager.getSelectedRoadIds());
        assertEquals(RoadUiTab.CROSS_SECTION, ctx.activeTab());
    }

}
