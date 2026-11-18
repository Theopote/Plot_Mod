package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.profile.ProfileChartInteraction;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadGradeSeparationEvaluation;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.profile.ProfileRenderCache;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.profile.RoadProfileRoadList;
import com.plot.plugin.road.profile.RoadProfileRoadNavigator;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.RoadProfileIntersectionResolver;
import com.plot.plugin.road.profile.RoadProfileIntersectionWarningResolver;
import com.plot.plugin.road.profile.edit.ProfileEditSession;
import com.plot.plugin.road.profile.edit.ProfileEditorWindowSession;
import com.plot.plugin.road.profile.edit.VoxelProfilePreviewSlot;
import com.plot.plugin.road.ui.profile.VerticalProfileChartCache;
import com.plot.plugin.road.ui.profile.VerticalProfileChartController;
import com.plot.plugin.road.ui.profile.VerticalProfileEditorBody;
import com.plot.plugin.road.ui.profile.VerticalProfileEditorChrome;
import com.plot.plugin.road.ui.profile.VerticalProfileIntersectionPanel;
import com.plot.plugin.road.ui.profile.VerticalProfileVoxelPreview;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.world.World;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadWorldElevationBounds;
import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;
import com.plot.plugin.road.vertical.FlatVerticalIntent;
import com.plot.plugin.road.vertical.FlatVerticalIntentSupport;
import com.plot.plugin.road.vertical.RoadVerticalAlignmentResolver;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorShell;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 纵断面浮动编辑器：钉顶剖面图 + 左轨控制点/交点 + 可滚详情。
 */
final class VerticalProfileEditor {

    private final ImBoolean editorWindowOpen = new ImBoolean(false);
    private String editorRoadId = "";
    private boolean focusEditorOnOpen = false;
    /** 上一帧实测底部高度；首帧用 estimateMinFooterHeight()。 */
    private float lastFooterHeight;

