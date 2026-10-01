package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadLongitudinalProfileRenderer;
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
import com.plot.plugin.road.profile.ProfileRenderCache;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.profile.RoadProfileChartRenderer;
import com.plot.plugin.road.profile.RoadProfileRoadList;
import com.plot.plugin.road.profile.RoadProfileRoadNavigator;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.RoadProfileIntersectionDragEditor;
import com.plot.plugin.road.profile.RoadProfileIntersectionResolver;
import com.plot.plugin.road.profile.RoadProfileIntersectionWarningResolver;
import com.plot.plugin.road.profile.edit.ProfileEditSession;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.world.World;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.road.vertical.FlatElevationProfileOverlay;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;
import com.plot.plugin.road.vertical.VerticalProfileNetworkPropagator;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
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

    private static final float INLINE_CHART_HEIGHT = 128f;
    private static final float MIN_EDITOR_CHART_HEIGHT = 220f;
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
    private boolean profileNetworkEditPending = false;
    private Runnable onAlignmentCommitted;
    private int selectedIntersectionIndex = -1;
    private RoadGradeSeparationControls gradeSeparationControls;
    private int activeIntersectionDragIndex = -1;
    private RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget
        activeIntersectionDragTarget =
            RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget.NONE;
    private String cachedIntersectionsRoadId = "";
    private long cachedIntersectionsKey = Long.MIN_VALUE;
    private List<RoadProfileIntersection> cachedIntersections = List.of();
    private RoadGenerator cachedGradeSeparationGenerator;
    private long cachedGradeSeparationGeneratorKey = Long.MIN_VALUE;
    private ProfileRenderCache profileRenderCache;
    private Function<Road, FlatElevationProfileOverlay> flatOverlayResolver = road -> null;

    void clearCache() {
        cachedChartData = null;
        cachedChartRoadId = "";
        editorState.reset();
        profileEditSession.cancelEdit();
        profileRenderCache = null;
        selectedIntersectionIndex = -1;
        activeIntersectionDragIndex = -1;
        activeIntersectionDragTarget =
            RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget.NONE;
        profileNetworkEditPending = false;
        invalidateIntersectionCache();
    }

    void openEditorForEdge(String edgeId) {
        if (edgeId == null || edgeId.isBlank()) {
            return;
        }
        editorRoadId = "";
        editorWindowOpen.set(true);
        focusEditorOnOpen = true;
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
        openEditorForEdge(edgeId);
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

    void setOnAlignmentCommitted(Runnable onAlignmentCommitted) {
        this.onAlignmentCommitted = onAlignmentCommitted;
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

    void renderInline(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            FlatElevationProfileOverlay flatOverlay) {
        renderInline(ctx, network, road, flatOverlay, false);
    }

    void renderInline(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            FlatElevationProfileOverlay flatOverlay,
            boolean workspaceEmbedded) {
        if (!workspaceEmbedded) {
            ImGui.spacing();
            if (!ImGui.collapsingHeader(
                    PlotI18n.tr("plugin.road.vertical_alignment_profile_editor"),
                    ImGuiTreeNodeFlags.DefaultOpen)) {
                return;
            }
        } else {
            ImGui.spacing();
        }
        if (road == null) {
            return;
        }
        if (!RoadStationing.isStationable(network, road)) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_discontinuous_road_hint"));
            return;
        }
        renderRoadSummary(ctx, network, road);
        if (ImGui.button(PlotI18n.tr("plugin.road.vertical_alignment_open_editor"),
                ImGui.getContentRegionAvailX(), 0)) {
            openEditorForRoad(ctx, road.getId());
        }
        RoadProfileChartData chartData = resolveChartData(ctx, network, road);
        if (chartData == null || !chartData.hasProfileData()) {
            renderMissingProfileActions(ctx, network, road);
            return;
        }
        VerticalAlignmentProfileOverlay design =
            VerticalAlignmentProfileOverlay.forRoad(network, road).orElse(null);
        RoadSystemConfig config = ctx.networkManager().getConfig();
        List<RoadProfileIntersection> intersections = resolveIntersections(
            ctx, network, road, config, chartData, true);
        RoadProfileChartRenderer.renderOverview(
            chartData, design, intersections, INLINE_CHART_HEIGHT, flatOverlay);
        boolean flatMode = RoadVerticalStrategy.fromRoad(road) == RoadVerticalStrategy.FLAT;
        renderInlineLegend(
            design,
            intersections,
            flatOverlay,
            flatMode,
            ctx.previewManager().needsPreviewRecalc());
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.vertical_alignment_inline_preview_hint"));
    }

    void renderEditorWindow(RoadUiContext ctx, RoadNetwork network) {
        if (!editorWindowOpen.get()) {
            finishPendingNetworkEdit(ctx);
            return;
        }
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

        if (!ImGui.begin(
                PlotI18n.tr("plugin.road.vertical_alignment_editor_window") + "###road_profile_editor",
                editorWindowOpen,
                EDITOR_WINDOW_FLAGS)) {
            finishPendingNetworkEdit(ctx);
            ImGui.end();
            return;
        }
        try {
            renderEditorRoadSelector(ctx, network);
            Road road = editorRoadId != null && !editorRoadId.isBlank()
                ? network.getRoad(editorRoadId)
                : null;
            if (road == null) {
                editorWindowOpen.set(false);
                finishPendingNetworkEdit(ctx);
                return;
            }
            ImGui.spacing();
            renderRoadSummary(ctx, network, road);
            ImGui.spacing();
            if (!RoadStationing.isStationable(network, road)) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr("plugin.road.profile_discontinuous_road_hint"));
                return;
            }
            RoadProfileChartData chartData = resolveChartData(ctx, network, road);
            if (chartData == null || !chartData.hasProfileData()) {
                renderMissingProfileActions(ctx, network, road);
                return;
            }
            FlatElevationProfileOverlay flatOverlay = resolveFlatOverlay(road);
            VerticalAlignmentProfileOverlay design =
                profileEditSession.effectiveDesignOverlay(network, road).orElse(null);
            float chartHeight = Math.max(
                MIN_EDITOR_CHART_HEIGHT,
                ImGui.getContentRegionAvail().y * 0.42f);
            renderInteractiveEditor(ctx, network, road, chartData, design, chartHeight, flatOverlay);
        } finally {
            if (!editorWindowOpen.get()) {
                finishPendingNetworkEdit(ctx);
            }
            ImGui.end();
        }
    }

    private void beginProfileNetworkEdit(RoadUiContext ctx) {
        if (!profileNetworkEditPending) {
            ctx.beginNetworkEdit(RoadChangeKind.VERTICAL_PROFILE);
            profileNetworkEditPending = true;
        }
    }

    private void finishProfileNetworkEdit(RoadUiContext ctx, Runnable propagateJunctionGrades) {
        if (!profileNetworkEditPending && !editorState.elevationEditPending) {
            return;
        }
        if (propagateJunctionGrades != null) {
            propagateJunctionGrades.run();
        }
        ctx.finishNetworkEdit();
        profileNetworkEditPending = false;
        editorState.elevationEditPending = false;
        if (onAlignmentCommitted != null) {
            onAlignmentCommitted.run();
        }
    }

    private void finishPendingNetworkEdit(RoadUiContext ctx) {
        profileEditSession.cancelEdit();
        finishProfileNetworkEdit(ctx, null);
    }

    private void selectEditorRoad(RoadUiContext ctx, String roadId, boolean syncSelection) {
        if (roadId == null || roadId.isBlank()) {
            return;
        }
        if (Objects.equals(editorRoadId, roadId)) {
            return;
        }
        finishPendingNetworkEdit(ctx);
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

    private void renderRoadSummary(RoadUiContext ctx, RoadNetwork network, Road road) {
        ImGui.text(RoadEdgeListHelper.formatRoadLabel(network, road));
        double length = RoadStationing.isStationable(network, road)
            ? RoadStationing.canonicalLength(network, road)
            : RoadEdgeListHelper.computeRoadWorldLength(network, road, ctx.host().coordinates());
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.road.profile_editor_road_summary",
                RoadUiFormat.formatDistance(length),
                RoadVerticalStrategy.fromRoad(road).label()));
    }

    private FlatElevationProfileOverlay resolveFlatOverlay(Road road) {
        return flatOverlayResolver.apply(road);
    }

    private ProfileRenderCache resolveRenderCache(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadProfileChartData chartData,
            VerticalAlignmentProfileOverlay design,
            List<ProfileControlPoint> controls,
            FlatElevationProfileOverlay flatOverlay,
            List<RoadProfileIntersection> intersections) {
        long cacheKey = ProfileRenderCache.computeKey(
            road.getId(),
            ctx.networkManager().getNetworkRevision(),
            ctx.previewManager().getTerrainRevision(),
            chartData);
        if (profileRenderCache != null
                && profileRenderCache.cacheKey() == cacheKey
                && road.getId().equals(editorRoadId)) {
            return profileRenderCache;
        }
        profileRenderCache = ProfileRenderCache.build(
            cacheKey, chartData, design, controls, intersections, flatOverlay);
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
        activeIntersectionDragIndex = -1;
        activeIntersectionDragTarget =
            RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget.NONE;
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

    private static void renderInlineLegend(
            VerticalAlignmentProfileOverlay design,
            List<RoadProfileIntersection> intersections,
            FlatElevationProfileOverlay flatOverlay,
            boolean flatMode,
            boolean buildPreviewStale) {
        String actualRoadLabel = buildPreviewStale
            ? PlotI18n.tr("plugin.road.profile_last_preview_stale")
            : PlotI18n.tr("plugin.road.profile_actual_road");
        String targetLabel = buildPreviewStale
            ? PlotI18n.tr("plugin.road.profile_last_preview_stale")
            : PlotI18n.tr("plugin.road.profile_target");
        ImGui.textColored(0xFF8B5A2B, "■ " + PlotI18n.tr("plugin.road.profile_ground"));
        if (flatMode) {
            ImGui.sameLine();
            ImGui.textColored(0xFFB0B0B0, "■ " + actualRoadLabel);
            if (flatOverlay != null && flatOverlay.showCurrent()) {
                ImGui.sameLine();
                ImGui.textColored(0xFF66D9EF, "--- " + PlotI18n.tr(
                    "plugin.road.profile_flat_base_y",
                    flatOverlay.currentElevation()));
            }
            if (flatOverlay != null && flatOverlay.showSuggested()) {
                ImGui.sameLine();
                ImGui.textColored(0xFFFFB84D, "=== " + PlotI18n.tr(
                    "plugin.road.profile_flat_suggested",
                    flatOverlay.suggestedElevation()));
            }
        } else {
            ImGui.sameLine();
            ImGui.textColored(0xFFB0B0B0, "■ " + actualRoadLabel);
            if (design != null && !design.isEmpty()) {
                ImGui.sameLine();
                ImGui.textColored(0xFF5FD35F, "■ " + PlotI18n.tr("plugin.road.profile_design"));
            }
            if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.profile_advanced_display"))) {
                ImGui.textColored(0xFF4DA3FF, "--- " + PlotI18n.tr("plugin.road.profile_guide"));
                ImGui.sameLine();
                ImGui.textColored(0xFFB0B0B0, "■ " + targetLabel);
            }
        }
        if (intersections != null && !intersections.isEmpty()) {
            boolean hasGradeSeparated = intersections.stream().anyMatch(RoadProfileIntersection::gradeSeparated);
            boolean hasAtGrade = intersections.stream().anyMatch(intersection -> !intersection.gradeSeparated());
            if (hasAtGrade) {
                ImGui.textColored(0xFF66CCFF, "\u25CF " + PlotI18n.tr("plugin.road.profile_intersection_marker"));
            }
            if (hasGradeSeparated) {
                if (hasAtGrade) {
                    ImGui.sameLine();
                }
                ImGui.textColored(0xFFFF9966, "\u25CE " + PlotI18n.tr(
                    "plugin.road.profile_intersection_marker_current"));
                ImGui.sameLine();
                ImGui.textColored(0xFFCC99FF, "\u25C7 " + PlotI18n.tr(
                    "plugin.road.profile_intersection_marker_grade"));
            }
        }
    }

    private static void renderEditorControlLegend(boolean flatMode) {
        if (flatMode) {
            return;
        }
        ImGui.textColored(PluginUiColors.LEGEND, "\u25CF " + PlotI18n.tr("plugin.road.profile_legend_pvi"));
        ImGui.sameLine();
        ImGui.textColored(PluginUiColors.ERROR, "\u25CF " + PlotI18n.tr("plugin.road.profile_legend_pvi_invalid"));
        ImGui.sameLine();
        ImGui.textColored(PluginUiColors.ACCENT_BLUE, "\u25CF " + PlotI18n.tr(
            "plugin.road.profile_legend_pvi_selected"));
        ImGui.sameLine();
        ImGui.textColored(0xFF88DDFF, "\u25A0 " + PlotI18n.tr("plugin.road.profile_legend_curve_handle"));
    }

    private void renderEditorLegend(
            RoadUiContext ctx,
            VerticalAlignmentProfileOverlay design,
            List<RoadProfileIntersection> intersections,
            FlatElevationProfileOverlay flatOverlay,
            boolean flatMode) {
        renderInlineLegend(
            design,
            intersections,
            flatOverlay,
            flatMode,
            ctx.previewManager().needsPreviewRecalc());
        renderEditorControlLegend(flatMode);
    }

    private void renderInteractiveEditor(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadProfileChartData chartData,
            VerticalAlignmentProfileOverlay design,
            float chartHeight,
            FlatElevationProfileOverlay flatOverlay) {
        design = profileEditSession.effectiveDesignOverlay(network, road).orElse(null);
        boolean flatMode = RoadVerticalStrategy.fromRoad(road) == RoadVerticalStrategy.FLAT;
        RoadVerticalAlignment effectiveAlignment = profileEditSession.effectiveAlignment(road);
        List<ProfileControlPoint> points = flatMode
            ? List.of()
            : VerticalProfileControlPoints.forAlignment(network, road, effectiveAlignment);
        List<VerticalProfileControlPoints.ControlPoint> legacyPoints = points.stream()
            .map(VerticalProfileControlPoints::toLegacyControlPoint)
            .toList();
        float maxGrade = road.getMaxSlope() != null
            ? road.getMaxSlope()
            : ctx.networkManager().getConfig().getMaxSlope();
        RoadSystemConfig config = ctx.networkManager().getConfig();
        List<RoadProfileIntersection> baseIntersections = resolveIntersections(
            ctx, network, road, config, chartData, activeIntersectionDragIndex < 0);
        List<RoadProfileIntersection> intersections =
            profileEditSession.effectiveIntersections(baseIntersections);
        resolveRenderCache(
            ctx, network, road, chartData, design, points, flatOverlay, baseIntersections);
        int chartSelectedPvi = flatMode ? -1 : editorState.selectedProfilePvi;
        int chartActivePvi = flatMode ? -1 : editorState.activeProfilePvi;
        List<RoadLongitudinalProfileRenderer.CurveHandle> curveHandles = flatMode
            ? List.of()
            : buildRoadCurveHandles(network, road, effectiveAlignment, points, chartSelectedPvi);
        if (!flatMode) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_editor_interaction_hint"));
        }
        RoadLongitudinalProfileRenderer.ControlInteraction interaction =
            RoadProfileChartRenderer.renderInteractive(
                chartData, design, points, chartSelectedPvi, chartActivePvi, maxGrade,
                intersections, selectedIntersectionIndex, chartHeight,
                activeIntersectionDragIndex, activeIntersectionDragTarget, flatOverlay,
                curveHandles,
                editorState.pendingProfilePvi,
                editorState.pendingClickX,
                editorState.pendingClickY,
                editorState.activeCurveHandlePvi,
                editorState.activeCurveHandle);
        if (interaction.intersectionDragStarted()) {
            profileEditSession.beginIntersectionEdit(baseIntersections);
        }
        if (interaction.curveHandleDragStarted()) {
            profileEditSession.beginCurveEdit(road, interaction.activeCurveHandlePvi());
        }
        if (interaction.dragStarted()) {
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
            applyProfilePointInsert(ctx, network, road, chartData, interaction);
            updateProfileCurveHandleDraft(interaction);
            renderProfilePviContextMenu(ctx, network, road);
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
                requestedStation = RoadStationing.canonicalLength(network, road);
            }
            if (draggedPoint != null
                    && VerticalProfileControlPoints.isEditablePvi(network, road, draggedPoint)) {
                profileEditSession.updatePviDrag(
                    network,
                    road,
                    draggedPoint,
                    editorState.selectedProfilePvi,
                    requestedStation,
                    interaction.draggedElevation());
            }
        }
        if (interaction.dragFinished()) {
            profileEditSession.commitEdit(
                ctx, network, road, config,
                () -> propagateJunctionGrades(ctx, network, road),
                onAlignmentCommitted);
            editorState.activeCurveHandlePvi = -1;
            editorState.activeCurveHandle =
                RoadLongitudinalProfileRenderer.ControlInteraction.CurveHandleSide.NONE;
        }
        if (interaction.curveHandleDragFinished()) {
            profileEditSession.commitEdit(
                ctx, network, road, config,
                () -> propagateJunctionGrades(ctx, network, road),
                onAlignmentCommitted);
            editorState.activeCurveHandlePvi = -1;
            editorState.activeCurveHandle =
                RoadLongitudinalProfileRenderer.ControlInteraction.CurveHandleSide.NONE;
        }
        if (interaction.activeCurveHandlePvi() >= 0) {
            editorState.activeCurveHandlePvi = interaction.activeCurveHandlePvi();
            editorState.activeCurveHandle = interaction.activeCurveHandle();
        }
        if (interaction.intersectionDragFinished()) {
            Road otherRoad = activeIntersectionDragIndex >= 0
                    && activeIntersectionDragIndex < intersections.size()
                ? network.getRoad(intersections.get(activeIntersectionDragIndex).otherRoadId())
                : null;
            profileEditSession.commitEdit(
                ctx,
                network,
                road,
                config,
                null,
                () -> {
                    if (otherRoad != null) {
                        propagateJunctionGrades(ctx, network, otherRoad);
                    }
                    invalidateIntersectionCache();
                    ctx.requestOverlayRefresh();
                });
            activeIntersectionDragIndex = -1;
            activeIntersectionDragTarget =
                RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget.NONE;
        }
        if (interaction.selectedIntersectionIndex() >= 0) {
            selectedIntersectionIndex = interaction.selectedIntersectionIndex();
        }
        renderEditorLegend(ctx, design, intersections, flatOverlay, flatMode);
        renderIntersectionDetail(ctx, network, road, intersections, interaction, config, flatMode);
        if (flatMode) {
            flatProfileControls.render(
                ctx,
                network,
                road,
                () -> ctx.networkManager().pushHistory(RoadChangeKind.VERTICAL_PROFILE),
                FlatElevationRecommendationUi.terrainSupplier(ctx));
        } else {
            adaptiveProfileControls.render(
                network,
                road,
                legacyPoints,
                maxGrade,
                editorState,
                () -> propagateJunctionGrades(ctx, network, road),
                () -> beginProfileNetworkEdit(ctx),
                propagate -> finishProfileNetworkEdit(ctx, propagate));
        }
        renderBuildPreviewStaleBar(ctx, network);
    }

    private static void renderBuildPreviewStaleBar(RoadUiContext ctx, RoadNetwork network) {
        if (!ctx.previewManager().needsPreviewRecalc()) {
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
            ctx.previewManager().startNetworkPreview(network);
        }
    }

    private void applyIntersectionDrag(
            RoadSystemConfig config,
            List<RoadProfileIntersection> intersections,
            RoadLongitudinalProfileRenderer.ControlInteraction interaction) {
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
            RoadLongitudinalProfileRenderer.ControlInteraction interaction) {
        if (interaction.draggedCurveLength() == null || interaction.activeCurveHandlePvi() < 0) {
            return;
        }
        profileEditSession.updateCurveLength(interaction.draggedCurveLength());
    }

    private static RoadProfileIntersectionDragEditor.DragTarget mapIntersectionDragTarget(
            RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget target) {
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

    private void renderIntersectionDetail(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            List<RoadProfileIntersection> intersections,
            RoadLongitudinalProfileRenderer.ControlInteraction interaction,
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
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_intersection_drag_grade_hint"));
        } else {
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
            if (gradeSeparationControls == null) {
                gradeSeparationControls = new RoadGradeSeparationControls(ctx);
            }
            if (com.plot.plugin.road.crossing.RoadCrossingRef.isCrossingRef(intersection.nodeId())) {
                com.plot.plugin.road.crossing.RoadCrossing crossing = network.getCrossing(
                    com.plot.plugin.road.crossing.RoadCrossingRef.crossingIdFromRef(intersection.nodeId()));
                if (crossing != null) {
                    gradeSeparationControls.renderCrossing(
                        crossing, network, config, RoadGradeSeparationControls.Layout.PROFILE);
                }
            } else {
                RoadNode node = network.getNode(intersection.nodeId());
                if (node != null) {
                    gradeSeparationControls.renderLegacyJunction(
                        node, network, config, RoadGradeSeparationControls.Layout.PROFILE);
                }
            }
        }
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

    private static double requiredClearanceForIntersection(
            RoadNetwork network,
            RoadProfileIntersection intersection,
            RoadSystemConfig config) {
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
        RoadNetwork profileNetwork = ctx.previewManager().getLastProfileNetwork();
        RoadNetwork intersectionNetwork = profileNetwork != null ? profileNetwork : network;
        Road profileRoad = intersectionNetwork.getRoad(road.getId());
        if (profileRoad == null) {
            profileRoad = road;
        }
        List<RoadProfileIntersection> intersections = chartData != null
            ? chartData.intersections()
            : RoadProfileIntersectionResolver.forRoad(
                intersectionNetwork,
                profileRoad,
                config,
                ctx.previewManager().getLastEdgeResults());
        if (!enrichSteepGradeWarnings || intersections.isEmpty()) {
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
            RoadLongitudinalProfileRenderer.ControlInteraction interaction) {
        if (!interaction.addPointRequested()
                || interaction.addPointLocalDistance() == null
                || interaction.addPointElevation() == null) {
            return;
        }
        beginProfileNetworkEdit(ctx);
        double roadLength = RoadStationing.canonicalLength(network, road);
        double insertStation = interaction.addPointLocalDistance();
        double startElevation = chartData.groundElevationAt(0.0);
        double endElevation = chartData.groundElevationAt(chartData.totalStation());
        RoadVerticalAlignment updated = VerticalProfileControlPoints.bootstrapOrInsert(
            road.getVerticalAlignment(),
            roadLength,
            startElevation,
            endElevation,
            insertStation,
            interaction.addPointElevation());
        road.setVerticalAlignment(updated);
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        editorState.selectedProfilePvi = findNearestPviIndex(updated, insertStation);
        finishProfileNetworkEdit(ctx, () -> propagateJunctionGrades(ctx, network, road));
    }

    private void renderProfilePviContextMenu(RoadUiContext ctx, RoadNetwork network, Road road) {
        if (editorState.contextMenuPvi < 0) {
            return;
        }
        if (!ImGui.beginPopup("##road_profile_pvi_context")) {
            return;
        }
        int pviIndex = editorState.contextMenuPvi;
        boolean canDelete = road.getVerticalAlignment() != null
            && road.getVerticalAlignment().pviCount() > 2
            && pviIndex > 0
            && pviIndex < road.getVerticalAlignment().pviCount() - 1;
        if (!canDelete) {
            ImGui.textDisabled(PlotI18n.tr("plugin.road.profile_pvi_delete_disabled"));
        } else if (ImGui.menuItem(PlotI18n.tr("plugin.road.profile_pvi_delete"))) {
            beginProfileNetworkEdit(ctx);
            road.setVerticalAlignment(VerticalProfileControlPoints.removeAt(
                road.getVerticalAlignment(), pviIndex));
            road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
            editorState.selectedProfilePvi = -1;
            editorState.activeProfilePvi = -1;
            editorState.contextMenuPvi = -1;
            finishProfileNetworkEdit(ctx, () -> propagateJunctionGrades(ctx, network, road));
        }
        ImGui.endPopup();
    }

    private static List<RoadLongitudinalProfileRenderer.CurveHandle> buildRoadCurveHandles(
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
        if (selected == null
                || !VerticalProfileControlPoints.isEditablePvi(network, road, selected)) {
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
            new RoadLongitudinalProfileRenderer.CurveHandle(
                selectedPvi, bvcStation, bvcElevation, true),
            new RoadLongitudinalProfileRenderer.CurveHandle(
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

    private void propagateJunctionGrades(RoadUiContext ctx, RoadNetwork network, Road road) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        VerticalProfileNetworkPropagator.Result result =
            VerticalProfileNetworkPropagator.propagate(
                network, road, connected -> connected.getEffectiveMaxSlope(config));
        if (result.limitReached()) {
            editorState.profileAutoFixMessage = PlotI18n.tr(
                "plugin.road.vertical_alignment_network_limit",
                VerticalProfileNetworkPropagator.MAX_PROPAGATION_PASSES);
        } else if (result.unresolvedRoadCount() > 0) {
            editorState.profileAutoFixMessage = PlotI18n.tr(
                "plugin.road.vertical_alignment_network_unresolved",
                result.unresolvedRoadCount());
        } else if (result.adjustedRoadCount() > 0) {
            editorState.profileAutoFixMessage = PlotI18n.tr(
                "plugin.road.vertical_alignment_network_adjusted",
                result.adjustedRoadCount());
        }
    }
}
