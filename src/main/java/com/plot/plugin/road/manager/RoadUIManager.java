package com.plot.plugin.road.manager;

import com.plot.core.model.Shape;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.overlay.RoadCanvasSelectionController;
import com.plot.plugin.road.overlay.RoadJunctionOverlayController;
import com.plot.plugin.road.overlay.RoadJunctionOverlayEntry;
import com.plot.plugin.road.overlay.RoadOverlayCompositor;
import com.plot.plugin.road.overlay.RoadOverlayController;
import com.plot.plugin.road.overlay.RoadOverlayEntry;
import com.plot.plugin.road.repair.RoadRepairDiagnosisCache;
import com.plot.plugin.road.ui.RoadAdoptPanel;
import com.plot.plugin.road.ui.RoadAutoRepairUi;
import com.plot.plugin.road.ui.RoadBuildPanel;
import com.plot.plugin.road.ui.RoadDefaultParamsPanel;
import com.plot.plugin.road.ui.RoadEdgeListPanel;
import com.plot.plugin.road.ui.RoadEditPanel;
import com.plot.plugin.road.ui.RoadGeneratePanel;
import com.plot.plugin.road.ui.RoadJunctionPanel;
import com.plot.plugin.road.ui.RoadNodePropertyPanel;
import com.plot.plugin.road.ui.RoadPathOverviewPanel;
import com.plot.plugin.road.ui.RoadIntersectionDetailPanel;
import com.plot.plugin.road.ui.RoadIntersectionListPanel;
import com.plot.plugin.road.ui.RoadPathPanel;
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
 * 道路系统 ImGui 界面编排（路径 / 编辑 / 生成）。
 */
public final class RoadUIManager implements RoadJunctionPropertyProvider {
    private final RoadUiContext ctx;
    private final RoadToolbarPanel toolbarPanel;
    private final RoadPathPanel pathPanel;
    private final RoadEditPanel editPanel;
    private final RoadBuildPanel buildPanel;
    private final RoadEdgeListPanel edgeListPanel;
    private final RoadJunctionPanel junctionPanel;
    private final RoadNodePropertyPanel nodePropertyPanel;

    private List<RoadOverlayEntry> overlayEntries = List.of();
    private List<RoadJunctionOverlayEntry> junctionOverlayEntries = List.of();
    private long overlaySnapshotRevision = -1L;
    private String overlaySelectionKey = "";
    private final RoadCanvasSelectionController canvasSelectionController = new RoadCanvasSelectionController();

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

        RoadDefaultParamsPanel defaultParamsPanel = new RoadDefaultParamsPanel(ctx);
        this.edgeListPanel = new RoadEdgeListPanel(ctx);
        this.junctionPanel = new RoadJunctionPanel(ctx);
        this.nodePropertyPanel = new RoadNodePropertyPanel(ctx);
        RoadPathOverviewPanel overviewPanel = new RoadPathOverviewPanel(ctx);
        RoadAdoptPanel adoptPanel = new RoadAdoptPanel(ctx);
        RoadIntersectionListPanel intersectionListPanel = new RoadIntersectionListPanel(ctx);
        RoadIntersectionDetailPanel intersectionDetailPanel = new RoadIntersectionDetailPanel(ctx);
        this.editPanel = new RoadEditPanel(ctx, defaultParamsPanel);
        RoadGeneratePanel generatePanel = new RoadGeneratePanel(ctx);