    private boolean externalTopologyConflict;
    private final ProfileEditorState editorState = new ProfileEditorState();
    private final ProfileEditSession profileEditSession = new ProfileEditSession();
    private final AdaptiveProfileControls adaptiveProfileControls = new AdaptiveProfileControls();
    private final FlatProfileControls flatProfileControls = new FlatProfileControls();
    private final VerticalProfileChartCache chartCache = new VerticalProfileChartCache();
    /** Package-visible for compile-cancellation tests. */
    final VerticalProfileVoxelPreview voxelPreview = new VerticalProfileVoxelPreview(
        new VerticalProfileVoxelPreview.Host() {
            @Override
            public ProfileEditorWindowSession windowSession() {
                return windowSession;
            }

            @Override
            public ProfileEditSession editSession() {
                return profileEditSession;
            }

            @Override
            public RoadProfileChartData resolveChartData(
                    RoadUiContext ctx, RoadNetwork network, Road road) {
                return VerticalProfileEditor.this.resolveChartData(ctx, network, road);
            }

            @Override
            public List<RoadProfileIntersection> resolveIntersections(
                    RoadUiContext ctx,
                    RoadNetwork network,
                    Road road,
                    RoadSystemConfig config,
                    RoadProfileChartData chart,
                    boolean allowCache) {
                return VerticalProfileEditor.this.resolveIntersections(
                    ctx, network, road, config, chart, allowCache);
            }

            @Override
            public RoadGenerator gradeSeparationGenerator(
                    RoadUiContext ctx, RoadSystemConfig config, long cacheKey) {
                return VerticalProfileEditor.this.gradeSeparationGenerator(ctx, config, cacheKey);
            }

            @Override
            public RoadElevationBounds resolveElevationBounds(RoadUiContext ctx) {
                return VerticalProfileEditor.resolveElevationBounds(ctx);
            }

            @Override
            public void onVoxelPreviewReady() {
                chartCache.invalidateRenderCache();
            }
        });
    private final VerticalProfileEditorChrome chrome = new VerticalProfileEditorChrome(
        new VerticalProfileEditorChrome.Host() {
            @Override
            public ProfileEditorWindowSession windowSession() {
                return windowSession;
            }

            @Override
            public String editorRoadId() {
                return editorRoadId;
            }

            @Override
            public ProfileEditorState editorState() {
                return editorState;
            }

            @Override
            public ProfileEditSession profileEditSession() {
                return profileEditSession;
            }

            @Override
            public VerticalProfileVoxelPreview voxelPreview() {
                return voxelPreview;
            }

            @Override
            public int selectedIntersectionIndex() {
                return selectedIntersectionIndex;
            }

            @Override
            public List<RoadProfileIntersection> cachedIntersections() {
                return cachedIntersections;
            }

            @Override
            public boolean closeConfirmPending() {
                return closeConfirmPending;
            }

            @Override
            public void setCloseConfirmPending(boolean pending) {
                closeConfirmPending = pending;
            }

            @Override
            public ImBoolean editorWindowOpen() {
                return editorWindowOpen;
            }

            @Override
            public boolean externalTopologyConflict() {
                return externalTopologyConflict;
            }

            @Override
            public float lastFooterHeight() {
                return lastFooterHeight;
            }

            @Override
            public void setLastFooterHeight(float height) {
                lastFooterHeight = height;
            }

            @Override
            public void selectEditorRoad(RoadUiContext ctx, String roadId, boolean sync) {
                VerticalProfileEditor.this.selectEditorRoad(ctx, roadId, sync);
            }

            @Override
            public void undoEditorSession(RoadUiContext ctx) {
                VerticalProfileEditor.this.undoEditorSession(ctx);
            }

            @Override
            public void redoEditorSession(RoadUiContext ctx) {
                VerticalProfileEditor.this.redoEditorSession(ctx);
            }

            @Override
            public void requestVoxelPreview(RoadUiContext ctx, Road road) {
                voxelPreview.request(ctx, road);
            }

            @Override
            public void restoreWindowSession() {
                VerticalProfileEditor.this.restoreWindowSession();
            }

            @Override
            public void requestEditorClose(
                    RoadUiContext ctx, RoadNetwork liveNetwork, boolean forceIfClean) {
                VerticalProfileEditor.this.requestEditorClose(ctx, liveNetwork, forceIfClean);
            }

            @Override
            public void applyWindowSessionConfirm(RoadUiContext ctx, RoadNetwork liveNetwork) {
                VerticalProfileEditor.this.applyWindowSessionConfirm(ctx, liveNetwork);
            }

            @Override
            public void discardWindowSession() {
                VerticalProfileEditor.this.discardWindowSession();
            }

            @Override
            public void discardAndReloadWindowSession(RoadUiContext ctx, RoadNetwork liveNetwork) {
                VerticalProfileEditor.this.discardAndReloadWindowSession(ctx, liveNetwork);
            }
        });
    private final VerticalProfileIntersectionPanel intersectionPanel =
        new VerticalProfileIntersectionPanel(new VerticalProfileIntersectionPanel.Host() {
            @Override
            public ProfileEditorWindowSession windowSession() {
                return windowSession;
            }

            @Override
            public ProfileEditSession profileEditSession() {
                return profileEditSession;
            }

            @Override
            public ProfileEditorState editorState() {
                return editorState;
            }

            @Override
            public int selectedIntersectionIndex() {
                return selectedIntersectionIndex;
            }

            @Override
            public int activeIntersectionDragIndex() {
                return activeIntersectionDragIndex;
            }

            @Override
            public ProfileChartInteraction.IntersectionDragTarget activeIntersectionDragTarget() {
                return activeIntersectionDragTarget;
            }

            @Override
            public void recordUndoPointBeforeEdit() {
                VerticalProfileEditor.this.recordUndoPointBeforeEdit();
            }

            @Override
            public void notifyWorkingProfileChanged() {
                VerticalProfileEditor.this.notifyWorkingProfileChanged();
            }

            @Override
            public void setProfileAutoFixMessage(String message) {
                editorState.profileAutoFixMessage = message != null ? message : "";
            }

            @Override
            public RoadElevationBounds resolveElevationBounds(RoadUiContext ctx) {
                return VerticalProfileEditor.resolveElevationBounds(ctx);
            }

            @Override
            public int findPviIndexAtStation(RoadVerticalAlignment alignment, double station) {
                return VerticalProfileChartController.findPviIndexAtStation(alignment, station);
            }

            @Override
            public void selectEditorRoad(RoadUiContext ctx, String roadId, boolean sync) {
                VerticalProfileEditor.this.selectEditorRoad(ctx, roadId, sync);
            }
        });
    private final VerticalProfileEditorBody body = new VerticalProfileEditorBody(
        new VerticalProfileEditorBody.Host() {
            @Override
            public ProfileEditorWindowSession windowSession() {
                return windowSession;
            }

            @Override
            public ProfileEditSession profileEditSession() {
                return profileEditSession;
            }

            @Override
            public ProfileEditorState editorState() {
                return editorState;
            }

            @Override
            public AdaptiveProfileControls adaptiveProfileControls() {
                return adaptiveProfileControls;
            }

            @Override
            public FlatProfileControls flatProfileControls() {
                return flatProfileControls;
            }

            @Override
            public VerticalProfileChartCache chartCache() {
                return chartCache;
            }

            @Override
            public VerticalProfileVoxelPreview voxelPreview() {
                return voxelPreview;
            }

            @Override
            public VerticalProfileIntersectionPanel intersectionPanel() {
                return intersectionPanel;
            }

            @Override
            public float playheadStation() {
                return playheadStation;
            }

            @Override
            public void setPlayheadStation(float station) {
                playheadStation = station;
            }

            @Override
            public int selectedIntersectionIndex() {
                return selectedIntersectionIndex;
            }

            @Override
            public void setSelectedIntersectionIndex(int index) {
                selectedIntersectionIndex = index;
            }

            @Override
            public int activeIntersectionDragIndex() {
                return activeIntersectionDragIndex;
            }

            @Override
            public void setActiveIntersectionDragIndex(int index) {
                activeIntersectionDragIndex = index;
            }

            @Override
            public ProfileChartInteraction.IntersectionDragTarget activeIntersectionDragTarget() {
                return activeIntersectionDragTarget;
            }

            @Override
            public void setActiveIntersectionDragTarget(
                    ProfileChartInteraction.IntersectionDragTarget target) {
                activeIntersectionDragTarget = target;
            }

            @Override
            public void recordUndoPointBeforeEdit() {
                VerticalProfileEditor.this.recordUndoPointBeforeEdit();
            }

            @Override
            public void notifyWorkingProfileChanged() {
                VerticalProfileEditor.this.notifyWorkingProfileChanged();
            }

            @Override
            public void propagateJunctionGrades(RoadUiContext ctx, RoadNetwork network, Road road) {
                VerticalProfileEditor.this.propagateJunctionGrades(ctx, network, road);
            }

            @Override
            public void ensureEditorRoadAlignment(
                    RoadUiContext ctx,
                    RoadNetwork network,
                    Road road,
                    RoadProfileChartData chartData) {
                VerticalProfileEditor.this.ensureEditorRoadAlignment(ctx, network, road, chartData);
            }

            @Override
            public FlatElevationProfileOverlay resolveFlatOverlay(Road road) {
                return VerticalProfileEditor.this.resolveFlatOverlay(road);
            }

            @Override
            public ProfileRenderCache resolveRenderCache(
                    RoadUiContext ctx,
                    RoadNetwork network,
                    Road road,
                    RoadProfileChartData chartData,
                    FlatElevationProfileOverlay flatOverlay,
                    List<RoadProfileIntersection> intersections,
                    VoxelProfilePreviewSlot voxelSlot) {
                return VerticalProfileEditor.this.resolveRenderCache(
                    ctx, network, road, chartData, flatOverlay, intersections, voxelSlot);
            }

            @Override
            public RoadProfileChartData resolveChartData(
                    RoadUiContext ctx, RoadNetwork network, Road road) {
                return VerticalProfileEditor.this.resolveChartData(ctx, network, road);
            }

            @Override
            public List<RoadProfileIntersection> resolveIntersections(
                    RoadUiContext ctx,
                    RoadNetwork network,
                    Road road,
                    RoadSystemConfig config,
                    RoadProfileChartData chart,
                    boolean allowCache) {
                return VerticalProfileEditor.this.resolveIntersections(
                    ctx, network, road, config, chart, allowCache);
            }

            @Override
            public RoadElevationBounds resolveElevationBounds(RoadUiContext ctx) {
                return VerticalProfileEditor.resolveElevationBounds(ctx);
            }

            @Override
            public RoadProfileChartData resolveEffectiveChartData(
                    Road road, RoadProfileChartData base) {
                return VerticalProfileEditor.this.resolveEffectiveChartData(road, base);
            }

            @Override
            public boolean isVoxelBuildProfileStale(Road road) {
                return VerticalProfileEditor.this.isVoxelBuildProfileStale(road);
            }

            @Override
            public boolean isChartDerivedDataStale(Road road) {
                return VerticalProfileEditor.this.isChartDerivedDataStale(road);
            }

            @Override
            public double resolveBootstrapEndpointElevation(
                    RoadNetwork network,
                    Road road,
                    RoadProfileChartData chartData,
                    double station) {
                return VerticalProfileEditor.this.resolveBootstrapEndpointElevation(
                    network, road, chartData, station);
            }
        });
    private ProfileEditorWindowSession windowSession;
    private boolean closeConfirmPending;
    private Runnable onProfileConfirmed;
    private int selectedIntersectionIndex = -1;
    private int activeIntersectionDragIndex = -1;
    private ProfileChartInteraction.IntersectionDragTarget
        activeIntersectionDragTarget =
            ProfileChartInteraction.IntersectionDragTarget.NONE;
    private String cachedIntersectionsRoadId = "";
    private long cachedIntersectionsKey = Long.MIN_VALUE;
    private List<RoadProfileIntersection> cachedIntersections = List.of();
    private RoadGenerator cachedGradeSeparationGenerator;
    private long cachedGradeSeparationGeneratorKey = Long.MIN_VALUE;
    private Function<Road, FlatElevationProfileOverlay> flatOverlayResolver = road -> null;
    /** 沿路浏览位置（canonical / 画布域）；与平面示意、剖面竖线同步。 */
    private float playheadStation;

