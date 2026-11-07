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
import com.plot.plugin.road.ui.ComplexJunctionControls;
import com.plot.plugin.road.ui.RoadAdoptPanel;
import com.plot.plugin.road.ui.RoadAutoRepairUi;
import com.plot.plugin.road.ui.RoadBuildPanel;
import com.plot.plugin.road.ui.RoadCrossSectionPanel;
import com.plot.plugin.road.ui.RoadDefaultParamsPanel;
import com.plot.plugin.road.ui.RoadEdgeListPanel;
import com.plot.plugin.road.ui.RoadGeneratePanel;
import com.plot.plugin.road.ui.RoadJunctionPanel;
import com.plot.plugin.road.ui.RoadNodePropertyPanel;
import com.plot.plugin.road.ui.RoadPathOverviewPanel;
import com.plot.plugin.road.ui.RoadIntersectionDetailPanel;
import com.plot.plugin.road.ui.RoadIntersectionListPanel;
import com.plot.plugin.road.ui.RoadIntersectionPropertySupport;
import com.plot.plugin.road.ui.RoadPathPanel;
import com.plot.plugin.road.ui.RoadPresetCards;
import com.plot.plugin.road.ui.RoadToolbarPanel;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.ui.RoadUiTab;
import com.plot.plugin.road.ui.RoadVerticalProfilePanel;
import com.plot.plugin.road.ui.TunnelStyleControls;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorHost;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorKind;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.ui.PluginTabScrollUi;
import com.plot.ui.canvas.Canvas;
import com.plot.ui.canvas.CanvasAccess;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiTabItemFlags;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 道路系统 ImGui 界面编排（路线 / 横断面 / 纵断面 / 建造）。
 */
public final class RoadUIManager implements RoadJunctionPropertyProvider {
    private final RoadUiContext ctx;
    private final RoadToolbarPanel toolbarPanel;
    private final RoadPathPanel pathPanel;
    private final RoadCrossSectionPanel crossSectionPanel;
    private final RoadVerticalProfilePanel verticalProfilePanel;
    private final RoadBuildPanel buildPanel;
    private final RoadEdgeListPanel edgeListPanel;
    private final RoadJunctionPanel junctionPanel;
    private final RoadNodePropertyPanel nodePropertyPanel;

    private List<RoadOverlayEntry> overlayEntries = List.of();
    private List<RoadJunctionOverlayEntry> junctionOverlayEntries = List.of();
    private long overlaySnapshotRevision = -1L;
    private String overlaySelectionKey = "";
    private int overlayProjectionFingerprint = 0;
    private final RoadCanvasSelectionController canvasSelectionController = new RoadCanvasSelectionController();
    private final RoadFloatingEditorHost floatingEditorHost = new RoadFloatingEditorHost();
    /** 脏草稿挡住切 Tab 后，下一帧强制把 ImGui 选回离开前的 Tab。 */
    private boolean forceReselectBlockedTab;
    private RoadUiTab blockedReturnTab = RoadUiTab.ROUTE;

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
        RoadPathOverviewPanel overviewPanel = new RoadPathOverviewPanel(ctx);
        RoadAdoptPanel adoptPanel = new RoadAdoptPanel(ctx);
        RoadIntersectionListPanel intersectionListPanel = new RoadIntersectionListPanel(ctx);
        RoadIntersectionDetailPanel intersectionDetailPanel = new RoadIntersectionDetailPanel(ctx);
        this.nodePropertyPanel = new RoadNodePropertyPanel(ctx, intersectionDetailPanel);
        this.crossSectionPanel = new RoadCrossSectionPanel(ctx, defaultParamsPanel, floatingEditorHost);
        this.verticalProfilePanel = new RoadVerticalProfilePanel(ctx);
        RoadGeneratePanel generatePanel = new RoadGeneratePanel(ctx);

