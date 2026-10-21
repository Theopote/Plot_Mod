package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.profile.ProfileChartInteraction;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadGradeSeparationEvaluation;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.profile.ProfileChartRenderMode;
import com.plot.plugin.road.profile.ProfileControlPoint;
import com.plot.plugin.road.profile.ProfilePointRole;
import com.plot.plugin.road.profile.ProfileChartLegend;
import com.plot.plugin.road.profile.ProfileQualitySummary;
import com.plot.plugin.road.profile.ProfileRenderCache;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.profile.RoadProfileChartRenderer;
import com.plot.plugin.road.profile.ProfileAlignmentViolationFormatter;
import com.plot.plugin.road.profile.RoadProfileRoadList;
import com.plot.plugin.road.profile.RoadProfileRoadNavigator;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.RoadProfileIntersectionDragEditor;
import com.plot.plugin.road.profile.RoadProfileIntersectionResolver;
import com.plot.plugin.road.profile.RoadProfileIntersectionWarningResolver;
import com.plot.plugin.road.pipeline.profile.ProfileCompileOptions;
import com.plot.plugin.road.pipeline.profile.RoadVoxelProfileCompiler;
import com.plot.plugin.road.pipeline.profile.VoxelProfileCompileResult;
import com.plot.plugin.road.profile.edit.ProfileEditSession;
import com.plot.plugin.road.profile.edit.ProfileEditorWindowSession;
import com.plot.plugin.road.profile.edit.VoxelProfilePreviewCompileRequest;
import com.plot.plugin.road.profile.edit.VoxelProfilePreviewKey;
import com.plot.plugin.road.profile.edit.VoxelProfilePreviewSlot;
import com.plot.plugin.road.profile.edit.VoxelProfilePreviewState;
import com.plot.plugin.road.vertical.VerticalAlignmentValidator;
import com.plot.plugin.road.vertical.VerticalAlignmentViolation;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.world.World;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadWorldElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;
import com.plot.plugin.road.vertical.FlatVerticalIntent;
import com.plot.plugin.road.vertical.FlatVerticalIntentSupport;
import com.plot.plugin.road.vertical.RoadVerticalAlignmentResolver;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 生成 Tab 纵断面：内联只读概览 + 独立编辑器窗口（放大交互）。
 */
final class VerticalProfileEditor {

    private static final float MIN_EDITOR_CHART_HEIGHT = 260f;
    private static final float EDITOR_CHART_HEIGHT_RATIO = 0.70f;
    private static final int EDITOR_WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

    private final ImBoolean editorWindowOpen = new ImBoolean(false);
    private String editorRoadId = "";
    private boolean focusEditorOnOpen = false;

    private RoadProfileChartData cachedChartData;
    private String cachedChartRoadId = "";
    private final ProfileEditorState editorState = new ProfileEditorState();
    private final ProfileEditSession profileEditSession = new ProfileEditSession();
    private final FlatProfileControls flatProfileControls = new FlatProfileControls();
    private final AdaptiveProfileControls adaptiveProfileControls = new AdaptiveProfileControls();
    private ProfileEditorWindowSession windowSession;
    private boolean closeConfirmPending;
    private Runnable onProfileConfirmed;
    private int selectedIntersectionIndex = -1;
    private String intersectionInputKey = "";
    private final imgui.type.ImFloat intersectionHeightInput = new imgui.type.ImFloat();
    private final imgui.type.ImFloat pviHeightInput = new imgui.type.ImFloat();
    private boolean intersectionInputDirty;
    private int intersectionInputTarget;
    private RoadGradeSeparationControls gradeSeparationControls;
    private int activeIntersectionDragIndex = -1;
    private ProfileChartInteraction.IntersectionDragTarget
        activeIntersectionDragTarget =
            ProfileChartInteraction.IntersectionDragTarget.NONE;
    private String cachedIntersectionsRoadId = "";
    private long cachedIntersectionsKey = Long.MIN_VALUE;
    private List<RoadProfileIntersection> cachedIntersections = List.of();
    private RoadGenerator cachedGradeSeparationGenerator;
    private long cachedGradeSeparationGeneratorKey = Long.MIN_VALUE;
    private ProfileRenderCache profileRenderCache;
    private Function<Road, FlatElevationProfileOverlay> flatOverlayResolver = road -> null;
    private VoxelProfilePreviewCompileRequest pendingVoxelCompile;

    void clearCache() {
        cachedChartData = null;
        cachedChartRoadId = "";
        editorState.reset();
        profileEditSession.cancelEdit();
        profileRenderCache = null;
        selectedIntersectionIndex = -1;
        intersectionInputKey = "";
        activeIntersectionDragIndex = -1;
        activeIntersectionDragTarget =
            ProfileChartInteraction.IntersectionDragTarget.NONE;
        windowSession = null;
        closeConfirmPending = false;
        pendingVoxelCompile = null;
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
        selectEditorRoad(ctx, roadId, true);
        editorWindowOpen.set(true);
        focusEditorOnOpen = true;
    }

    void renderCompactMissingProfile(RoadUiContext ctx, RoadNetwork network, Road road) {
        renderMissingProfileActions(ctx, network, road);
    }

    void setOnAlignmentCommitted(Runnable onProfileConfirmed) {
        this.onProfileConfirmed = onProfileConfirmed;
    }

    void setFlatOverlayResolver(Function<Road, FlatElevationProfileOverlay> flatOverlayResolver) {
        this.flatOverlayResolver = flatOverlayResolver != null ? flatOverlayResolver : road -> null;
    }

    boolean isEditorOpen() {
        return editorWindowOpen.get();
    }

    String getEditorRoadId() {
        return editorRoadId;
    }

    void renderEditorWindow(RoadUiContext ctx, RoadNetwork liveNetwork) {
        if (!editorWindowOpen.get()) {
            discardWindowSession();
            return;
        }
        ensureWindowSession(ctx, liveNetwork);
        tickPendingVoxelCompile(ctx);
        RoadNetwork network = windowSession.editingNetwork();
        ensureEditorRoadSelection(ctx, network);

        if (focusEditorOnOpen) {
            var center = ImGui.getMainViewport().getCenter();
            ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);
            float viewportWidth = ImGui.getIO().getDisplaySizeX();
            float viewportHeight = ImGui.getIO().getDisplaySizeY();
            ImGui.setNextWindowSize(
                Math.min(viewportWidth * 0.88f, 920f),
                Math.min(viewportHeight * 0.72f, 580f),
                ImGuiCond.Appearing);
            ImGui.setNextWindowFocus();
            focusEditorOnOpen = false;
        }
        ImGui.setNextWindowSizeConstraints(480f, 380f, Float.MAX_VALUE, Float.MAX_VALUE);

