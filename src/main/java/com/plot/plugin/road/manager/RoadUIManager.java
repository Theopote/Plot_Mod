package com.plot.plugin.road.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import com.plot.core.tool.BaseTool;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.overlay.RoadOverlayController;
import com.plot.plugin.road.overlay.RoadOverlayEntry;
import com.plot.plugin.road.repair.RoadRepairDiagnosisCache;
import com.plot.plugin.road.ui.RoadAdoptPanel;
import com.plot.plugin.road.ui.RoadAutoRepairUi;
import com.plot.plugin.road.ui.RoadDefaultParamsPanel;
import com.plot.plugin.road.ui.RoadEdgeListPanel;
import com.plot.plugin.road.ui.RoadEditPanel;
import com.plot.plugin.road.ui.RoadGeneratePanel;
import com.plot.plugin.road.ui.RoadJunctionPanel;
import com.plot.plugin.road.ui.RoadNodePropertyPanel;
import com.plot.plugin.road.ui.RoadOverviewPanel;
import com.plot.plugin.road.ui.RoadToolbarPanel;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.ui.RoadUiTab;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.ui.PluginTabScrollUi;
import com.plot.ui.canvas.Canvas;
import com.plot.ui.canvas.CanvasAccess;
import imgui.ImGui;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiTabItemFlags;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 道路系统 ImGui 界面编排。
 */
public final class RoadUIManager implements RoadJunctionPropertyProvider {
    private final RoadUiContext ctx;
    private final RoadToolbarPanel toolbarPanel;
    private final RoadOverviewPanel overviewPanel;
    private final RoadAdoptPanel adoptPanel;
    private final RoadEditPanel editPanel;
    private final RoadGeneratePanel generatePanel;
    private final RoadEdgeListPanel edgeListPanel;
    private final RoadJunctionPanel junctionPanel;
    private final RoadNodePropertyPanel nodePropertyPanel;

    private List<RoadOverlayEntry> overlayEntries = List.of();

    public RoadUIManager(
            RoadNetworkManager networkManager,
            RoadPreviewManager previewManager,
            RoadPersistenceManager persistenceManager,
            RoadToolManager toolManager,
            RoadProjectStatus status,
            com.plot.core.context.PluginContext host) {
        this.ctx = new RoadUiContext(
            networkManager, previewManager, persistenceManager, toolManager, status, host);
        this.ctx.setPathsAdoptedListener(this::onPathsPicked);

        this.edgeListPanel = new RoadEdgeListPanel(ctx);
        this.junctionPanel = new RoadJunctionPanel(ctx);
        this.nodePropertyPanel = new RoadNodePropertyPanel(ctx);
        this.toolbarPanel = new RoadToolbarPanel(ctx);
        this.overviewPanel = new RoadOverviewPanel(ctx);
        this.adoptPanel = new RoadAdoptPanel(ctx, new RoadDefaultParamsPanel(ctx));
        this.editPanel = new RoadEditPanel(ctx, edgeListPanel, junctionPanel, nodePropertyPanel);
        this.generatePanel = new RoadGeneratePanel(ctx);
    }

    public RoadUiContext context() {
        return ctx;
    }

    public List<RoadOverlayEntry> overlayEntries() {
        return overlayEntries;
    }

    public void render() {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }

        if (ctx.toolManager().getPathPickSession().isActive()) {
            ctx.toolManager().tick();
        }
        ctx.previewManager().tickPreviewJob();
        refreshOverlaySnapshot();
        tickOverlayCanvasSelection();

        toolbarPanel.render();

        RoadUiTab pendingTab = ctx.pendingTab();

        if (ImGui.beginTabBar("##road_tabs", ImGuiTabBarFlags.None)) {
            renderTab(RoadUiTab.OVERVIEW, "plugin.road.tab.overview", pendingTab, overviewPanel::render);
            renderTab(RoadUiTab.ADOPT, "plugin.road.tab.adopt", pendingTab, adoptPanel::render);
            renderTab(RoadUiTab.EDIT, "plugin.road.tab.edit", pendingTab, editPanel::render);
            renderTab(RoadUiTab.GENERATE, "plugin.road.tab.generate", pendingTab, this::renderGenerateTab);
            ImGui.endTabBar();
        }