        this.toolbarPanel = new RoadToolbarPanel(ctx);
        this.pathPanel = new RoadPathPanel(
            ctx,
            adoptPanel,
            edgeListPanel,
            overviewPanel,
            intersectionDetailPanel,
            intersectionListPanel,
            junctionPanel);
        this.buildPanel = new RoadBuildPanel(generatePanel);
    }

    public RoadUiContext context() {
        return ctx;
    }

    public void render() {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }

        RoadUiTab tabAtFrameStart = ctx.activeTab();
        ctx.roadListRename().tickFrame();
        ctx.previewManager().tickPreviewJob();
        ctx.previewManager().tickTerrainPrefetch();
        ctx.previewManager().tickTerrainProfileJob();

        verticalProfilePanel.renderProfileEditorWindow(ctx.networkManager().getNetwork());
        floatingEditorHost.renderAll(ctx);
        RoadPresetCards.renderFloatingWindow(ctx);
        TunnelStyleControls.renderFloatingWindow(ctx);
        ComplexJunctionControls.renderFloatingWindow(ctx);

        RoadUiTab pendingTab = ctx.pendingTab();
        var pendingNav = ctx.pendingNavigation();
        if (pendingNav != null && pendingTab != null) {
            // 同标签换路/开编辑器也要先过草稿检查；失败则保持原标签与原选择。
            if (tryBlockLeave(tabAtFrameStart)) {
                ctx.clearPendingNavigation();
                pendingTab = null;
                forceReselectBlockedTab = true;
                blockedReturnTab = tabAtFrameStart;
            } else {
                commitNavigation(pendingNav);
                ctx.setActiveTab(pendingTab);
            }
        }

        RoadUiTab selectFlagTab = forceReselectBlockedTab
            ? blockedReturnTab
            : pendingTab;
        forceReselectBlockedTab = false;

        toolbarPanel.render();

        if (ImGui.beginTabBar("##road_tabs", ImGuiTabBarFlags.None)) {
            renderTab(RoadUiTab.ROUTE, "plugin.road.tab.route", selectFlagTab, pathPanel::render);
            renderTab(
                RoadUiTab.CROSS_SECTION,
                "plugin.road.tab.cross_section",
                selectFlagTab,
                crossSectionPanel::render);
            renderTab(
                RoadUiTab.VERTICAL_PROFILE,
                "plugin.road.tab.vertical_profile",
                selectFlagTab,
                verticalProfilePanel::render);
            renderTab(RoadUiTab.BUILD, "plugin.road.tab.build", selectFlagTab, buildPanel::render);
            ImGui.endTabBar();
        }

        if (pendingTab != null) {
            ctx.clearPendingNavigation();
        }

        // ImGui 原生点选标签：仅草稿门禁拦截；不得关闭本帧导航刚打开的干净浮动窗。
        if (tabAtFrameStart != ctx.activeTab() && tryBlockLeave(tabAtFrameStart)) {
            ctx.setActiveTab(tabAtFrameStart);
            forceReselectBlockedTab = true;
            blockedReturnTab = tabAtFrameStart;
        }

        ensureOverlaySnapshot();
        String selectionKeyBefore = overlaySelectionKey;
        tickCanvasSelection();
        if (ctx.activeTab() == RoadUiTab.CROSS_SECTION
                && !overlaySelectionKey().equals(selectionKeyBefore)) {
            crossSectionPanel.reconcileRoadScope();
        }
        if (ctx.toolManager().getPathPickSession().isActive()) {
            ctx.toolManager().tick();
        }
    }

    /**
     * 草稿检查通过后统一提交：校验目标 → 更新选择 → 打开/聚焦编辑器。
     * 调用前不得改写选择；被拒绝时由 {@link RoadUiContext#clearPendingNavigation()} 清除全部意图。
     */
    private void commitNavigation(com.plot.plugin.road.ui.RoadUiNavigationRequest request) {
        if (request == null) {
            return;
        }
        RoadNetwork network = ctx.networkManager().getNetwork();
        String roadId = request.roadId();
        boolean roadOk = roadId != null && !roadId.isBlank() && network.getRoad(roadId) != null;
        if (roadOk) {
            ctx.networkManager().selectRoad(roadId, false);
        }
        if (request.crossingId() != null && !request.crossingId().isBlank()) {
            ctx.networkManager().setSelectedCrossingId(request.crossingId());
        }
        String edgeId = request.edgeId();
        boolean edgeOk = edgeId != null && !edgeId.isBlank() && network.getEdge(edgeId) != null;
        if (edgeOk) {
            if (request.targetTab() == RoadUiTab.VERTICAL_PROFILE) {
                verticalProfilePanel.openProfileForEdge(edgeId);
            } else {
                ctx.networkManager().setPrimarySelectedEdge(edgeId);
            }
        }
        RoadFloatingEditorKind kind = request.editorKind();
        if (kind != null && roadOk) {
            floatingEditorHost.openOrFocus(ctx, kind, roadId);
        }
    }

    /**
     * 任意工作区离开时，若浮动编辑器/隧道草稿脏则拦截。
     * 草稿归属编辑器会话，不绑定某一主标签。
     */
    private boolean tryBlockLeave(RoadUiTab leavingTab) {
        return crossSectionPanel.tryLeaveEditors();
    }

    public void onPathsPicked() {
        RoadRepairDiagnosisCache.invalidate();
        LinkedHashSet<String> roadIds = ctx.networkManager().getSelectedRoadIds();
        if (!roadIds.isEmpty()) {
            ctx.networkManager().selectRoad(roadIds.getFirst(), false);
        }
        ctx.requestOverlayRefresh();
    }

    public void renderCanvasOverlay() {
        if (!ctx.isRoadOverlayVisible() || !CanvasAccess.isPresent()) {
            return;
        }
        ensureOverlaySnapshot();
        Canvas canvas = CanvasAccess.get();
        RoadOverlayCompositor.renderOnCanvas(
            canvas,
            canvas.getCamera(),
            overlayEntries,
            junctionOverlayEntries);
    }

    private void ensureOverlaySnapshot() {
        if (!ctx.isRoadOverlayVisible()) {
            overlayEntries = List.of();
            junctionOverlayEntries = List.of();
            overlaySnapshotRevision = -1L;
            overlaySelectionKey = "";
            overlayProjectionFingerprint = 0;
            ctx.consumeOverlayForegroundDirty();
            return;
        }
        long revision = ctx.networkManager().getNetworkRevision();
        String selectionKey = overlaySelectionKey();
        int projectionFingerprint = currentOverlayProjectionFingerprint();
        if (revision == overlaySnapshotRevision
                && selectionKey.equals(overlaySelectionKey)
                && projectionFingerprint == overlayProjectionFingerprint
                && !ctx.isOverlayForegroundDirty()) {
            return;
        }
        captureOverlaySnapshot();
        overlaySnapshotRevision = revision;
        overlaySelectionKey = selectionKey;
        overlayProjectionFingerprint = projectionFingerprint;
        ctx.consumeOverlayForegroundDirty();
    }

    private int currentOverlayProjectionFingerprint() {
        try {
            var coordinates = ctx.host().coordinates();
            if (coordinates == null) {
                return 0;
            }
            var snapshot = coordinates.captureProjection();
            return snapshot != null ? snapshot.uiFingerprint() : 0;
        } catch (RuntimeException ignored) {
            return 0;
        }
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
            Set.of(),
            ctx.showSlopeBatterOverlay().get());
        junctionOverlayEntries = RoadJunctionOverlayController.snapshot(
            network,
            ctx.networkManager().getNetworkBuilder(),
            ctx.networkManager().getConfig(),
            ctx.networkManager().getSelectedCrossingId(),
            ctx.networkManager().getSelectedNodeId(),
            ctx.isJunctionFilletPreviewVisible());
    }

    private String overlaySelectionKey() {
        LinkedHashSet<String> selectedRoadIds = ctx.networkManager().getSelectedRoadIds();
        boolean pickActive = ctx.toolManager().getPathPickSession().isActive();
        var crossing = ctx.networkManager().getSelectedCrossing();
        String cornerKey = crossing != null && crossing.cornerRadius() != null
            ? String.valueOf(crossing.cornerRadius())
            : "default";
        RoadNode selectedNode = ctx.networkManager().getSelectedNode();
        if (crossing == null && selectedNode != null && selectedNode.getCornerRadius() != null) {
            cornerKey = String.valueOf(selectedNode.getCornerRadius());
        }
        return selectedRoadIds + "|"
            + ctx.networkManager().getSelectedNodeId() + "|"
            + ctx.networkManager().getSelectedCrossingId() + "|"
            + pickActive + "|"
            + ctx.toolManager().getPickOverlayPaths().size() + "|"
            + ctx.showSlopeBatterOverlay().get() + "|"
            + ctx.isJunctionFilletPreviewVisible() + "|"
            + cornerKey;
    }

    private void tickCanvasSelection() {
        if (!CanvasAccess.isPresent()) {
            canvasSelectionController.resetPointer();
            return;
        }
        boolean pickActive = ctx.toolManager().getPathPickSession().isActive();
        ensureOverlaySnapshot();
        String keyBefore = overlaySelectionKey;
        canvasSelectionController.tick(
            CanvasAccess.get(),
            ctx.host().appState(),
            ctx.networkManager(),
            overlayEntries,
            junctionOverlayEntries,
            pickActive,
            ctx::isRoadOverlayVisible);
        if (!overlaySelectionKey().equals(keyBefore)) {
            ensureOverlaySnapshot();
        }
    }

    private void renderTab(RoadUiTab tab, String labelKey, RoadUiTab selectFlagTab, Runnable body) {
        int flags = selectFlagTab == tab ? ImGuiTabItemFlags.SetSelected : ImGuiTabItemFlags.None;
        if (!ImGui.beginTabItem(PlotI18n.tr(labelKey), flags)) {
            return;
        }
        try {
            if (tab != ctx.activeTab() && tryBlockLeave(ctx.activeTab())) {
                forceReselectBlockedTab = true;
                blockedReturnTab = ctx.activeTab();
                return;
            }
            ctx.setActiveTab(tab);
            PluginTabScrollUi.renderScrollBody(
                "##road_tab_" + tab.name().toLowerCase(), body);
        } finally {
            ImGui.endTabItem();
        }
    }

    public void renderDeferredModals() {
        edgeListPanel.renderDeleteConfirmPopup();
        buildPanel.renderBuildConfirmPopup();
        buildPanel.renderDeferredModals();
        verticalProfilePanel.renderDeferredModals();
    }

    public void onDeactivate() {
        crossSectionPanel.finishPendingEdits();
        ctx.toolManager().cancel();
        ctx.cancelPreviewJobSilently();
        ctx.previewManager().clearPreview();
        ctx.previewManager().clearTerrainProfileCache();
        ctx.clearTransientUiState();
        RoadAutoRepairUi.invalidateCache();
    }

    @Override
    public boolean hasJunctionPropertyContent() {
        return RoadIntersectionPropertySupport.hasIntersectionSelection(ctx.networkManager());
    }

    @Override
    public void renderJunctionPropertySection() {
        nodePropertyPanel.renderPropertySection(junctionPanel);
    }

    @Override
    public String getPropertySectionTitleKey() {
        if (ctx.networkManager().getSelectedCrossing() != null
                || ctx.networkManager().getSelectedStackCrossing() != null) {
            return "panel.plot.road_junction";
        }
        RoadNode node = ctx.networkManager().getSelectedNode();
        if (node != null && node.isJunction()) {
            return "panel.plot.road_junction";
        }
        return "panel.plot.road_node";
    }
}