        String windowTitle = PlotI18n.tr("plugin.road.vertical_alignment_editor_window");
        if (windowSession != null && windowSession.isDirty()) {
            windowTitle += " " + PlotI18n.tr("plugin.road.profile_editor_dirty_marker");
        }
        if (!ImGui.begin(windowTitle + "###road_profile_editor", editorWindowOpen, EDITOR_WINDOW_FLAGS)) {
            handleEditorCloseRequest(ctx, liveNetwork);
            ImGui.end();
            return;
        }
        try {
            renderEditorRoadSelector(ctx, network);
            Road road = editorRoadId != null && !editorRoadId.isBlank()
                ? network.getRoad(editorRoadId)
                : null;
            if (road == null) {
                requestEditorClose(ctx, liveNetwork, false);
                return;
            }
            renderEditorStatusBadge();
            renderEditorContextCard(ctx, network, road);
            float footerHeight = ImGui.getFrameHeightWithSpacing() * 4.4f;
            if (ImGui.beginChild("##profile_editor_scroll", 0f, -footerHeight, true)) {
                if (!RoadStationing.isStationable(network, road)) {
                    RoadUiWidgets.textWrappedColored(
                        PluginUiColors.HINT_GRAY,
                        PlotI18n.tr("plugin.road.profile_discontinuous_road_hint"));
                } else {
                    RoadProfileChartData chartData = resolveChartData(ctx, liveNetwork, road);
                    if (chartData == null || !chartData.hasProfileData()) {
                        renderMissingProfileActions(ctx, liveNetwork, road);
                    } else {
                        FlatElevationProfileOverlay flatOverlay = resolveFlatOverlay(road);
                        VerticalAlignmentProfileOverlay design =
                            profileEditSession.effectiveDesignOverlay(network, road).orElse(null);
                        float chartHeight = Math.max(
                            MIN_EDITOR_CHART_HEIGHT,
                            ImGui.getContentRegionAvail().y * EDITOR_CHART_HEIGHT_RATIO);
                        renderInteractiveEditor(
                            ctx, liveNetwork, network, road, chartData, design, chartHeight, flatOverlay);
                    }
                }
                ImGui.endChild();
            }
            handleEditorKeyboardShortcuts(ctx);
            renderEditorFooter(ctx, liveNetwork);
            renderEditorDialogs(ctx, liveNetwork);
        } finally {
            if (!editorWindowOpen.get()) {
                handleEditorCloseRequest(ctx, liveNetwork);
            }
            ImGui.end();
        }
    }

    private void ensureWindowSession(RoadUiContext ctx, RoadNetwork liveNetwork) {
        if (windowSession == null && liveNetwork != null) {
            windowSession = ProfileEditorWindowSession.open(
                liveNetwork, ctx.networkManager().getNetworkRevision());
            windowSession.seedSampledProfiles(ctx.networkManager().getConfig(),
                ctx.previewManager().getLastEdgeResults());
        }
    }

    private void discardWindowSession() {
        if (windowSession != null) {
            windowSession.cancel();
        }
        windowSession = null;
        profileEditSession.cancelEdit();
        closeConfirmPending = false;
        pendingVoxelCompile = null;
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
        windowSession = ProfileEditorWindowSession.open(
            liveNetwork, ctx.networkManager().getNetworkRevision());
        profileEditSession.cancelEdit();
        profileRenderCache = null;
        invalidateIntersectionCache();
        cachedChartData = null;
        cachedChartRoadId = "";
    }

    private void restoreWindowSession() {
        if (windowSession == null) {
            return;
        }
        windowSession.restore();
        profileEditSession.cancelEdit();
        profileRenderCache = null;
        resetEditorLocalState();
        invalidateIntersectionCache();
        cachedChartData = null;
        cachedChartRoadId = "";
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
        profileRenderCache = null;
        editorRoadId = roadId;
        resetEditorLocalState();
        invalidateIntersectionCache();
        cachedChartData = null;
        cachedChartRoadId = "";
        if (syncSelection) {
            ctx.networkManager().selectRoad(roadId, false);
            ctx.requestOverlayRefresh();
        }
    }

    private void renderEditorFooter(RoadUiContext ctx, RoadNetwork liveNetwork) {
        ImGui.separator();
        if (windowSession != null && windowSession.isDirty()) {
            ImGui.textColored(
                PluginUiColors.WARNING_LIGHT,
                PlotI18n.tr("plugin.road.profile_editor_unsaved_status"));
        } else {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_editor_clean_status"));
        }
        boolean canUndo = windowSession != null && windowSession.canUndo();
        boolean canRedo = windowSession != null && windowSession.canRedo();
        float undoRedoWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2f;
        if (!canUndo) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_editor_undo") + "##profile_undo",
                undoRedoWidth, 0)) {
            undoEditorSession(ctx);
        }
        if (!canUndo) {
            ImGui.endDisabled();
        }
        ImGui.sameLine();
        if (!canRedo) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_editor_redo") + "##profile_redo",
                undoRedoWidth, 0)) {
            redoEditorSession(ctx);
        }
        if (!canRedo) {
            ImGui.endDisabled();
        }
        boolean dirty = windowSession != null && windowSession.isDirty();
        if (dirty) {
            int affectCount = windowSession.estimateConfirmAffectRoadCount();
            if (affectCount > 0) {
                ImGui.textColored(
                    PluginUiColors.WARNING_LIGHT,
                    PlotI18n.tr("plugin.road.profile_editor_confirm_affect_summary", affectCount));
            }
        }
        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX() * 2f) / 3f;
        if (!dirty) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_editor_restore") + "##profile_restore",
                buttonWidth, 0)) {
            restoreWindowSession();
        }
        if (!dirty) {
            ImGui.endDisabled();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_editor_cancel") + "##profile_cancel",
                buttonWidth, 0)) {
            requestEditorClose(ctx, liveNetwork, true);
        }
        ImGui.sameLine();
        boolean confirmDirty = windowSession != null && windowSession.isDirty();
        if (confirmDirty) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, PluginUiColors.ACCENT_BLUE);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, PluginUiColors.INFO_BLUE);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, PluginUiColors.STATUS_INFO);
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_editor_confirm") + "##profile_confirm",
                buttonWidth, 0)) {
            applyWindowSessionConfirm(ctx, liveNetwork);
        }
        if (confirmDirty) {
            ImGui.popStyleColor(3);
        }
    }

    private void renderEditorDialogs(RoadUiContext ctx, RoadNetwork liveNetwork) {
        renderCloseConfirmDialog(ctx, liveNetwork);
    }

    private void renderCloseConfirmDialog(RoadUiContext ctx, RoadNetwork liveNetwork) {
        if (!RoadUiWidgets.beginDeferredPopupModal(
                "##profile_editor_close_confirm",
                closeConfirmPending,
                () -> closeConfirmPending = false)) {
            return;
        }
        ImGui.text(PlotI18n.tr("plugin.road.profile_editor_close_confirm_message"));
        if (windowSession != null && windowSession.isDirty()) {
            int affectCount = windowSession.estimateConfirmAffectRoadCount();
            if (affectCount > 0) {
                ImGui.textColored(
                    PluginUiColors.WARNING_LIGHT,
                    PlotI18n.tr("plugin.road.profile_editor_confirm_affect_summary", affectCount));
            }
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_editor_confirm") + "##close_apply")) {
            applyWindowSessionConfirm(ctx, liveNetwork);
            closeConfirmPending = false;
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_editor_cancel") + "##close_discard")) {
            closeConfirmPending = false;
            editorWindowOpen.set(false);
            discardWindowSession();
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_editor_continue_edit") + "##close_continue")) {
            closeConfirmPending = false;
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void ensureEditorRoadSelection(RoadUiContext ctx, RoadNetwork network) {
        List<Road> roads = RoadProfileRoadList.listStationableRoads(network);
        if (roads.isEmpty()) {
            return;
        }
        Road primary = ctx.networkManager().getPrimarySelectedRoad();
        if (primary != null
                && RoadStationing.isStationable(network, primary)
                && !Objects.equals(editorRoadId, primary.getId())) {
            selectEditorRoad(ctx, primary.getId(), false);
            return;
        }
        String normalized = RoadProfileRoadNavigator.normalizeRoadId(roads, editorRoadId);
        if (normalized != null && !Objects.equals(editorRoadId, normalized)) {
            selectEditorRoad(ctx, normalized, false);
        }
    }

    private void renderEditorRoadSelector(RoadUiContext ctx, RoadNetwork network) {
        List<Road> roads = RoadProfileRoadList.listStationableRoads(network);
        if (roads.isEmpty()) {
            return;
        }
        int currentIndex = RoadProfileRoadNavigator.indexOf(roads, editorRoadId);
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        Road currentRoad = roads.get(currentIndex);

        ImGui.text(PlotI18n.tr("plugin.road.profile_editor_road_selector"));
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_editor_road_nav_prev") + "##profile_prev")) {
            selectEditorRoad(ctx, RoadProfileRoadNavigator.previousRoadId(roads, editorRoadId), true);
        }
        ImGui.sameLine();
        String preview = RoadProfileRoadList.formatProfileRoadSummary(network, currentRoad);
        if (ImGui.beginCombo("##profile_editor_road_combo", preview)) {
            for (Road road : roads) {
                String label = RoadProfileRoadList.formatProfileRoadSummary(network, road);
                boolean selected = road.getId().equals(editorRoadId);
                if (ImGui.selectable(label, selected)) {
                    selectEditorRoad(ctx, road.getId(), true);
                }
                if (selected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_editor_road_nav_next") + "##profile_next")) {
            selectEditorRoad(ctx, RoadProfileRoadNavigator.nextRoadId(roads, editorRoadId), true);
        }
        ImGui.sameLine();
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.road.profile_overview_road_index",
                currentIndex + 1,
                roads.size()));
    }

    private void renderEditorStatusBadge() {
        boolean dirty = windowSession != null && windowSession.isDirty();
        if (dirty) {
            ImGui.sameLine();
            ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.road.profile_editor_unsaved_badge"));
        } else {
            ImGui.sameLine();
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_editor_clean_badge"));
        }
    }

    private void renderEditorContextCard(RoadUiContext ctx, RoadNetwork network, Road road) {
        ImGui.spacing();
        String roadLabel = RoadEdgeListHelper.formatRoadLabel(network, road);
        String modeLabel = RoadVerticalStrategy.fromRoad(road).label();
        ImGui.textColored(
            PluginUiColors.ACCENT_BLUE,
            PlotI18n.tr("plugin.road.profile_editor_context_editing", roadLabel));
        ImGui.sameLine();
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_editor_context_mode", modeLabel));

        String selection = resolveSelectionSummary(road);
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_editor_context_selection", selection));

        String affecting = resolveAffectingSummary(network, road);
        ImGui.textColored(
            affectingAffectsOthers(network, road) ? PluginUiColors.WARNING_LIGHT : PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_editor_context_affecting", affecting));
        ImGui.spacing();
    }

    private String resolveSelectionSummary(Road road) {
        if (selectedIntersectionIndex >= 0) {
            return PlotI18n.tr(
                "plugin.road.profile_editor_selection_intersection",
                selectedIntersectionIndex + 1);
        }
        if (editorState.selectedProfilePvi >= 0) {
            RoadVerticalAlignment alignment = profileEditSession.effectiveAlignment(road);
            if (alignment != null
                    && editorState.selectedProfilePvi < alignment.pviCount()) {
                double station = alignment.getPvis().get(editorState.selectedProfilePvi).getStation();
                return PlotI18n.tr(
                    "plugin.road.profile_editor_selection_pvi",
                    editorState.selectedProfilePvi + 1,
                    RoadUiFormat.formatDistance(station));
            }
            return PlotI18n.tr(
                "plugin.road.profile_editor_selection_pvi_index",
                editorState.selectedProfilePvi + 1);
        }
        return PlotI18n.tr("plugin.road.profile_editor_selection_none");
    }

    private boolean affectingAffectsOthers(RoadNetwork network, Road road) {
        if (selectedIntersectionIndex >= 0) {
            return true;
        }
        if (editorState.selectedProfilePvi < 0 || network == null || road == null) {
            return false;
        }
        RoadVerticalAlignment alignment = profileEditSession.effectiveAlignment(road);
        if (alignment == null || editorState.selectedProfilePvi >= alignment.pviCount()) {
            return false;
        }
        List<ProfileControlPoint> points = VerticalProfileControlPoints.forAlignment(
            network, road, alignment);
        return points.stream()
            .anyMatch(point -> point.pviIndex() == editorState.selectedProfilePvi && point.sharedJunction());
    }

    private String resolveAffectingSummary(RoadNetwork network, Road road) {
        if (selectedIntersectionIndex >= 0) {
            List<RoadProfileIntersection> intersections = cachedIntersections;
            if (selectedIntersectionIndex < intersections.size()) {
                RoadProfileIntersection intersection = intersections.get(selectedIntersectionIndex);
                if (intersection.gradeSeparated()) {
                    return PlotI18n.tr("plugin.road.profile_editor_affect_current_only");
                }
                return PlotI18n.tr(
                    "plugin.road.profile_editor_affect_intersection",
                    intersection.otherRoadLabel());
            }
            return PlotI18n.tr("plugin.road.profile_editor_affect_connected");
        }
        if (affectingAffectsOthers(network, road)) {
            return PlotI18n.tr("plugin.road.profile_editor_affect_junction_pvi");
        }
        return PlotI18n.tr("plugin.road.profile_editor_affect_current_only");
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
            List<RoadProfileIntersection> intersections) {
        return resolveRenderCache(ctx, network, road, chartData, flatOverlay, intersections, null);
    }

    private ProfileRenderCache resolveRenderCache(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadProfileChartData chartData,
            FlatElevationProfileOverlay flatOverlay,
            List<RoadProfileIntersection> intersections,
            VoxelProfilePreviewSlot voxelSlot) {
        long cacheKey = ProfileRenderCache.computeKey(
            road.getId(),
            ctx.networkManager().getNetworkRevision(),
            ctx.previewManager().getTerrainRevision(),
            chartData,
            voxelSlot != null ? voxelSlot.state().ordinal() : -1,
            voxelSlot != null && voxelSlot.key() != null ? voxelSlot.key().hashCode() : 0);
        if (profileRenderCache != null
                && profileRenderCache.cacheKey() == cacheKey
                && road.getId().equals(editorRoadId)) {
            return profileRenderCache;
        }
        profileRenderCache = ProfileRenderCache.build(
            cacheKey, chartData, intersections, flatOverlay);
        return profileRenderCache;
    }

    private RoadProfileChartData resolveChartData(RoadUiContext ctx, RoadNetwork network, Road road) {
        if (road == null) {
            return null;
        }
        RoadProfileChartData chart = ctx.previewManager().getRoadProfileChart(road.getId());
        if (chart != null && chart.hasProfileData()) {
            cachedChartData = chart;
            cachedChartRoadId = road.getId();
            return chart;
        }
        if (road.getId().equals(cachedChartRoadId)
                && cachedChartData != null
                && cachedChartData.hasProfileData()) {
            return cachedChartData;
        }
        return null;
    }

    private void resetEditorLocalState() {
        editorState.reset();
        selectedIntersectionIndex = -1;
        intersectionInputKey = "";
        activeIntersectionDragIndex = -1;
        activeIntersectionDragTarget =
            ProfileChartInteraction.IntersectionDragTarget.NONE;
    }

    private void renderMissingProfileActions(RoadUiContext ctx, RoadNetwork network, Road road) {
        String hint = road != null && ctx.previewManager().hasIncompleteProfileSampling(network, road)
            ? PlotI18n.tr("plugin.road.profile_incomplete_preview_hint")
            : PlotI18n.tr("plugin.road.vertical_alignment_profile_preview_required");
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, hint);
        if (ImGui.button(PlotI18n.tr("plugin.road.vertical_alignment_calculate_profile"))) {
            ctx.previewManager().calculateProfileSamplingOnly(network);
        }
        ImGui.sameLine();
        String fullPreviewLabel = ctx.previewManager().needsPreviewRecalc()
            ? PlotI18n.tr("plugin.road.recalculate_preview")
            : PlotI18n.tr("plugin.road.calc_preview");
        if (ImGui.button(fullPreviewLabel + "##profile_full_preview")) {
            ctx.previewManager().startNetworkPreview(network);
        }
    }

    private void renderEditorLegend(
            RoadUiContext ctx,
            Road road,
            VerticalAlignmentProfileOverlay design,
            List<RoadProfileIntersection> intersections,
            FlatElevationProfileOverlay flatOverlay,
            boolean flatMode) {
        ProfileChartLegend.renderCompact(
            ProfileChartRenderMode.EDITOR,
            road != null ? road.getVerticalMode() : null,
            flatMode,
            isChartDerivedDataStale(road) || ctx.previewManager().needsPreviewRecalc());
        ProfileChartLegend.renderIntersectionLegend(intersections);
        renderVoxelProfileControls(ctx, road);
    }

    private void renderVoxelProfileControls(RoadUiContext ctx, Road road) {
        if (windowSession == null || road == null) {
            return;
        }
        VoxelProfilePreviewSlot slot = windowSession.voxelPreview(road.getId());
        ImGui.spacing();
        String statusKey = switch (slot.state()) {
            case READY -> "plugin.road.voxel_profile_ready";
            case STALE, NOT_COMPUTED -> "plugin.road.voxel_profile_stale";
            case COMPUTING -> "plugin.road.voxel_profile_computing";
            case FAILED -> "plugin.road.voxel_profile_failed";
        };
        int statusColor = slot.state() == VoxelProfilePreviewState.READY
            ? PluginUiColors.STATUS_OK
            : slot.state() == VoxelProfilePreviewState.FAILED
                ? PluginUiColors.WARNING
                : PluginUiColors.HINT_GRAY;
        ImGui.textColored(statusColor, PlotI18n.tr(statusKey));
        if (slot.state() == VoxelProfilePreviewState.FAILED && !slot.failureReason().isBlank()) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, slot.failureReason());
        }
        boolean computing = slot.state() == VoxelProfilePreviewState.COMPUTING;
        if (computing) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(
                PlotI18n.tr("plugin.road.update_voxel_profile") + "##update_voxel_profile")) {
            requestVoxelProfilePreview(ctx, road);
        }
        if (computing) {
            ImGui.endDisabled();
        }
    }

    private void requestVoxelProfilePreview(RoadUiContext ctx, Road road) {
        if (windowSession == null || road == null) {
            return;
        }
        RoadNetwork editingNetwork = windowSession.editingNetwork();
        Road editingRoad = editingNetwork.getRoad(road.getId());
        if (editingRoad == null) {
            return;
        }
        // Compile against a disposable snapshot so an active Draft is never written into the
        // window working copy by the preview button itself.
        RoadSystemConfig config = ctx.networkManager().getConfig();
        RoadNetwork compileNetwork = profileEditSession.previewNetwork(editingNetwork, editingRoad, config).snapshot();
        Road compileRoad = compileNetwork.getRoad(road.getId());
        if (compileRoad == null) {
            return;
        }
        RoadVerticalAlignment effective = compileRoad.getVerticalAlignment();
        if (effective != null) {
            compileRoad.setVerticalAlignment(effective);
        }
        ProfileCompileOptions options = ProfileCompileOptions.defaults();
        RoadElevationBounds elevationBounds = resolveElevationBounds(ctx);
        RoadProfileChartData baseChart = resolveChartData(ctx, editingNetwork, editingRoad);
        List<RoadProfileIntersection> intersections = resolveIntersections(
            ctx, compileNetwork, compileRoad, config, baseChart, true);
        intersections = profileEditSession.effectiveIntersections(intersections);
        VoxelProfilePreviewKey key = VoxelProfilePreviewKey.of(
            compileNetwork,
            compileRoad,
            effective,
            intersections,
            com.plot.plugin.road.pipeline.profile.VoxelProfileConstraints.empty(),
            elevationBounds,
            ctx.previewManager().getTerrainRevision(),
            config,
            options);
        VoxelProfilePreviewSlot slot = windowSession.voxelPreview(editingRoad.getId());
        slot.markComputing(key);
        long generatorCacheKey = System.identityHashCode(config) ^ editingRoad.getId().hashCode();
        pendingVoxelCompile = new VoxelProfilePreviewCompileRequest(
            editingRoad.getId(),
            compileNetwork,
            effective,
            key,
            options,
            elevationBounds,
            generatorCacheKey);
    }

    private void tickPendingVoxelCompile(RoadUiContext ctx) {
        if (pendingVoxelCompile == null || windowSession == null || ctx == null) {
            return;
        }
        VoxelProfilePreviewCompileRequest request = pendingVoxelCompile;
        pendingVoxelCompile = null;
        Road compileRoad = request.compileNetwork().getRoad(request.roadId());
        if (compileRoad == null) {
            return;
        }
        VoxelProfilePreviewSlot slot = windowSession.voxelPreview(request.roadId());
        if (slot.state() != VoxelProfilePreviewState.COMPUTING) {
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();
        try {
            RoadGenerator generator = gradeSeparationGenerator(
                ctx, config, request.generatorCacheKey());
            TerrainSampler terrain = resolveTerrainSampler(generator);
            VoxelProfileCompileResult result = RoadVoxelProfileCompiler.compile(
                request.compileNetwork(),
                compileRoad,
                config,
                generator,
                terrain,
                ctx.previewManager().getLastNodeElevations(),
                request.options(),
                request.elevationBounds(),
                request.alignmentOverride());
            windowSession.storeVoxelPreview(request.roadId(), request.key(), result);
            profileRenderCache = null;
        } catch (RuntimeException ex) {
            slot.markFailed(request.key(), ex.getMessage() != null ? ex.getMessage() : "compile error");
        }
    }

    private RoadProfileChartData resolveEffectiveChartData(Road road, RoadProfileChartData base) {
        if (base == null || road == null || windowSession == null) {
            return base;
        }
        VoxelProfilePreviewSlot slot = windowSession.voxelPreview(road.getId());
        RoadProfileChartData overlay = slot.displayOverlay();
        if (overlay != null && overlay.hasBuildSamples()) {
            return overlay;
        }
        return base;
    }

    private boolean isVoxelBuildProfileStale(Road road) {
        if (windowSession == null || road == null) {
            return false;
        }
        VoxelProfilePreviewSlot slot = windowSession.voxelPreview(road.getId());
        return slot.state() == VoxelProfilePreviewState.STALE
            || slot.state() == VoxelProfilePreviewState.NOT_COMPUTED
            || slot.state() == VoxelProfilePreviewState.FAILED
            || slot.state() == VoxelProfilePreviewState.COMPUTING;
    }

    private void renderInteractiveEditor(
            RoadUiContext ctx,
            RoadNetwork liveNetwork,
            RoadNetwork editingNetwork,
            Road road,
            RoadProfileChartData chartData,
            VerticalAlignmentProfileOverlay design,
            float chartHeight,
            FlatElevationProfileOverlay flatOverlay) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        RoadNetwork displayNetwork = profileEditSession.previewNetwork(editingNetwork, road, config);
        Road displayRoad = displayNetwork.getRoad(road.getId());
        design = displayNetwork == editingNetwork
            ? profileEditSession.effectiveDesignOverlay(editingNetwork, road).orElse(null)
            : VerticalAlignmentProfileOverlay.forAlignment(displayNetwork, displayRoad,
                displayRoad.getVerticalAlignment()).orElse(null);
        boolean flatMode = RoadVerticalStrategy.fromRoad(road) == RoadVerticalStrategy.FLAT;
        RoadVerticalAlignment effectiveAlignment = profileEditSession.effectiveAlignment(road);
        RoadProfileChartData effectiveChart = resolveEffectiveChartData(road, chartData);
        boolean buildProfileStale = isChartDerivedDataStale(road);
        List<ProfileControlPoint> points = flatMode
            ? List.of()
            : VerticalProfileControlPoints.forAlignment(editingNetwork, road, effectiveAlignment);
        List<VerticalProfileControlPoints.ControlPoint> legacyPoints = points.stream()
            .map(VerticalProfileControlPoints::toLegacyControlPoint)
            .toList();
        float maxGrade = road.getMaxSlope() != null
            ? road.getMaxSlope()
            : ctx.networkManager().getConfig().getMaxSlope();
        List<RoadProfileIntersection> baseIntersections = resolveIntersections(
            ctx, displayNetwork, displayRoad, config, effectiveChart,
            !profileEditSession.isActive() && activeIntersectionDragIndex < 0);
        List<RoadProfileIntersection> intersections = baseIntersections;
        renderIntersectionSelector(intersections);
        ProfileRenderCache renderCache = resolveRenderCache(
            ctx, liveNetwork, road, effectiveChart, flatOverlay, baseIntersections,
            windowSession != null ? windowSession.voxelPreview(road.getId()) : null);
        int chartSelectedPvi = flatMode ? -1 : editorState.selectedProfilePvi;
        int chartActivePvi = flatMode ? -1 : editorState.activeProfilePvi;
        int curveHandlePvi = flatMode
            ? -1
            : resolveCurveHandlePviIndex(
                road, effectiveAlignment, chartSelectedPvi, selectedIntersectionIndex, intersections);
        List<ProfileChartInteraction.CurveHandle> curveHandles = flatMode
            ? List.of()
            : buildRoadCurveHandles(editingNetwork, road, effectiveAlignment, points, curveHandlePvi);
        if (!flatMode) {
            renderProfileInteractionHint();
        }
        renderManualEndpointConstraintWarning(effectiveChart);
        renderWaterConstraintWarning(effectiveChart);
        RoadElevationBounds elevationBounds = resolveElevationBounds(ctx);
        profileEditSession.setElevationBounds(elevationBounds);
        ProfileChartInteraction.ControlInteraction interaction =
            RoadProfileChartRenderer.renderInteractive(
                effectiveChart, design, points, chartSelectedPvi, chartActivePvi, maxGrade,
                intersections, selectedIntersectionIndex, chartHeight,
                activeIntersectionDragIndex, activeIntersectionDragTarget, flatOverlay,
                curveHandles,
                editorState.pendingProfilePvi,
                editorState.pendingClickX,
                editorState.pendingClickY,
                editorState.activeCurveHandlePvi,
                editorState.activeCurveHandle,
                ProfileChartRenderMode.EDITOR,
                renderCache,
                road.getVerticalMode(),
                elevationBounds,
                effectiveAlignment,
                buildProfileStale);
        if (interaction.intersectionDragStarted()) {
            recordUndoPointBeforeEdit();
            profileEditSession.beginIntersectionEdit(baseIntersections);
        }
        if (interaction.curveHandleDragStarted()) {
            recordUndoPointBeforeEdit();
            profileEditSession.beginCurveEdit(road, interaction.activeCurveHandlePvi());
        }
        if (interaction.dragStarted()) {
            recordUndoPointBeforeEdit();
            profileEditSession.beginPviEdit(road);
        }
        if (!flatMode) {
            if (interaction.selectedPviIndex() >= 0
                    && interaction.selectedPviIndex() != editorState.selectedProfilePvi) {
                legacyPoints.stream()
                    .filter(point -> point.pviIndex() == interaction.selectedPviIndex())
                    .findFirst()
                    .ifPresent(point -> adaptiveProfileControls.onPviSelected(editorState, point));
            }
            editorState.selectedProfilePvi = interaction.selectedPviIndex();
            editorState.activeProfilePvi = interaction.activePviIndex();
            editorState.pendingProfilePvi = interaction.pendingPviIndex();
            editorState.pendingClickX = interaction.pendingClickX();
            editorState.pendingClickY = interaction.pendingClickY();
            if (interaction.contextMenuPviIndex() >= 0) {
                editorState.contextMenuPvi = interaction.contextMenuPviIndex();
            }
            applyProfilePointInsert(ctx, editingNetwork, road, chartData, interaction);
            updateProfileCurveHandleDraft(interaction);
            renderProfilePviContextMenu(ctx, editingNetwork, road);
        }
        if (interaction.activeIntersectionDragIndex() >= 0) {
            activeIntersectionDragIndex = interaction.activeIntersectionDragIndex();
            activeIntersectionDragTarget = interaction.activeIntersectionDragTarget();
        }
        applyIntersectionDrag(config, intersections, interaction);
        if (!flatMode
                && interaction.draggedElevation() != null && interaction.draggedLocalDistance() != null
                && editorState.selectedProfilePvi >= 0
                && effectiveAlignment != null
                && editorState.selectedProfilePvi < effectiveAlignment.pviCount()) {
            editorState.selectedProfileElevation[0] = interaction.draggedElevation().floatValue();
            double currentStation = effectiveAlignment.getPvis()
                .get(editorState.selectedProfilePvi).getStation();
            double requestedStation = interaction.draggedLocalDistance();
            ProfileControlPoint draggedPoint = points.stream()
                .filter(point -> point.pviIndex() == editorState.selectedProfilePvi)
                .findFirst()
                .orElse(null);
            if (draggedPoint != null && draggedPoint.sharedJunction()) {
                requestedStation = currentStation;
            }
            if (draggedPoint != null && (draggedPoint.role() == ProfilePointRole.START_ENDPOINT
                    || draggedPoint.role() == ProfilePointRole.LOOP_SEAM_START)) {
                requestedStation = 0.0;
            } else if (draggedPoint != null && (draggedPoint.role() == ProfilePointRole.END_ENDPOINT
                    || draggedPoint.role() == ProfilePointRole.LOOP_SEAM_END)) {
                requestedStation = RoadStationing.canonicalLength(editingNetwork, road);
            }
            if (draggedPoint != null
                    && VerticalProfileControlPoints.isEditablePvi(editingNetwork, road, draggedPoint)) {
                profileEditSession.updatePviDrag(
                    editingNetwork,
                    road,
                    draggedPoint,
                    editorState.selectedProfilePvi,
                    requestedStation,
                    interaction.draggedElevation());
            }
        }
        if (interaction.dragFinished()) {
            profileEditSession.commitEdit(
                editingNetwork, road, config, windowSession,
                () -> propagateJunctionGrades(ctx, editingNetwork, road),
                this::notifyWorkingProfileChanged);
            editorState.activeCurveHandlePvi = -1;
            editorState.activeCurveHandle =
                ProfileChartInteraction.CurveHandleSide.NONE;
        }
        if (interaction.curveHandleDragFinished()) {
            profileEditSession.commitEdit(
                editingNetwork, road, config, windowSession,
                () -> propagateJunctionGrades(ctx, editingNetwork, road),
                this::notifyWorkingProfileChanged);
            editorState.activeCurveHandlePvi = -1;
            editorState.activeCurveHandle =
                ProfileChartInteraction.CurveHandleSide.NONE;
        }
        if (interaction.activeCurveHandlePvi() >= 0) {
            editorState.activeCurveHandlePvi = interaction.activeCurveHandlePvi();
            editorState.activeCurveHandle = interaction.activeCurveHandle();
        }
        if (interaction.intersectionDragFinished()) {
            Road otherRoad = activeIntersectionDragIndex >= 0
                    && activeIntersectionDragIndex < intersections.size()
                ? editingNetwork.getRoad(intersections.get(activeIntersectionDragIndex).otherRoadId())
                : null;
            profileEditSession.commitEdit(
                editingNetwork,
                road,
                config,
                windowSession,
                null,
                () -> {
                    if (otherRoad != null) {
                        propagateJunctionGrades(ctx, editingNetwork, otherRoad);
                    }
                    notifyWorkingProfileChanged();
                    ctx.requestOverlayRefresh();
                });
            activeIntersectionDragIndex = -1;
            activeIntersectionDragTarget =
                ProfileChartInteraction.IntersectionDragTarget.NONE;
        }
        selectedIntersectionIndex = interaction.selectedIntersectionIndex();
        renderEditorLegend(ctx, road, design, intersections, flatOverlay, flatMode);
        ProfileQualitySummary.render(
            effectiveChart,
            road,
            config,
            isChartDerivedDataStale(road) || ctx.previewManager().needsPreviewRecalc());
        renderAlignmentViolations(editingNetwork, road, flatMode);
        if (profileEditSession.isActive() && !profileEditSession.constraintResult().unresolvedConstraints().isEmpty()) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.WARNING, PlotI18n.tr(
                "plugin.road.vertical_alignment_network_unresolved",
                profileEditSession.constraintResult().unresolvedConstraints().size()));
        }
        renderIntersectionDetail(ctx, editingNetwork, road, intersections, interaction, config, flatMode);
        if (flatMode) {
            flatProfileControls.render(
                ctx,
                editingNetwork,
                road,
                windowSession,
                FlatElevationEditHooks.forProfileEditor(
                    this::recordUndoPointBeforeEdit,
                    () -> {
                        propagateJunctionGrades(ctx, editingNetwork, road);
                        notifyWorkingProfileChanged();
                    }),
                FlatElevationRecommendationUi.terrainSupplier(ctx));
        } else {
            adaptiveProfileControls.render(
                ctx,
                editingNetwork,
                road,
                config,
                profileEditSession,
                windowSession,
                legacyPoints,
                maxGrade,
                editorState,
                this::recordUndoPointBeforeEdit,
                () -> propagateJunctionGrades(ctx, editingNetwork, road),
                this::notifyWorkingProfileChanged,
                () -> deleteProfilePvi(ctx, editingNetwork, road, editorState.selectedProfilePvi));
        }
        renderBuildPreviewStaleBar(ctx, liveNetwork);
    }

    private void renderAlignmentViolations(RoadNetwork network, Road road, boolean flatMode) {
        if (flatMode || road == null || network == null) {
            return;
        }
        RoadVerticalAlignment alignment = profileEditSession.effectiveAlignment(road);
        if (alignment == null || alignment.isEmpty()) {
            return;
        }
        double roadLength = RoadStationing.canonicalLength(network, road);
        List<VerticalAlignmentViolation> violations =
            VerticalAlignmentValidator.validate(alignment, roadLength);
        if (violations.isEmpty()) {
            return;
        }
        ImGui.spacing();
        if (ImGui.collapsingHeader(
                PlotI18n.tr("plugin.road.profile_alignment_issues", violations.size())
                    + "##profile_alignment_issues",
                ImGuiTreeNodeFlags.DefaultOpen)) {
            for (VerticalAlignmentViolation violation : violations) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.WARNING,
                    ProfileAlignmentViolationFormatter.format(violation));
            }
        }
    }

    private static void renderWaterConstraintWarning(RoadProfileChartData chartData) {
        if (chartData == null || chartData.waterConstraintFeasible()) {
            return;
        }
        ImGui.spacing();
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.WARNING,
            PlotI18n.tr("plugin.road.profile_water_constraint_infeasible"));
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_water_constraint_infeasible_hint"));
    }

    private static void renderManualEndpointConstraintWarning(RoadProfileChartData chartData) {
        if (chartData == null || chartData.manualEndpointConstraintFeasible()) {
            return;
        }
        ImGui.spacing();
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.WARNING,
            PlotI18n.tr("plugin.road.profile_endpoint_constraint_infeasible"));
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_endpoint_constraint_infeasible_hint"));
    }

    private void renderBuildPreviewStaleBar(RoadUiContext ctx, RoadNetwork network) {
        boolean dirty = windowSession != null && windowSession.isDirty() || profileEditSession.isDirty();
        if (!dirty && !ctx.previewManager().needsPreviewRecalc()) {
            return;
        }
        ImGui.separator();
        ImGui.textColored(
            PluginUiColors.WARNING_LIGHT,
            PlotI18n.tr("plugin.road.profile_build_preview_stale"));
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_build_preview_stale_hint"));
        if (ImGui.button(
                PlotI18n.tr("plugin.road.update_road_preview") + "##profile_editor_update_preview",
                ImGui.getContentRegionAvailX(),
                0)) {
            if (dirty && windowSession != null) {
                Road current = windowSession.editingNetwork().getRoad(editorRoadId);
                if (current != null) profileEditSession.commitEdit(windowSession.editingNetwork(), current,
                    ctx.networkManager().getConfig(), windowSession, null, this::notifyWorkingProfileChanged);
                if (confirmWindowSession(ctx, network, true)) reopenWindowSession(ctx, network);
            } else {
                ctx.previewManager().startNetworkPreview(network);
            }
        }
    }

    private void applyIntersectionDrag(
            RoadSystemConfig config,
            List<RoadProfileIntersection> intersections,
            ProfileChartInteraction.ControlInteraction interaction) {
        if (interaction.draggedIntersectionElevation() == null
                || activeIntersectionDragIndex < 0
                || activeIntersectionDragIndex >= intersections.size()) {
            return;
        }
        RoadProfileIntersectionDragEditor.DragTarget dragTarget = mapIntersectionDragTarget(
            activeIntersectionDragTarget);
        if (dragTarget == null) {
            return;
        }
        profileEditSession.updateIntersectionDrag(
            activeIntersectionDragIndex,
            dragTarget,
            interaction.draggedIntersectionElevation());
    }

    private void updateProfileCurveHandleDraft(
            ProfileChartInteraction.ControlInteraction interaction) {
        if (interaction.draggedCurveLength() == null || interaction.activeCurveHandlePvi() < 0) {
            return;
        }
        profileEditSession.updateCurveLength(interaction.draggedCurveLength());
    }

    private static RoadProfileIntersectionDragEditor.DragTarget mapIntersectionDragTarget(
            ProfileChartInteraction.IntersectionDragTarget target) {
        if (target == null) {
            return null;
        }
        return switch (target) {
            case CURRENT -> RoadProfileIntersectionDragEditor.DragTarget.CURRENT;
            case OTHER -> RoadProfileIntersectionDragEditor.DragTarget.OTHER;
            case SHARED -> RoadProfileIntersectionDragEditor.DragTarget.SHARED;
            case NONE -> null;
        };
    }

    private void renderIntersectionSelector(List<RoadProfileIntersection> intersections) {
        if (intersections.isEmpty()) return;
        String preview = selectedIntersectionIndex >= 0 && selectedIntersectionIndex < intersections.size()
            ? intersectionLabel(intersections.get(selectedIntersectionIndex))
            : PlotI18n.tr("plugin.road.profile_intersection_select");
        if (ImGui.beginCombo(PlotI18n.tr("plugin.road.profile_intersection_picker"), preview)) {
            for (int i = 0; i < intersections.size(); i++) {
                if (ImGui.selectable(intersectionLabel(intersections.get(i)) + "##crossing_" + i,
                        i == selectedIntersectionIndex)) {
                    selectedIntersectionIndex = i;
                    editorState.selectedProfilePvi = -1;
                    intersectionInputKey = "";
                }
            }
            ImGui.endCombo();
        }
    }

    private static String intersectionLabel(RoadProfileIntersection intersection) {
        return intersection.otherRoadLabel() + " @ " + String.format("%.2f", intersection.roadStation());
    }

    private void renderIntersectionHeightEdit(RoadUiContext ctx, RoadNetwork network, Road road,
            List<RoadProfileIntersection> intersections, int index, RoadSystemConfig config, boolean flatMode) {
        var intersection = intersections.get(index);
        String key = road.getId() + ":" + intersection.nodeId() + ":" + intersection.roadStation();
        if (!key.equals(intersectionInputKey)) {
            intersectionInputKey = key;
            intersectionInputTarget = 0;
            intersectionInputDirty = false;
        }
        if (intersection.gradeSeparated() && ImGui.beginCombo(
                PlotI18n.tr("plugin.road.profile_intersection_edit_target"),
                PlotI18n.tr(intersectionInputTarget == 0 ? "plugin.road.profile_intersection_target_current"
                    : "plugin.road.profile_intersection_target_other"))) {
            for (int target = 0; target < 2; target++) {
                if (ImGui.selectable(PlotI18n.tr(target == 0 ? "plugin.road.profile_intersection_target_current"
                        : "plugin.road.profile_intersection_target_other"), intersectionInputTarget == target)) {
                    intersectionInputTarget = target;
                    intersectionInputDirty = false;
                }
            }
            ImGui.endCombo();
        }
        if (!intersectionInputDirty) intersectionHeightInput.set((float) (intersectionInputTarget == 1
            && intersection.gradeSeparated() ? intersection.otherRoadElevation() : intersection.currentRoadElevation()));
        if (ImGui.inputFloat(PlotI18n.tr("plugin.road.profile_intersection_height_edit"),
                intersectionHeightInput, 0.1f, 1f, "%.2f")) intersectionInputDirty = true;
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_intersection_apply_height"))
                && Float.isFinite(intersectionHeightInput.get())) {
            recordUndoPointBeforeEdit();
            profileEditSession.setElevationBounds(resolveElevationBounds(ctx));
            profileEditSession.beginIntersectionEdit(intersections);
            var target = !intersection.gradeSeparated() ? RoadProfileIntersectionDragEditor.DragTarget.SHARED
                : intersectionInputTarget == 0 ? RoadProfileIntersectionDragEditor.DragTarget.CURRENT
                : RoadProfileIntersectionDragEditor.DragTarget.OTHER;
            profileEditSession.updateIntersectionDrag(index, target, intersectionHeightInput.get());
            profileEditSession.commitEdit(network, road, config, windowSession, null, this::notifyWorkingProfileChanged);
            intersectionInputDirty = false;
            ctx.requestOverlayRefresh();
        }
        if (!flatMode && VerticalAlignmentGeometry.isEvaluable(road.getVerticalAlignment())) {
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.road.profile_intersection_smooth"))) {
                recordUndoPointBeforeEdit();
                var before = road.getVerticalAlignment();
                com.plot.plugin.road.vertical.RoadProfileConstraintSynchronizer.setElevation(
                    road, intersection.roadStation(), intersection.currentRoadElevation());
                int pvi = findPviIndexAtStation(road.getVerticalAlignment(), intersection.roadStation());
                var fitted = com.plot.plugin.road.vertical.VerticalProfileCurveFitter.fitAt(road.getVerticalAlignment(), pvi);
                editorState.profileAutoFixMessage = PlotI18n.tr(fitted.hasSpace()
                    ? "plugin.road.vertical_alignment_auto_smooth_success" : "plugin.road.vertical_alignment_auto_smooth_no_space");
                road.setVerticalAlignment(fitted.alignment());
                road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
                com.plot.plugin.road.vertical.RoadProfileConstraintSynchronizer.synchronize(
                    network, road, before, config, resolveElevationBounds(ctx));
                editorState.selectedProfilePvi = pvi;
                if (windowSession != null) windowSession.markPropagationAdjustedRoads(network);
                notifyWorkingProfileChanged();
                ctx.requestOverlayRefresh();
            }
        }
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_intersection_numeric_hint"));
        if (!Objects.equals(road.getId(), intersection.otherRoadId())
                && ImGui.button(PlotI18n.tr("plugin.road.profile_intersection_switch_road"))) {
            selectEditorRoad(ctx, intersection.otherRoadId(), true);
        }
    }

    private void renderIntersectionDetail(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            List<RoadProfileIntersection> intersections,
            ProfileChartInteraction.ControlInteraction interaction,
            RoadSystemConfig config,
            boolean flatMode) {
        int detailIndex = selectedIntersectionIndex >= 0
            ? selectedIntersectionIndex
            : interaction.hoveredIntersectionIndex();
        if (detailIndex < 0 || detailIndex >= intersections.size()) {
            return;
        }
        boolean editable = selectedIntersectionIndex >= 0;
        RoadProfileIntersection intersection = intersections.get(detailIndex);
        ImGui.spacing();
        ImGui.separator();
        ImGui.text(PlotI18n.tr(
            "plugin.road.profile_intersection_title",
            intersection.otherRoadLabel()));
        if (!editable) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_intersection_click_to_edit"));
        } else if (intersection.gradeSeparated()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING_LIGHT,
                PlotI18n.tr("plugin.road.profile_editor_affect_current_only"));
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_intersection_drag_grade_hint"));
        } else {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING_LIGHT,
                PlotI18n.tr(
                    "plugin.road.profile_editor_affect_intersection",
                    intersection.otherRoadLabel()));
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_intersection_drag_shared_hint"));
        }
        ImGui.text(PlotI18n.tr(
            "plugin.road.profile_intersection_current_elevation",
            String.format("%.1f", intersection.currentRoadElevation())));
        ImGui.text(PlotI18n.tr(
            "plugin.road.profile_intersection_other_elevation",
            String.format("%.1f", intersection.otherRoadElevation())));
        if (intersection.gradeSeparated()) {
            String relation = intersection.currentRoadElevated()
                ? PlotI18n.tr("plugin.road.profile_intersection_current_over")
                : PlotI18n.tr("plugin.road.profile_intersection_other_over");
            ImGui.text(PlotI18n.tr("plugin.road.profile_intersection_relation", relation));
            double requiredClearance = requiredClearanceForIntersection(network, intersection, config);
            double actualClearance = intersection.clearanceGap();
            ImGui.text(PlotI18n.tr(
                "plugin.road.profile_intersection_required_clearance",
                String.format("%.0f", requiredClearance)));
            ImGui.text(PlotI18n.tr(
                "plugin.road.profile_intersection_actual_clearance",
                String.format("%.1f", actualClearance)));
            if (actualClearance + 1e-6 < requiredClearance) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.WARNING,
                    PlotI18n.tr("plugin.road.profile_intersection_clearance_insufficient"));
            }
            if (intersection.steepGradeWarning()) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.WARNING,
                    PlotI18n.tr("plugin.road.crossing_warning_steep"));
            }
        } else if (!editable) {
            ImGui.text(PlotI18n.tr("plugin.road.profile_intersection_at_grade"));
        }
        if (flatMode && intersection.steepGradeWarning()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.road.profile_flat_junction_transition_warning"));
        }
        if (editable) {
            renderIntersectionHeightEdit(ctx, network, road, intersections, detailIndex, config, flatMode);
            boolean crossingChanged = false;
            if (gradeSeparationControls == null) {
                gradeSeparationControls = new RoadGradeSeparationControls(ctx);
            }
            if (com.plot.plugin.road.crossing.RoadCrossingRef.isCrossingRef(intersection.nodeId())) {
                com.plot.plugin.road.crossing.RoadCrossing crossing = network.getCrossing(
                    com.plot.plugin.road.crossing.RoadCrossingRef.crossingIdFromRef(intersection.nodeId()));
                if (crossing != null) {
                    crossingChanged = gradeSeparationControls.renderCrossing(
                        crossing, network, config, RoadGradeSeparationControls.Layout.PROFILE, windowSession);
                }
            } else if (com.plot.plugin.road.crossing.RoadStackCrossingRef.isStackCrossingRef(intersection.nodeId())) {
                com.plot.plugin.road.crossing.RoadStackCrossing stack = network.getStackCrossing(
                    com.plot.plugin.road.crossing.RoadStackCrossingRef.stackCrossingIdFromRef(intersection.nodeId()));
                if (stack != null) {
                    crossingChanged = gradeSeparationControls.renderStackCrossing(
                        stack, network, config, RoadGradeSeparationControls.Layout.PROFILE, windowSession);
                }
            } else {
                RoadNode node = network.getNode(intersection.nodeId());
                if (node != null) {
                    crossingChanged = gradeSeparationControls.renderLegacyJunction(
                        node, network, config, RoadGradeSeparationControls.Layout.PROFILE, windowSession);
                }
            }
            if (crossingChanged) {
                var result = com.plot.plugin.road.vertical.RoadProfileConstraintSynchronizer.synchronize(
                    network, road, road.getVerticalAlignment(), config, resolveElevationBounds(ctx));
                if (windowSession != null) windowSession.markPropagationAdjustedRoads(network);
                if (!result.unresolvedConstraints().isEmpty()) {
                    editorState.profileAutoFixMessage = PlotI18n.tr(
                        "plugin.road.vertical_alignment_network_unresolved", result.unresolvedConstraints().size());
                }
                notifyWorkingProfileChanged();
            }
        }
        if (ImGui.collapsingHeader(
                PlotI18n.tr("plugin.road.profile_intersection_detail")
                    + "##profile_intersection_detail_" + detailIndex)) {
            ImGui.text(PlotI18n.tr(
                "plugin.road.profile_intersection_other_section",
                intersection.otherCrossSection().laneCount,
                Math.round(RoadCrossSectionPreviewRenderer.CrossSectionLayout
                    .fromResolved(intersection.otherCrossSection(), 0f)
                    .totalWidthBlocks())));
            float previewWidth = Math.min(ImGui.getContentRegionAvail().x, 280f);
            float previewHeight = 56f;
            ImDrawList drawList = ImGui.getWindowDrawList();
            ImVec2 cursor = ImGui.getCursorScreenPos();
            RoadCrossSectionPreviewRenderer.renderMini(
                drawList,
                RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromResolved(
                    intersection.otherCrossSection(), 0f),
                cursor.x,
                cursor.y,
                previewWidth,
                previewHeight);
            ImGui.dummy(previewWidth, previewHeight);
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_intersection_cross_section_hint"));
        }
    }

    private static double requiredClearanceForIntersection(
            RoadNetwork network,
            RoadProfileIntersection intersection,
            RoadSystemConfig config) {
        if (com.plot.plugin.road.crossing.RoadStackCrossingRef.isStackCrossingRef(intersection.nodeId())) {
            var stack = network.getStackCrossing(
                com.plot.plugin.road.crossing.RoadStackCrossingRef.stackCrossingIdFromRef(intersection.nodeId()));
            return stack != null && stack.crossingClearance() != null
                ? stack.crossingClearance() : config.getDefaultCrossingClearance();
        }
        if (com.plot.plugin.road.crossing.RoadCrossingRef.isCrossingRef(intersection.nodeId())) {
            com.plot.plugin.road.crossing.RoadCrossing crossing = network.getCrossing(
                com.plot.plugin.road.crossing.RoadCrossingRef.crossingIdFromRef(intersection.nodeId()));
            return RoadProfileIntersectionDragEditor.requiredClearance(crossing, config);
        }
        return RoadProfileIntersectionDragEditor.requiredClearance(
            network.getNode(intersection.nodeId()), config);
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

    private void applyProfilePointInsert(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadProfileChartData chartData,
            ProfileChartInteraction.ControlInteraction interaction) {
        if (!interaction.addPointRequested()
                || interaction.addPointLocalDistance() == null
                || interaction.addPointElevation() == null) {
            return;
        }
        recordUndoPointBeforeEdit();
        double roadLength = RoadStationing.canonicalLength(network, road);
        double insertStation = interaction.addPointLocalDistance();
        double startElevation = resolveBootstrapEndpointElevation(network, road, chartData, 0.0);
        double endElevation = resolveBootstrapEndpointElevation(
            network, road, chartData, roadLength);
        RoadElevationBounds bounds = resolveElevationBounds(ctx);
        double insertElevation = bounds.clamp(interaction.addPointElevation());
        VerticalProfileControlPoints.InsertResult result = VerticalProfileControlPoints.tryBootstrapOrInsert(
            road.getVerticalAlignment(),
            roadLength,
            startElevation,
            endElevation,
            insertStation,
            insertElevation);
        if (!result.success()) {
            ctx.status().warning(insertFailureMessage(result.reason()));
            return;
        }
        road.setVerticalAlignment(result.alignment());
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        if (windowSession != null) {
            windowSession.markTouched(road);
        }
        editorState.selectedProfilePvi = findNearestPviIndex(result.alignment(), insertStation);
        propagateJunctionGrades(ctx, network, road);
        notifyWorkingProfileChanged();
    }

    private void renderProfileInteractionHint() {
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_editor_interaction_hint_short"));
        ImGui.sameLine();
        ImGui.textDisabled("?");
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.profile_editor_interaction_hint"));
        }
    }

    static boolean canDeleteProfilePvi(RoadNetwork network, Road road, int pviIndex) {
        return VerticalProfileControlPoints.canDelete(network, road, pviIndex);
    }

    private void deleteProfilePvi(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            int pviIndex) {
        if (!canDeleteProfilePvi(network, road, pviIndex)) {
            return;
        }
        recordUndoPointBeforeEdit();
        road.setVerticalAlignment(VerticalProfileControlPoints.removeAt(
            road.getVerticalAlignment(), pviIndex));
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        if (windowSession != null) {
            windowSession.markTouched(road);
        }
        editorState.selectedProfilePvi = -1;
        editorState.activeProfilePvi = -1;
        editorState.contextMenuPvi = -1;
        propagateJunctionGrades(ctx, network, road);
        notifyWorkingProfileChanged();
    }

    private static String insertFailureMessage(
            VerticalProfileControlPoints.InsertFailureReason reason) {
        if (reason == null) {
            return PlotI18n.tr("plugin.road.profile_pvi_insert_failed");
        }
        return switch (reason) {
            case INSUFFICIENT_SPACE -> PlotI18n.tr("plugin.road.profile_pvi_insert_insufficient_space");
            case DUPLICATE_STATION, TOO_CLOSE_TO_NEIGHBOR ->
                PlotI18n.tr("plugin.road.profile_pvi_insert_too_close");
            case INVALID_INPUT -> PlotI18n.tr("plugin.road.profile_pvi_insert_invalid");
            case ROAD_TOO_SHORT -> PlotI18n.tr("plugin.road.profile_pvi_insert_road_too_short");
        };
    }

    private void renderProfilePviContextMenu(RoadUiContext ctx, RoadNetwork network, Road road) {
        if (editorState.contextMenuPvi < 0) {
            return;
        }
        if (!ImGui.beginPopup("##road_profile_pvi_context")) {
            return;
        }
        int pviIndex = editorState.contextMenuPvi;
        var alignment = road.getVerticalAlignment();
        if (alignment == null || pviIndex >= alignment.pviCount()) {
            ImGui.endPopup();
            return;
        }
        var point = alignment.getPvis().get(pviIndex);
        var bounds = resolveElevationBounds(ctx);
        if (ImGui.isWindowAppearing()) pviHeightInput.set((float) VerticalAlignmentGeometry
            .elevationAt(alignment, point.getStation()).orElse(point.getElevation()));
        boolean locked = point.getConstraint() == com.plot.plugin.road.vertical.VerticalControlPointConstraint.USER_LOCKED;
        ImGui.beginDisabled(locked);
        ImGui.inputFloat(PlotI18n.tr("plugin.road.profile_pvi_elevation_input"), pviHeightInput, 0.1f, 1f, "%.2f");
        ImGui.textDisabled(PlotI18n.tr("plugin.road.profile_pvi_elevation_range", bounds.minY(), bounds.maxY()));
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_intersection_apply_height")) && Float.isFinite(pviHeightInput.get())) {
            recordUndoPointBeforeEdit();
            profileEditSession.setElevationBounds(bounds);
            profileEditSession.beginNumericEdit(road);
            profileEditSession.updatePviElevation(network, road, pviIndex, bounds.clamp(pviHeightInput.get()));
            profileEditSession.commitEdit(network, road, ctx.networkManager().getConfig(), windowSession, null,
                this::notifyWorkingProfileChanged);
            pviHeightInput.set((float) bounds.clamp(pviHeightInput.get()));
            ctx.requestOverlayRefresh();
        }
        ImGui.endDisabled();
        ImGui.separator();
        boolean canDelete = canDeleteProfilePvi(network, road, pviIndex);
        if (!canDelete) {
            ImGui.textDisabled(PlotI18n.tr("plugin.road.profile_pvi_delete_disabled"));
        } else if (ImGui.menuItem(PlotI18n.tr("plugin.road.profile_pvi_delete"))) {
            deleteProfilePvi(ctx, network, road, pviIndex);
        }
        ImGui.endPopup();
    }

    private static int resolveCurveHandlePviIndex(
            Road road,
            RoadVerticalAlignment alignment,
            int selectedPvi,
            int selectedIntersection,
            List<RoadProfileIntersection> intersections) {
        if (road == null || alignment == null) {
            return -1;
        }
        if (selectedPvi > 0 && selectedPvi < alignment.pviCount() - 1
                && VerticalProfileControlPoints.isCurveEditable(road, alignment, selectedPvi)) {
            return selectedPvi;
        }
        if (selectedIntersection >= 0 && intersections != null
                && selectedIntersection < intersections.size()) {
            int atStation = findPviIndexAtStation(
                alignment, intersections.get(selectedIntersection).roadStation());
            if (atStation > 0 && atStation < alignment.pviCount() - 1
                    && VerticalProfileControlPoints.isCurveEditable(road, alignment, atStation)) {
                return atStation;
            }
            double station = intersections.get(selectedIntersection).roadStation();
            for (int i = 1; i < alignment.pviCount() - 1; i++) {
                var pvi = alignment.getPvis().get(i);
                if (pvi.hasCurve() && Math.abs(station - pvi.getStation()) <= pvi.getCurveLength() / 2
                        && VerticalProfileControlPoints.isCurveEditable(road, alignment, i)) return i;
            }
        }
        return selectedPvi;
    }

    private static int findPviIndexAtStation(RoadVerticalAlignment alignment, double station) {
        if (alignment == null) {
            return -1;
        }
        for (int i = 0; i < alignment.pviCount(); i++) {
            if (Math.abs(alignment.getPvis().get(i).getStation() - station) <= 0.26) {
                return i;
            }
        }
        return -1;
    }

    private static List<ProfileChartInteraction.CurveHandle> buildRoadCurveHandles(
            RoadNetwork network,
            Road road,
            RoadVerticalAlignment alignment,
            List<ProfileControlPoint> points,
            int selectedPvi) {
        if (road == null || alignment == null || selectedPvi <= 0) {
            return List.of();
        }
        if (selectedPvi >= alignment.pviCount() - 1) {
            return List.of();
        }
        ProfileControlPoint selected = points.stream()
            .filter(point -> point.pviIndex() == selectedPvi)
            .findFirst()
            .orElse(null);
        if (selected == null || !VerticalProfileControlPoints.isCurveEditable(road, alignment, selectedPvi)) {
            return List.of();
        }
        PointOfVerticalIntersection pvi = alignment.getPvis().get(selectedPvi);
        double half = pvi.hasCurve() ? pvi.getCurveLength() * 0.5 : 4.0;
        double bvcStation = pvi.getStation() - half;
        double evcStation = pvi.getStation() + half;
        double bvcElevation = VerticalAlignmentGeometry
            .elevationAt(alignment, bvcStation)
            .orElse(pvi.getElevation());
        double evcElevation = VerticalAlignmentGeometry
            .elevationAt(alignment, evcStation)
            .orElse(pvi.getElevation());
        return List.of(
            new ProfileChartInteraction.CurveHandle(
                selectedPvi, bvcStation, bvcElevation, true),
            new ProfileChartInteraction.CurveHandle(
                selectedPvi, evcStation, evcElevation, false));
    }

    private static int findNearestPviIndex(RoadVerticalAlignment alignment, double station) {
        if (alignment == null || alignment.pviCount() == 0) {
            return -1;
        }
        int nearest = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < alignment.pviCount(); i++) {
            double distance = Math.abs(alignment.getPvis().get(i).getStation() - station);
            if (distance < best) {
                best = distance;
                nearest = i;
            }
        }
        return nearest;
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
        profileRenderCache = null;
        resetEditorLocalState();
        invalidateIntersectionCache();
        cachedChartData = null;
        cachedChartRoadId = "";
        notifyWorkingProfileChanged();
        ctx.requestOverlayRefresh();
    }

    private void redoEditorSession(RoadUiContext ctx) {
        if (windowSession == null || !windowSession.canRedo()) {
            return;
        }
        windowSession.redo();
        profileEditSession.cancelEdit();
        profileRenderCache = null;
        resetEditorLocalState();
        invalidateIntersectionCache();
        cachedChartData = null;
        cachedChartRoadId = "";
        notifyWorkingProfileChanged();
        ctx.requestOverlayRefresh();
    }

    private void notifyWorkingProfileChanged() {
        profileRenderCache = null;
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