        if (pendingTab != null) {
            ctx.clearPendingTab();
        }
    }

    /** 拾取完成并自动认领道路后的 UI 反馈。 */
    public void onPathsPicked() {
        RoadRepairDiagnosisCache.invalidate();
        LinkedHashSet<String> roadIds = ctx.networkManager().getSelectedRoadIds();
        if (!roadIds.isEmpty()) {
            ctx.networkManager().selectRoad(roadIds.getFirst(), false);
        }
        ctx.requestTab(RoadUiTab.EDIT);
    }

    /** 画布叠加层渲染前刷新（与 ImGui 面板 render 解耦）。 */
    public void refreshOverlayForCanvas() {
        refreshOverlaySnapshot();
    }

    private void refreshOverlaySnapshot() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        boolean pickActive = ctx.toolManager().getPathPickSession().isActive();
        List<Shape> candidates = pickActive ? ctx.toolManager().getPickOverlayPaths() : List.of();
        LinkedHashSet<String> selectedRoadIds = ctx.networkManager().getSelectedRoadIds();
        String primaryRoadId = selectedRoadIds.isEmpty() ? null : selectedRoadIds.getFirst();
        overlayEntries = RoadOverlayController.snapshot(
            network,
            ctx.networkManager().getConfig(),
            selectedRoadIds,
            primaryRoadId,
            candidates,
            pickActive,
            Set.of());
    }

    private void tickOverlayCanvasSelection() {
        if (ctx.toolManager().getPathPickSession().isActive()) {
            return;
        }
        if (!CanvasAccess.isPresent()) {
            return;
        }
        Canvas canvas = CanvasAccess.get();
        Vec2d mouseScreen = new Vec2d(ImGui.getMousePosX(), ImGui.getMousePosY());
        if (!canvas.isScreenPosInsideCanvas(mouseScreen)) {
            return;
        }
        if (ImGui.getIO().getWantCaptureMouse()) {
            return;
        }
        if (!ImGui.isMouseClicked(0)) {
            return;
        }
        BaseTool tool = ctx.host().appState().getCurrentTool();
        if (tool == null || !"select".equals(tool.getId())) {
            return;
        }
        Vec2d world = canvas.screenToWorld(mouseScreen);
        String roadId = RoadOverlayController.hitTestRoad(overlayEntries, world.x, world.y);
        if (roadId != null && !roadId.isBlank()) {
            ctx.networkManager().selectRoad(roadId, false);
            ctx.requestTab(RoadUiTab.EDIT);
            RoadRepairDiagnosisCache.invalidate();
        }
    }

    private void renderTab(RoadUiTab tab, String labelKey, RoadUiTab pendingTab, Runnable body) {
        int flags = pendingTab == tab ? ImGuiTabItemFlags.SetSelected : ImGuiTabItemFlags.None;
        PluginTabScrollUi.renderTab(labelKey, flags, "##road_tab_" + tab.name().toLowerCase(), body);
    }

    private void renderGenerateTab() {
        String profileEdgeId = ctx.consumePendingProfileEdgeId();
        if (profileEdgeId != null && !profileEdgeId.isBlank()) {
            generatePanel.openProfileForEdge(profileEdgeId);
        }
        generatePanel.render();
    }

    public void renderDeferredModals() {
        edgeListPanel.renderDeleteConfirmPopup();
        generatePanel.renderBuildConfirmPopup();
        editPanel.renderUniformElevationConfirmPopup();
    }

    /**
     * 离开道路插件 Tab 时清理瞬时 UI 状态（不持久化路网）。
     */
    public void onDeactivate() {
        ctx.toolManager().cancel();
        ctx.cancelPreviewJobSilently();
        ctx.clearTransientUiState();
        RoadAutoRepairUi.invalidateCache();
    }

    @Override
    public boolean hasJunctionPropertyContent() {
        return ctx.networkManager().getSelectedNode() != null;
    }

    @Override
    public void renderJunctionPropertySection() {
        nodePropertyPanel.renderPropertySection(junctionPanel);
    }

    @Override
    public String getPropertySectionTitleKey() {
        RoadNode node = ctx.networkManager().getSelectedNode();
        if (node != null && node.isJunction()) {
            return "panel.plot.road_junction";
        }
        return "panel.plot.road_node";
    }
}