    void clearCache() {
        chartCache.clear();
        editorState.reset();
        profileEditSession.cancelEdit();
        selectedIntersectionIndex = -1;
        activeIntersectionDragIndex = -1;
        activeIntersectionDragTarget =
            ProfileChartInteraction.IntersectionDragTarget.NONE;
        windowSession = null;
        closeConfirmPending = false;
        externalTopologyConflict = false;
        voxelPreview.clear();
        intersectionPanel.clear();
        playheadStation = 0f;
        invalidateIntersectionCache();
    }

    boolean hasUnconfirmedEditsForRoad(String roadId) {
        return editorWindowOpen.get()
            && windowSession != null
            && windowSession.hasUnconfirmedEditsForRoad(roadId);
    }

    void openEditorForEdge(RoadUiContext ctx, RoadNetwork network, String edgeId) {
        if (edgeId == null || edgeId.isBlank()) {
            return;
        }
        if (network != null) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null && edge.getRoadId() != null && !edge.getRoadId().isBlank()) {
                openEditorForRoad(ctx, edge.getRoadId());
                return;
            }
        }
        if (ctx != null) {
            ctx.status().warning(PlotI18n.tr("plugin.road.profile_editor_edge_without_road"));
        }
    }

    void openEditorForRoad(RoadUiContext ctx, String roadId) {
        if (roadId == null || roadId.isBlank()) {
            return;
        }
        if (ctx != null
                && com.plot.plugin.road.vertical.NetworkElevationAuthority.isUniformFlat(
                    ctx.networkManager().getNetwork())) {
            ctx.status().warning(PlotI18n.tr("plugin.road.uniform_flat_profile_editor_blocked"));
            return;
        }
        selectEditorRoad(ctx, roadId, true);
        editorWindowOpen.set(true);
        focusEditorOnOpen = true;
    }

    void setOnAlignmentCommitted(Runnable onProfileConfirmed) {
        this.onProfileConfirmed = onProfileConfirmed;
    }

    void setFlatOverlayResolver(Function<Road, FlatElevationProfileOverlay> flatOverlayResolver) {
        this.flatOverlayResolver = flatOverlayResolver != null ? flatOverlayResolver : road -> null;
    }

    void renderEditorWindow(RoadUiContext ctx, RoadNetwork liveNetwork) {
        if (!editorWindowOpen.get()) {
            discardWindowSession();
            return;
        }
        ensureWindowSession(ctx, liveNetwork);
        if (windowSession == null) {
            editorWindowOpen.set(false);
            return;
        }
        voxelPreview.tick(ctx);
        RoadNetwork network = windowSession.editingNetwork();
        ensureEditorRoadSelection(ctx, network);

        // 与专业浮动窗共用 cascade 错开；纵断面保持可缩放、默认更大。
        RoadFloatingEditorShell.prepareResizableWindow(
            focusEditorOnOpen,
            RoadFloatingEditorShell.cascadeAfterHost(
                RoadFloatingEditorShell.CASCADE_AFTER_HOST_PROFILE),
            RoadFloatingEditorShell.PROFILE_WIDTH_OF_SCREEN,
            RoadFloatingEditorShell.PROFILE_HEIGHT_OF_SCREEN,
            RoadFloatingEditorShell.PROFILE_MIN_OF_SCREEN,
            RoadFloatingEditorShell.PROFILE_MAX_OF_SCREEN);
        focusEditorOnOpen = false;

        String windowTitle = PlotI18n.tr("plugin.road.vertical_alignment_editor_window");
        if (!ImGui.begin(
                windowTitle + "###road_profile_editor",
                editorWindowOpen,
                RoadFloatingEditorShell.RESIZABLE_WINDOW_FLAGS)) {
            handleEditorCloseRequest(ctx, liveNetwork);
            ImGui.end();
            return;
        }
        try {
            Road road = editorRoadId != null && !editorRoadId.isBlank()
                ? network.getRoad(editorRoadId)
                : null;
            if (road == null) {
                if (windowSession != null && windowSession.isDirty()) {
                    externalTopologyConflict = true;
                    chrome.renderExternalTopologyConflictBanner(ctx, liveNetwork);
                } else {
                    requestEditorClose(ctx, liveNetwork, true);
                }
                return;
            }
            chrome.renderEditorHeader(ctx, network, road);
            if (externalTopologyConflict) {
                chrome.renderExternalTopologyConflictBanner(ctx, liveNetwork);
            }
            float footerReserve = chrome.resolveFooterReserve();
            // 外层不滚动：平面示意 / scrub / 剖面图钉在上方，详情在下半区滚动。
            ImGui.beginChild(
                "##profile_editor_body",
                0f,
                -footerReserve,
                false,
                ImGuiWindowFlags.NoScrollbar);
            try {
                if (externalTopologyConflict) {
                    body.clearFooterLegend();
                    RoadUiWidgets.textWrappedColored(
                        PluginUiColors.WARNING_LIGHT,
                        PlotI18n.tr("plugin.road.profile_editor_topology_conflict_hint"));
                } else if (!RoadStationing.isStationable(network, road)) {
                    body.clearFooterLegend();
                    RoadUiWidgets.textWrappedColored(
                        PluginUiColors.HINT_GRAY,
                        PlotI18n.tr("plugin.road.profile_discontinuous_road_hint"));
                } else {
                    RoadProfileChartData chartData = resolveChartData(ctx, liveNetwork, road);
                    if (chartData == null || !chartData.canRenderChart()) {
                        body.renderMissingProfileActions(ctx, liveNetwork, road);
                    } else {
                        FlatElevationProfileOverlay flatOverlay = resolveFlatOverlay(road);
                        VerticalAlignmentProfileOverlay design =
                            profileEditSession.effectiveDesignOverlay(network, road).orElse(null);
                        body.renderInteractiveEditor(
                            ctx, liveNetwork, network, road, chartData, design, flatOverlay);
                    }
                }
            } finally {
                ImGui.endChild();
            }
            handleEditorKeyboardShortcuts(ctx);
            // 图例紧贴编辑区下方；页脚芯片/按钮再往下。
            float footerTop = ImGui.getCursorPosY();
            body.renderFooterLegend(ctx, road);
            chrome.renderEditorFooter(ctx, liveNetwork, road);
            // 按实测高度预留，仅加一行间距，避免内容区与按钮之间出现大块空白。
            lastFooterHeight = Math.max(
                VerticalProfileEditorChrome.estimateMinFooterHeight(),
                ImGui.getCursorPosY() - footerTop + ImGui.getStyle().getItemSpacingY());
            chrome.renderEditorDialogs(ctx, liveNetwork);
        } finally {
            if (!editorWindowOpen.get()) {
                handleEditorCloseRequest(ctx, liveNetwork);
            }
            ImGui.end();
        }
    }

    private void ensureWindowSession(RoadUiContext ctx, RoadNetwork liveNetwork) {
        if (liveNetwork == null) {
            return;
        }
        long liveRevision = ctx.networkManager().getNetworkRevision();
        if (windowSession == null) {
            windowSession = ProfileEditorWindowSession.open(liveNetwork, liveRevision);
            windowSession.seedSampledProfiles(ctx.networkManager().getConfig(),
                ctx.previewManager().getLastEdgeResults());
            externalTopologyConflict = false;
            return;
        }
        if (liveRevision == windowSession.openRevision()) {
            externalTopologyConflict = false;
            return;
        }
        boolean dirty = windowSession.isDirty() || profileEditSession.isDirty();
        if (!dirty) {
            reopenWindowSession(ctx, liveNetwork);
            if (editorRoadId != null && !editorRoadId.isBlank()
                    && windowSession.editingNetwork().getRoad(editorRoadId) == null) {
                List<Road> roads = RoadProfileRoadList.listStationableRoads(
                    windowSession.editingNetwork());
                if (roads.isEmpty()) {
                    editorWindowOpen.set(false);
                    discardWindowSession();
                } else {
                    selectEditorRoad(ctx, roads.getFirst().getId(), false);
                }
            }
            return;
        }
        externalTopologyConflict = true;
    }

    private void discardWindowSession() {
        if (windowSession != null) {
            windowSession.cancel();
        }
        windowSession = null;
        profileEditSession.cancelEdit();
        closeConfirmPending = false;
        externalTopologyConflict = false;
        voxelPreview.cancel();
    }

    private void discardAndReloadWindowSession(RoadUiContext ctx, RoadNetwork liveNetwork) {
        profileEditSession.cancelEdit();
        reopenWindowSession(ctx, liveNetwork);
        if (editorRoadId != null && !editorRoadId.isBlank()
                && windowSession.editingNetwork().getRoad(editorRoadId) == null) {
            List<Road> roads = RoadProfileRoadList.listStationableRoads(windowSession.editingNetwork());
            if (roads.isEmpty()) {
                editorWindowOpen.set(false);
                discardWindowSession();
                return;
            }
            selectEditorRoad(ctx, roads.getFirst().getId(), false);
        }
    }

    private void handleEditorCloseRequest(RoadUiContext ctx, RoadNetwork liveNetwork) {
        if (windowSession != null && windowSession.isDirty()) {
            editorWindowOpen.set(true);
            closeConfirmPending = true;
            return;
        }
        discardWindowSession();
    }

    private void requestEditorClose(RoadUiContext ctx, RoadNetwork liveNetwork, boolean forceIfClean) {
        if (!forceIfClean && windowSession != null && windowSession.isDirty()) {
            closeConfirmPending = true;
            return;
        }
        editorWindowOpen.set(false);
        discardWindowSession();
    }

    private void applyWindowSessionConfirm(RoadUiContext ctx, RoadNetwork liveNetwork) {
        if (confirmWindowSession(ctx, liveNetwork, true)) {
            editorWindowOpen.set(false);
            discardWindowSession();
        }
    }

    private boolean confirmWindowSession(
            RoadUiContext ctx,
            RoadNetwork liveNetwork,
            boolean notifyCommitted) {
        if (windowSession == null) {
            return true;
        }
        if (profileEditSession.isActive()) {
            Road current = windowSession.editingNetwork().getRoad(editorRoadId);
            if (current != null) profileEditSession.commitEdit(windowSession.editingNetwork(), current,
                ctx.networkManager().getConfig(), windowSession, null, this::notifyWorkingProfileChanged);
        }
        ProfileEditorWindowSession.ConfirmResult result = windowSession.confirm(
            ctx, liveNetwork, ctx.networkManager().getConfig());
        if (result == ProfileEditorWindowSession.ConfirmResult.CONFLICT) {
            ctx.status().warning(PlotI18n.tr("plugin.road.profile_editor_confirm_conflict"));
            return false;
        }
        if (result == ProfileEditorWindowSession.ConfirmResult.CONSTRAINT_CONFLICT) {
            ctx.status().warning(PlotI18n.tr("plugin.road.vertical_alignment_network_unresolved",
                windowSession.unresolvedConstraints().size()));
            return false;
        }
        if (notifyCommitted && onProfileConfirmed != null) {
            onProfileConfirmed.run();
        }
        ctx.requestOverlayRefresh();
        return true;
    }

    private void reopenWindowSession(RoadUiContext ctx, RoadNetwork liveNetwork) {
        voxelPreview.cancel();
        windowSession = ProfileEditorWindowSession.open(
            liveNetwork, ctx.networkManager().getNetworkRevision());
        windowSession.seedSampledProfiles(ctx.networkManager().getConfig(),
            ctx.previewManager().getLastEdgeResults());
        profileEditSession.cancelEdit();
        chartCache.invalidateRenderCache();
        invalidateIntersectionCache();
        chartCache.clear();
        externalTopologyConflict = false;
    }

    private void restoreWindowSession() {
        if (windowSession == null) {
            return;
        }
        voxelPreview.cancel();
        windowSession.restore();
        profileEditSession.cancelEdit();
        chartCache.invalidateRenderCache();
        resetEditorLocalState();
        invalidateIntersectionCache();
        chartCache.clear();
    }

    private void selectEditorRoad(RoadUiContext ctx, String roadId, boolean syncSelection) {
        if (roadId == null || roadId.isBlank()) {
            return;
        }
        if (Objects.equals(editorRoadId, roadId)) {
            return;
        }
        // All roads share one window transaction. Switching views must not commit
        // or discard the other roads before the network design is finished.
        if (windowSession != null && profileEditSession.isActive()) {
            Road currentRoad = windowSession.editingNetwork().getRoad(editorRoadId);
            if (currentRoad != null) profileEditSession.commitEdit(windowSession.editingNetwork(), currentRoad,
                ctx.networkManager().getConfig(), windowSession, null, this::notifyWorkingProfileChanged);
        }
        applyEditorRoadSwitch(ctx, roadId, syncSelection);
    }

    private void applyEditorRoadSwitch(RoadUiContext ctx, String roadId, boolean syncSelection) {
        profileEditSession.cancelEdit();
        chartCache.invalidateRenderCache();
        editorRoadId = roadId;
        resetEditorLocalState();
        invalidateIntersectionCache();
        chartCache.clear();
        if (windowSession != null) {
            Road road = windowSession.editingNetwork().getRoad(roadId);
            if (road != null
                    && com.plot.plugin.road.vertical.ResolvedProfileAuthority.hasStoredEditedCurve(road)
                    && !com.plot.plugin.road.vertical.ResolvedProfileAuthority.canActivateEditedProfile(
                        windowSession.editingNetwork(), road)) {
                windowSession.markTouched(road);
            }
            if (road != null) {
                RoadProfileChartData chart = ctx.previewManager().getOverviewChart(
                    windowSession.editingNetwork(), roadId);
                windowSession.ensureRoadEditorAlignment(
                    road,
                    ctx.networkManager().getConfig(),
                    ctx.previewManager().getLastEdgeResults(),
                    chart);
            }
        }
        if (syncSelection) {
            ctx.networkManager().selectRoad(roadId, false);
            ctx.requestOverlayRefresh();
        }
    }

    private void ensureEditorRoadAlignment(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadProfileChartData chartData) {
        if (windowSession == null || road == null || network == null) {
            return;
        }
        windowSession.ensureRoadEditorAlignment(
            road,
            ctx.networkManager().getConfig(),
            ctx.previewManager().getLastEdgeResults(),
            chartData);
    }

    private void ensureEditorRoadSelection(RoadUiContext ctx, RoadNetwork network) {
        List<Road> roads = RoadProfileRoadList.listStationableRoads(network);
        if (roads.isEmpty()) {
            return;
        }
        boolean keepEditorRoad = windowSession != null && windowSession.isDirty();
        if (!keepEditorRoad) {
            Road primary = ctx.networkManager().getPrimarySelectedRoad();
            if (primary != null
                    && RoadStationing.isStationable(network, primary)
                    && !Objects.equals(editorRoadId, primary.getId())) {
                selectEditorRoad(ctx, primary.getId(), false);
                return;
            }
        }
        String normalized = RoadProfileRoadNavigator.normalizeRoadId(roads, editorRoadId);
        if (normalized != null && !Objects.equals(editorRoadId, normalized)) {
            selectEditorRoad(ctx, normalized, false);
        }
    }

    private FlatElevationProfileOverlay resolveFlatOverlay(Road road) {
        return flatOverlayResolver.apply(road);
    }

    private ProfileRenderCache resolveRenderCache(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadProfileChartData chartData,
            FlatElevationProfileOverlay flatOverlay,
            List<RoadProfileIntersection> intersections,
            VoxelProfilePreviewSlot voxelSlot) {
        return chartCache.resolveRenderCache(
            ctx, road, editorRoadId, chartData, flatOverlay, intersections, voxelSlot);
    }

    private RoadProfileChartData resolveChartData(RoadUiContext ctx, RoadNetwork network, Road road) {
        return chartCache.resolveChartData(ctx, network, road, windowSession);
    }

    /** Package-visible for unit tests: chart fallback must match road + revisions + length. */
    static boolean matchesChartFallbackIdentity(
            String roadId,
            String cachedRoadId,
            long networkRevision,
            long cachedNetworkRevision,
            long terrainRevision,
            long cachedTerrainRevision,
            double roadLength,
            double cachedTotalStation) {
        return VerticalProfileChartCache.matchesChartFallbackIdentity(
                roadId,
            cachedRoadId,
                networkRevision,
            cachedNetworkRevision,
                terrainRevision,
            cachedTerrainRevision,
                roadLength,
            cachedTotalStation);
    }

    private void resetEditorLocalState() {
        editorState.reset();
        selectedIntersectionIndex = -1;
        intersectionPanel.resetHeightInputState();
        activeIntersectionDragIndex = -1;
        activeIntersectionDragTarget =
            ProfileChartInteraction.IntersectionDragTarget.NONE;
        playheadStation = 0f;
    }

    private RoadProfileChartData resolveEffectiveChartData(Road road, RoadProfileChartData base) {
        return voxelPreview.resolveEffectiveChartData(road, base);
    }

    private boolean isVoxelBuildProfileStale(Road road) {
        return voxelPreview.isVoxelBuildProfileStale(road);
    }

    private List<RoadProfileIntersection> resolveIntersections(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            RoadProfileChartData chartData,
            boolean enrichSteepGradeWarnings) {
        RoadNetwork intersectionNetwork = resolveIntersectionNetwork(ctx, network);
        Road profileRoad = intersectionNetwork.getRoad(road.getId());
        if (profileRoad == null) {
            profileRoad = road;
        }
        List<RoadProfileIntersection> intersections = windowSession != null
            ? RoadProfileIntersectionResolver.forRoadDesign(intersectionNetwork, profileRoad, config)
            : chartData != null
            ? chartData.intersections()
            : RoadProfileIntersectionResolver.forRoad(
                intersectionNetwork,
                profileRoad,
                config,
                ctx.previewManager().getLastEdgeResults());
        if (windowSession != null || !enrichSteepGradeWarnings || intersections.isEmpty()) {
            return intersections;
        }
        long cacheKey = intersectionsCacheKey(ctx, road.getId(), config);
        if (road.getId().equals(cachedIntersectionsRoadId) && cacheKey == cachedIntersectionsKey) {
            return cachedIntersections;
        }
        RoadGenerator generator = gradeSeparationGenerator(ctx, config, cacheKey);
        TerrainSampler terrain = resolveTerrainSampler(generator);
        Map<String, RoadGradeSeparationEvaluation> evaluationCache = new HashMap<>();
        List<RoadProfileIntersection> resolved = RoadProfileIntersectionWarningResolver.withSteepGradeWarnings(
            intersections,
            network,
            nodeId -> evaluationCache.computeIfAbsent(nodeId, id -> {
                RoadNode node = network.getNode(id);
                if (node == null || !node.isGradeSeparated()) {
                    return null;
                }
                return generator.evaluateGradeSeparation(node, network, terrain);
            }));
        cachedIntersectionsRoadId = road.getId();
        cachedIntersectionsKey = cacheKey;
        cachedIntersections = resolved;
        return resolved;
    }

    private void invalidateIntersectionCache() {
        cachedIntersectionsRoadId = "";
        cachedIntersectionsKey = Long.MIN_VALUE;
        cachedIntersections = List.of();
        cachedGradeSeparationGenerator = null;
        cachedGradeSeparationGeneratorKey = Long.MIN_VALUE;
    }

    private static long intersectionsCacheKey(
            RoadUiContext ctx,
            String roadId,
            RoadSystemConfig config) {
        return Objects.hash(
            roadId,
            ctx.networkManager().getNetworkRevision(),
            ctx.previewManager().getTerrainRevision(),
            config != null ? config.generationInputsFingerprint() : 0L);
    }

    private RoadGenerator gradeSeparationGenerator(
            RoadUiContext ctx,
            RoadSystemConfig config,
            long cacheKey) {
        if (cachedGradeSeparationGenerator != null && cachedGradeSeparationGeneratorKey == cacheKey) {
            return cachedGradeSeparationGenerator;
        }
        cachedGradeSeparationGenerator = new RoadGenerator(
            config, ctx.host().coordinates(), ctx.host().projection());
        cachedGradeSeparationGeneratorKey = cacheKey;
        return cachedGradeSeparationGenerator;
    }

    private static RoadElevationBounds resolveElevationBounds(RoadUiContext ctx) {
        TerrainSampler terrain = FlatElevationRecommendationUi.terrainSupplier(ctx).get();
        return RoadWorldElevationBounds.resolve(terrain);
    }

    private static TerrainSampler resolveTerrainSampler(RoadGenerator generator) {
        World world = RoadNetworkGenerator.getClientWorld();
        if (world != null) {
            return generator.createTerrainSampler(world);
        }
        return new FlatTerrainSampler(TerrainSampler.DEFAULT_SEA_LEVEL);
    }

    private void recordUndoPointBeforeEdit() {
        if (windowSession != null) {
            windowSession.recordUndoPoint();
        }
    }

    private void handleEditorKeyboardShortcuts(RoadUiContext ctx) {
        if (!ImGui.isWindowFocused()) {
            return;
        }
        if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.Z)) {
            if (ImGui.getIO().getKeyShift()) {
                redoEditorSession(ctx);
            } else {
                undoEditorSession(ctx);
            }
        } else if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.Y)) {
            redoEditorSession(ctx);
        }
    }

    private void undoEditorSession(RoadUiContext ctx) {
        if (windowSession == null || !windowSession.canUndo()) {
            return;
        }
        windowSession.undo();
        profileEditSession.cancelEdit();
        chartCache.invalidateRenderCache();
        resetEditorLocalState();
        invalidateIntersectionCache();
        chartCache.clear();
        notifyWorkingProfileChanged();
        ctx.requestOverlayRefresh();
    }

    private void redoEditorSession(RoadUiContext ctx) {
        if (windowSession == null || !windowSession.canRedo()) {
            return;
        }
        windowSession.redo();
        profileEditSession.cancelEdit();
        chartCache.invalidateRenderCache();
        resetEditorLocalState();
        invalidateIntersectionCache();
        chartCache.clear();
        notifyWorkingProfileChanged();
        ctx.requestOverlayRefresh();
    }

    private void notifyWorkingProfileChanged() {
        voxelPreview.cancel();
        chartCache.invalidateRenderCache();
        invalidateIntersectionCache();
        if (windowSession != null && editorRoadId != null && !editorRoadId.isBlank()) {
            windowSession.markAllVoxelPreviewsStale();
        }
        // Geometry/clearance overlays update live. Defer terrain and voxel work
        // until the user requests it after editing the connected set of roads.
    }

    private boolean isChartDerivedDataStale(Road road) {
        if (windowSession == null || road == null) {
            return isVoxelBuildProfileStale(road);
        }
        if (windowSession.isDirty() || profileEditSession.isActive() || profileEditSession.isDirty()) {
            return true;
        }
        return isVoxelBuildProfileStale(road);
    }

    private RoadNetwork resolveIntersectionNetwork(RoadUiContext ctx, RoadNetwork network) {
        if (windowSession != null) {
            return network;
        }
        RoadNetwork profileNetwork = ctx.previewManager().getLastProfileNetwork();
        return profileNetwork != null ? profileNetwork : network;
    }

    private double resolveBootstrapEndpointElevation(
            RoadNetwork network,
            Road road,
            RoadProfileChartData chartData,
            double station) {
        RoadVerticalAlignment alignment = profileEditSession.effectiveAlignment(road);
        if (alignment == null) {
            float maxGrade = road.getMaxSlope() != null
                ? road.getMaxSlope()
                : 8.0f;
            alignment = RoadVerticalAlignmentResolver.resolveSynced(network, road, maxGrade);
        }
        if (alignment != null && VerticalAlignmentGeometry.isEvaluable(alignment)) {
            return VerticalAlignmentGeometry.elevationAt(alignment, station)
                .orElseGet(() -> fallbackBootstrapEndpointElevation(network, road, chartData, station));
        }
        return fallbackBootstrapEndpointElevation(network, road, chartData, station);
    }

    private static double fallbackBootstrapEndpointElevation(
            RoadNetwork network,
            Road road,
            RoadProfileChartData chartData,
            double station) {
        if (RoadVerticalStrategy.fromRoad(road) == RoadVerticalStrategy.FLAT) {
            FlatVerticalIntent intent = FlatVerticalIntentSupport.resolveIntent(network, road);
            if (intent != null) {
                return intent.getBaseElevation();
            }
        }
        if (chartData != null && chartData.hasProfileData()) {
            double preview = chartData.previewElevationAt(station);
            if (Double.isFinite(preview)) {
                return preview;
            }
            return chartData.groundElevationAt(station);
        }
        return TerrainSampler.DEFAULT_SEA_LEVEL;
    }

    private void propagateJunctionGrades(RoadUiContext ctx, RoadNetwork network, Road road) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        var result = profileEditSession.isActive() ? profileEditSession.constraintResult()
            : com.plot.plugin.road.vertical.RoadProfileConstraintSynchronizer.synchronize(
                network, road, windowSession != null && windowSession.openSnapshot().getRoad(road.getId()) != null
                    ? windowSession.openSnapshot().getRoad(road.getId()).getVerticalAlignment() : null, config);
        if (windowSession != null) {
            windowSession.markTouched(road);
            windowSession.markPropagationAdjustedRoads(network);
        }
        if (!result.unresolvedConstraints().isEmpty()) {
            editorState.profileAutoFixMessage = PlotI18n.tr(
                "plugin.road.vertical_alignment_network_unresolved",
                result.unresolvedConstraints().size());
        } else if (!result.changedRoadIds().isEmpty()) {
            editorState.profileAutoFixMessage = PlotI18n.tr(
                "plugin.road.vertical_alignment_network_adjusted",
                result.changedRoadIds().size());
        }
    }
}