        this.toolbarPanel = new RoadToolbarPanel(ctx);
        this.pathPanel = new RoadPathPanel(
            ctx,
            adoptPanel,
            edgeListPanel,
            overviewPanel,
            intersectionDetailPanel,
            intersectionListPanel);
        this.buildPanel = new RoadBuildPanel(generatePanel);
    }

    public RoadUiContext context() {
        return ctx;
    }

    public List<RoadOverlayEntry> overlayEntries() {
        return overlayEntries;
    }

    public List<RoadJunctionOverlayEntry> junctionOverlayEntries() {
        return junctionOverlayEntries;
    }

    public void render() {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }

        ctx.roadListRename().tickFrame();
        ctx.previewManager().tickPreviewJob();

        buildPanel.renderProfileEditorWindow(ctx.networkManager().getNetwork());

        toolbarPanel.render();

        RoadUiTab pendingTab = ctx.pendingTab();

        if (ImGui.beginTabBar("##road_tabs", ImGuiTabBarFlags.None)) {
            renderTab(RoadUiTab.PATH, "plugin.road.tab.path", pendingTab, pathPanel::render);
            renderTab(RoadUiTab.EDIT, "plugin.road.tab.edit", pendingTab, editPanel::render);
            renderTab(RoadUiTab.GENERATE, "plugin.road.tab.generate", pendingTab, this::renderBuildTab);
            ImGui.endTabBar();
        }

        if (pendingTab != null) {
            ctx.clearPendingTab();
        }

        refreshOverlaySnapshotIfStale();
        tickCanvasSelection();
        if (ctx.toolManager().getPathPickSession().isActive()) {
            ctx.toolManager().tick();
        }
    }

    /** 拾取完成并自动认领道路后的 UI 反馈。 */
    public void onPathsPicked() {
        RoadRepairDiagnosisCache.invalidate();
        LinkedHashSet<String> roadIds = ctx.networkManager().getSelectedRoadIds();
        if (!roadIds.isEmpty()) {
            ctx.networkManager().selectRoad(roadIds.getFirst(), false);
        }
        ctx.requestOverlayRefresh();
    }

    /** 画布叠加层渲染前：按路网 revision / 选择态刷新 snapshot，避免晚一帧。 */
    public void refreshOverlayForCanvas() {
        refreshOverlaySnapshotIfStale();
    }

    /**
     * 插件 UI 渲染后补绘：画布先于插件面板绘制，宽度/横断面滑条变更需前景层覆盖。
     */
    public void renderDeferredOverlay() {
        if (!ctx.consumeOverlayForegroundDirty() || !CanvasAccess.isPresent()) {
            return;
        }
        Canvas canvas = CanvasAccess.get();
        RoadOverlayCompositor.renderForeground(canvas, canvas.getCamera(), overlayEntries);
    }

    private void refreshOverlaySnapshotIfStale() {
        if (!ctx.isRoadOverlayVisible()) {
            overlayEntries = List.of();
            junctionOverlayEntries = List.of();
            overlaySnapshotRevision = -1L;
            overlaySelectionKey = "";
            return;
        }
        long revision = ctx.networkManager().getNetworkRevision();
        String selectionKey = overlaySelectionKey();
        if (revision == overlaySnapshotRevision
                && selectionKey.equals(overlaySelectionKey)
                && !ctx.isOverlayForegroundDirty()) {
            return;
        }
        captureOverlaySnapshot();
        overlaySnapshotRevision = revision;
        overlaySelectionKey = selectionKey;
    }

    private void captureOverlaySnapshot() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        boolean pickActive = ctx.toolManager().getPathPickSession().isActive();
        List<Shape> candidates = pickActive ? ctx.toolManager().getPickOverlayPaths() : List.of();
        LinkedHashSet<String> selectedRoadIds = ctx.networkManager().getSelectedRoadIds();
        String primaryRoadId = selectedRoadIds.isEmpty() ? null : selectedRoadIds.getFirst();
        overlayEntries = RoadOverlayController.snapshot(
            network,
            ctx.networkManager().getConfig(),
            ctx.host().coordinates(),
            ctx.networkManager().getNetworkRevision(),
            selectedRoadIds,
            primaryRoadId,
            candidates,
            pickActive,
            Set.of());
        junctionOverlayEntries = RoadJunctionOverlayController.snapshot(
            network,
            ctx.networkManager().getNetworkBuilder(),
            ctx.networkManager().getSelectedNodeId());
    }

    private String overlaySelectionKey() {
        LinkedHashSet<String> selectedRoadIds = ctx.networkManager().getSelectedRoadIds();
        boolean pickActive = ctx.toolManager().getPathPickSession().isActive();
        return selectedRoadIds + "|"
            + ctx.networkManager().getSelectedNodeId() + "|"
            + pickActive + "|"
            + ctx.toolManager().getPickOverlayPaths().size();
    }

    private void tickCanvasSelection() {
        if (!CanvasAccess.isPresent()) {
            canvasSelectionController.resetPointer();
            return;
        }
        boolean pickActive = ctx.toolManager().getPathPickSession().isActive();
        if (ctx.isRoadOverlayVisible()) {
            captureOverlaySnapshot();
        }
        canvasSelectionController.tick(
            CanvasAccess.get(),
            ctx.host().appState(),
            ctx.networkManager(),
            overlayEntries,
            junctionOverlayEntries,
            pickActive,
            ctx::isRoadOverlayVisible);
        if (pickActive || ctx.isRoadOverlayVisible()) {
            ctx.requestOverlayRefresh();
        }
    }

    private void renderTab(RoadUiTab tab, String labelKey, RoadUiTab pendingTab, Runnable body) {
        int flags = pendingTab == tab ? ImGuiTabItemFlags.SetSelected : ImGuiTabItemFlags.None;
        PluginTabScrollUi.renderTab(labelKey, flags, "##road_tab_" + tab.name().toLowerCase(), body);
    }

    private void renderBuildTab() {
        String profileEdgeId = ctx.consumePendingProfileEdgeId();
        if (profileEdgeId != null && !profileEdgeId.isBlank()) {
            buildPanel.openProfileForEdge(profileEdgeId);
        }
        buildPanel.render();
    }

    public void renderDeferredModals() {
        edgeListPanel.renderDeleteConfirmPopup();
        buildPanel.renderBuildConfirmPopup();
        buildPanel.renderUniformElevationConfirmPopup();
        pathPanel.renderDeferredModals();
    }

    /**
     * 离开道路插件 Tab 时清理瞬时 UI 状态（不持久化路网）。
     */
    public void onDeactivate() {
        ctx.toolManager().cancel();
        ctx.cancelPreviewJobSilently();
        ctx.previewManager().clearPreview();
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
