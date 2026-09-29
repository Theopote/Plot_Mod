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
import com.plot.plugin.road.profile.ProfileChartCoordinates;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.RoadProfileIntersectionDragEditor;
import com.plot.plugin.road.profile.RoadProfileIntersectionResolver;
import com.plot.plugin.road.profile.RoadProfileIntersectionWarningResolver;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.world.World;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.solid.RoadGenerationResult;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 生成 Tab 纵断面：内联只读概览 + 独立编辑器窗口（放大交互）。
 */
final class VerticalProfileEditor {

    private static final float INLINE_CHART_HEIGHT = 128f;
    private static final float MIN_EDITOR_CHART_HEIGHT = 220f;
    private static final int EDITOR_WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

    private final ImBoolean editorWindowOpen = new ImBoolean(false);
    private String editorEdgeId = "";
    private boolean focusEditorOnOpen = false;

    private RoadGenerationResult cachedEditProfile;
    private String cachedEditProfileEdgeId = "";
    private final ProfileEditorState editorState = new ProfileEditorState();
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
    private String cachedIntersectionsEdgeId = "";
    private long cachedIntersectionsKey = Long.MIN_VALUE;
    private List<RoadProfileIntersection> cachedIntersections = List.of();
    private RoadGenerator cachedGradeSeparationGenerator;
    private long cachedGradeSeparationGeneratorKey = Long.MIN_VALUE;

    void clearCache() {
        cachedEditProfile = null;
        cachedEditProfileEdgeId = "";
        editorState.reset();
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
        editorEdgeId = edgeId;
        editorWindowOpen.set(true);
        focusEditorOnOpen = true;
    }

    void setOnAlignmentCommitted(Runnable onAlignmentCommitted) {
        this.onAlignmentCommitted = onAlignmentCommitted;
    }

    boolean isEditorOpen() {
        return editorWindowOpen.get();
    }

    /** 编辑器打开时当前聚焦的分段，供生成 Tab 内联区同步。 */
    String getFocusedEdgeId() {
        return editorWindowOpen.get() ? editorEdgeId : "";
    }

    void renderInline(
            RoadUiContext ctx,
            RoadNetwork network,
            RoadEdge edge,
            FlatElevationProfileOverlay flatOverlay) {
        renderInline(ctx, network, edge, flatOverlay, false);
    }

    void renderInline(
            RoadUiContext ctx,
            RoadNetwork network,
            RoadEdge edge,
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
        RoadGenerationResult edgeResult = resolveEdgeResult(ctx, edge);
        if (edgeResult == null || !edgeResult.hasProfileData()) {
            renderMissingProfileActions(ctx, network);
            return;
        }
        Road road = network.getRoadForEdge(edge);
        if (road == null) {
            return;
        }
        VerticalAlignmentProfileOverlay design =
            VerticalAlignmentProfileOverlay.forEdge(network, edge).orElse(null);
        RoadSystemConfig config = ctx.networkManager().getConfig();
        List<RoadProfileIntersection> intersections = resolveIntersections(
            ctx, network, road, edge, config, edgeResult, true);

        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            RoadEdgeListHelper.formatEdgeLabel(network, edge));
        RoadLongitudinalProfileRenderer.renderOverview(
            edgeResult,
            design,
            intersections,
            INLINE_CHART_HEIGHT,
            flatOverlay,
            ProfileChartCoordinates.geometryToProfileScale(edge, edgeResult));
        boolean flatMode = RoadVerticalStrategy.fromRoad(road) == RoadVerticalStrategy.FLAT;
        renderInlineLegend(design, intersections, flatOverlay, flatMode);
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.vertical_alignment_inline_preview_hint"));
        if (ImGui.button(PlotI18n.tr("plugin.road.vertical_alignment_open_editor"),
                ImGui.getContentRegionAvailX(), 0)) {
            openEditorForEdge(edge.getId());
        }
    }

    void renderEditorWindow(
            RoadUiContext ctx,
            RoadNetwork network,
            FlatElevationProfileOverlay flatOverlay) {
        if (!editorWindowOpen.get()) {
            finishPendingNetworkEdit(ctx);
            return;
        }
        RoadEdge edge = network.getEdge(editorEdgeId);
        if (edge == null) {
            List<String> profileEdgeIds = listProfileEdgeIds(ctx, network, null);
            ensureEditorEdgeSelection(ctx, network, profileEdgeIds);
            edge = network.getEdge(editorEdgeId);
        }
        if (edge == null) {
            editorWindowOpen.set(false);
            finishPendingNetworkEdit(ctx);
            return;
        }
        Road road = network.getRoadForEdge(edge);
        List<String> profileEdgeIds = listProfileEdgeIds(ctx, network, road);

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
            if (road != null) {
                ImGui.textColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr(
                        "plugin.road.vertical_alignment_editor_window_title",
                        RoadEdgeListHelper.formatRoadLabel(network, road)));
            }
            renderEditorEdgeSelector(ctx, network, profileEdgeIds);
            ImGui.spacing();

            edge = network.getEdge(editorEdgeId);
            if (edge == null) {
                editorWindowOpen.set(false);
                finishPendingNetworkEdit(ctx);
                return;
            }
            road = network.getRoadForEdge(edge);
            RoadGenerationResult edgeResult = resolveEdgeResult(ctx, edge);
            if (edgeResult == null || !edgeResult.hasProfileData()) {
                renderMissingProfileActions(ctx, network);
                return;
            }
            if (road == null) {
                return;
            }
            VerticalAlignmentProfileOverlay design =
                VerticalAlignmentProfileOverlay.forEdge(network, edge).orElse(null);
            float chartHeight = Math.max(
                MIN_EDITOR_CHART_HEIGHT,
                ImGui.getContentRegionAvail().y * 0.42f);
            renderInteractiveEditor(ctx, network, edge, road, edgeResult, design, chartHeight, flatOverlay);
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
        finishProfileNetworkEdit(ctx, null);
    }

    private List<String> listProfileEdgeIds(RoadUiContext ctx, RoadNetwork network, Road road) {
        if (road != null && com.plot.plugin.road.station.RoadStationing.isStationable(network, road)) {
            List<String> ordered = new ArrayList<>();
            for (String edgeId : road.getOrderedSegmentIds()) {
                RoadGenerationResult result = ctx.previewManager().getLastEdgeResult(edgeId);
                if (result != null && result.hasProfileData()) {
                    ordered.add(edgeId);
                }
            }
            if (!ordered.isEmpty()) {
                return ordered;
            }
        }
        List<String> edgeIds = new ArrayList<>();
        for (Map.Entry<String, RoadGenerationResult> entry
                : ctx.previewManager().getLastEdgeResults().entrySet()) {
            if (entry.getValue() != null && entry.getValue().hasProfileData()) {
                edgeIds.add(entry.getKey());
            }
        }
        return edgeIds;
    }

    private void ensureEditorEdgeSelection(
            RoadUiContext ctx,
            RoadNetwork network,
            List<String> edgeIds) {
        if (edgeIds.isEmpty()) {
            return;
        }
        if (editorEdgeId != null && !editorEdgeId.isBlank() && edgeIds.contains(editorEdgeId)) {
            return;
        }
        String primaryId = ctx.networkManager().getPrimarySelectedEdgeId();
        editorEdgeId = edgeIds.contains(primaryId) ? primaryId : edgeIds.getFirst();
        resetEdgeLocalState();
    }

    private void renderEditorEdgeSelector(
            RoadUiContext ctx,
            RoadNetwork network,
            List<String> edgeIds) {
        if (edgeIds.isEmpty()) {
            return;
        }
        RoadEdge currentEdge = network.getEdge(editorEdgeId);
        Road road = currentEdge != null ? network.getRoadForEdge(currentEdge) : null;
        if (road != null) {
            ImGui.text(RoadEdgeListHelper.formatRoadLabel(network, road));
            double length = RoadEdgeListHelper.computeRoadWorldLength(
                network, road, ctx.host().coordinates());
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.road.profile_editor_road_summary",
                    length,
                    RoadVerticalStrategy.fromRoad(road).label()));
        }
        if (edgeIds.size() == 1) {
            return;
        }
        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.road.profile_editor_view_segment"));
        RoadEdge current = network.getEdge(editorEdgeId);
        String previewLabel = current != null
            ? RoadEdgeListHelper.formatEdgeLabel(network, current)
            : editorEdgeId;

        if (ImGui.beginCombo("##profile_editor_edge", previewLabel)) {
            for (String edgeId : edgeIds) {
                RoadEdge edge = network.getEdge(edgeId);
                if (edge == null) {
                    continue;
                }
                String label = RoadEdgeListHelper.formatEdgeLabel(network, edge);
                if (ImGui.selectable(label + "##editor_profile_" + edgeId, edgeId.equals(editorEdgeId))) {
                    selectEditorEdge(ctx, network, edgeId);
                }
            }
            ImGui.endCombo();
        }

        int currentIndex = Math.max(0, edgeIds.indexOf(editorEdgeId));
        float navButtonWidth = 28f;
        if (ImGui.button("<##profile_editor_prev", navButtonWidth, 0)) {
            selectEditorEdge(
                ctx, network,
                edgeIds.get((currentIndex - 1 + edgeIds.size()) % edgeIds.size()));
        }
        ImGui.sameLine();
        ImGui.text(PlotI18n.tr("plugin.road.profile_edge_index", currentIndex + 1, edgeIds.size()));
        ImGui.sameLine();
        if (ImGui.button(">##profile_editor_next", navButtonWidth, 0)) {
            selectEditorEdge(
                ctx, network,
                edgeIds.get((currentIndex + 1) % edgeIds.size()));
        }
    }

    private void selectEditorEdge(RoadUiContext ctx, RoadNetwork network, String edgeId) {
        if (edgeId == null || edgeId.isBlank() || edgeId.equals(editorEdgeId)) {
            return;
        }
        if (network.getEdge(edgeId) == null) {
            return;
        }
        finishPendingNetworkEdit(ctx);
        editorEdgeId = edgeId;
        resetEdgeLocalState();
        RoadEdge edge = network.getEdge(edgeId);
        if (edge != null && edge.getRoadId() != null) {
            ctx.networkManager().selectRoad(edge.getRoadId(), false);
        }
        ctx.networkManager().setPrimarySelectedEdge(edgeId);
        ctx.requestOverlayRefresh();
    }

    private void resetEdgeLocalState() {
        editorState.reset();
        selectedIntersectionIndex = -1;
        activeIntersectionDragIndex = -1;
        activeIntersectionDragTarget =
            RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget.NONE;
    }

    private void renderMissingProfileActions(RoadUiContext ctx, RoadNetwork network) {
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.vertical_alignment_profile_preview_required"));
        if (ImGui.button(PlotI18n.tr("plugin.road.vertical_alignment_calculate_profile"))) {
            ctx.previewManager().startNetworkPreview(network, false);
        }
        ImGui.sameLine();
        String fullPreviewLabel = ctx.previewManager().needsPreviewRecalc()
            ? PlotI18n.tr("plugin.road.recalculate_preview")
            : PlotI18n.tr("plugin.road.calc_preview");
        if (ImGui.button(fullPreviewLabel + "##profile_full_preview")) {
            ctx.previewManager().startNetworkPreview(network);
        }
    }

    private RoadGenerationResult resolveEdgeResult(RoadUiContext ctx, RoadEdge edge) {
        if (edge == null) {
            return null;
        }
        RoadGenerationResult edgeResult = ctx.previewManager().getLastEdgeResult(edge.getId());
        if (edgeResult != null && edgeResult.hasProfileData()) {
            cachedEditProfile = edgeResult;
            cachedEditProfileEdgeId = edge.getId();
            return edgeResult;
        }
        if (edge.getId().equals(cachedEditProfileEdgeId)
                && cachedEditProfile != null
                && cachedEditProfile.hasProfileData()) {
            return cachedEditProfile;
        }
        return null;
    }

    private static void renderInlineLegend(
            VerticalAlignmentProfileOverlay design,
            List<RoadProfileIntersection> intersections,
            FlatElevationProfileOverlay flatOverlay,
            boolean flatMode) {
        ImGui.textColored(0xFF8B5A2B, "■ " + PlotI18n.tr("plugin.road.profile_ground"));
        if (flatMode) {
            ImGui.sameLine();
            ImGui.textColored(0xFFB0B0B0, "■ " + PlotI18n.tr("plugin.road.profile_actual_road"));
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
            ImGui.textColored(0xFFB0B0B0, "■ " + PlotI18n.tr("plugin.road.profile_actual_road"));
            if (design != null && !design.isEmpty()) {
                ImGui.sameLine();
                ImGui.textColored(0xFF5FD35F, "■ " + PlotI18n.tr("plugin.road.profile_design"));
            }
            if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.profile_advanced_display"))) {
                ImGui.textColored(0xFF4DA3FF, "--- " + PlotI18n.tr("plugin.road.profile_guide"));
                ImGui.sameLine();
                ImGui.textColored(0xFFB0B0B0, "■ " + PlotI18n.tr("plugin.road.profile_target"));
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
            VerticalAlignmentProfileOverlay design,
            List<RoadProfileIntersection> intersections,
            FlatElevationProfileOverlay flatOverlay,
            boolean flatMode) {
        renderInlineLegend(design, intersections, flatOverlay, flatMode);
        renderEditorControlLegend(flatMode);
    }

    private void renderInteractiveEditor(
            RoadUiContext ctx,
            RoadNetwork network,
            RoadEdge edge,
            Road road,
            RoadGenerationResult edgeResult,
            VerticalAlignmentProfileOverlay design,
            float chartHeight,
            FlatElevationProfileOverlay flatOverlay) {
        boolean flatMode = RoadVerticalStrategy.fromRoad(road) == RoadVerticalStrategy.FLAT;
        List<VerticalProfileControlPoints.ControlPoint> points = flatMode
            ? List.of()
            : VerticalProfileControlPoints.forEdge(network, road, edge);
        float maxGrade = road.getMaxSlope() != null
            ? road.getMaxSlope()
            : ctx.networkManager().getConfig().getMaxSlope();
        RoadSystemConfig config = ctx.networkManager().getConfig();
        List<RoadProfileIntersection> intersections = resolveIntersections(
            ctx, network, road, edge, config, edgeResult, activeIntersectionDragIndex < 0);
        int chartSelectedPvi = flatMode ? -1 : editorState.selectedProfilePvi;
        int chartActivePvi = flatMode ? -1 : editorState.activeProfilePvi;
        List<RoadLongitudinalProfileRenderer.CurveHandle> curveHandles = flatMode
            ? List.of()
            : buildCurveHandles(network, road, edge, points, chartSelectedPvi);
        double profileScale = ProfileChartCoordinates.geometryToProfileScale(edge, edgeResult);
        if (!flatMode) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_editor_interaction_hint"));
        }
        RoadLongitudinalProfileRenderer.ControlInteraction interaction =
            RoadLongitudinalProfileRenderer.renderInteractive(
                edgeResult, design, points, chartSelectedPvi, chartActivePvi, maxGrade,
                intersections, selectedIntersectionIndex, chartHeight,
                activeIntersectionDragIndex, activeIntersectionDragTarget, flatOverlay,
                curveHandles,
                editorState.pendingProfilePvi,
                editorState.pendingClickX,
                editorState.pendingClickY,
                editorState.activeCurveHandlePvi,
                editorState.activeCurveHandle,
                profileScale);
        if (interaction.dragStarted()
                || interaction.intersectionDragStarted()
                || interaction.curveHandleDragStarted()) {
            beginProfileNetworkEdit(ctx);
        }
        if (!flatMode) {
            if (interaction.selectedPviIndex() >= 0
                    && interaction.selectedPviIndex() != editorState.selectedProfilePvi) {
                points.stream()
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
            applyProfilePointInsert(ctx, network, road, edge, edgeResult, interaction);
            applyProfileCurveHandleDrag(ctx, network, road, interaction);
            renderProfilePviContextMenu(ctx, network, road);
        }
        if (interaction.activeIntersectionDragIndex() >= 0) {
            activeIntersectionDragIndex = interaction.activeIntersectionDragIndex();
            activeIntersectionDragTarget = interaction.activeIntersectionDragTarget();
        }
        applyIntersectionDrag(ctx, network, road, edge, edgeResult, config, intersections, interaction);
        if (!flatMode
                && interaction.draggedElevation() != null && interaction.draggedLocalDistance() != null
                && editorState.selectedProfilePvi >= 0
                && road.getVerticalAlignment() != null
                && editorState.selectedProfilePvi < road.getVerticalAlignment().pviCount()) {
            editorState.selectedProfileElevation[0] = interaction.draggedElevation().floatValue();
            double currentStation = road.getVerticalAlignment().getPvis()
                .get(editorState.selectedProfilePvi).getStation();
            double requestedStation = RoadStationing.orientedSegment(network, road, edge.getId())
                .map(segment -> segment.roadStationAtGeometryLocal(
                    ProfileChartCoordinates.profileDistanceToGeometryLocal(
                        edge, edgeResult, interaction.draggedLocalDistance())))
                .orElse(currentStation);
            VerticalProfileControlPoints.ControlPoint draggedPoint = points.stream()
                .filter(point -> point.pviIndex() == editorState.selectedProfilePvi)
                .findFirst()
                .orElse(null);
            if (draggedPoint != null && draggedPoint.sharedJunction()) {
                requestedStation = currentStation;
            }
            if (draggedPoint != null
                    && VerticalProfileControlPoints.isEditablePvi(network, road, draggedPoint)) {
                road.setVerticalAlignment(VerticalProfileControlPoints.move(
                    road.getVerticalAlignment(), editorState.selectedProfilePvi, requestedStation,
                    interaction.draggedElevation(), RoadStationing.canonicalLength(network, road)));
                road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
            }
        }
        if (interaction.dragFinished() || interaction.curveHandleDragFinished()) {
            editorState.activeCurveHandlePvi = -1;
            editorState.activeCurveHandle =
                RoadLongitudinalProfileRenderer.ControlInteraction.CurveHandleSide.NONE;
            finishProfileNetworkEdit(ctx, () -> propagateJunctionGrades(ctx, network, road));
        }
        if (interaction.activeCurveHandlePvi() >= 0) {
            editorState.activeCurveHandlePvi = interaction.activeCurveHandlePvi();
            editorState.activeCurveHandle = interaction.activeCurveHandle();
        }
        if (interaction.intersectionDragFinished()) {
            finishProfileNetworkEdit(ctx, null);
            Road otherRoad = activeIntersectionDragIndex >= 0
                    && activeIntersectionDragIndex < intersections.size()
                ? network.getRoad(intersections.get(activeIntersectionDragIndex).otherRoadId())
                : null;
            if (otherRoad != null) {
                propagateJunctionGrades(ctx, network, otherRoad);
            }
            activeIntersectionDragIndex = -1;
            activeIntersectionDragTarget =
                RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget.NONE;
            ctx.requestOverlayRefresh();
        }
        if (interaction.selectedIntersectionIndex() >= 0) {
            selectedIntersectionIndex = interaction.selectedIntersectionIndex();
        }
        renderEditorLegend(design, intersections, flatOverlay, flatMode);
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
                points,
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
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            RoadGenerationResult edgeResult,
            RoadSystemConfig config,
            List<RoadProfileIntersection> intersections,
            RoadLongitudinalProfileRenderer.ControlInteraction interaction) {
        if (interaction.draggedIntersectionElevation() == null
                || activeIntersectionDragIndex < 0
                || activeIntersectionDragIndex >= intersections.size()) {
            return;
        }
        RoadProfileIntersection intersection = intersections.get(activeIntersectionDragIndex);
        RoadProfileIntersectionDragEditor.DragTarget dragTarget = mapIntersectionDragTarget(
            activeIntersectionDragTarget);
        if (dragTarget == null) {
            return;
        }
        if (RoadProfileIntersectionDragEditor.applyDraggedElevation(
                network, road, intersection, dragTarget,
                interaction.draggedIntersectionElevation(), config)) {
            resolveIntersections(ctx, network, road, edge, config, edgeResult, false);
        }
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
        boolean editable = selectedIntersectionIndex >= 0 && detailIndex == selectedIntersectionIndex;
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
            RoadNode node = network.getNode(intersection.nodeId());
            double requiredClearance = RoadProfileIntersectionDragEditor.requiredClearance(node, config);
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
            RoadNode node = network.getNode(intersection.nodeId());
            if (node != null) {
                if (gradeSeparationControls == null) {
                    gradeSeparationControls = new RoadGradeSeparationControls(ctx);
                }
                gradeSeparationControls.render(
                    node, network, config, RoadGradeSeparationControls.Layout.PROFILE);
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

    private List<RoadProfileIntersection> resolveIntersections(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            RoadSystemConfig config,
            RoadGenerationResult edgeResult,
            boolean enrichSteepGradeWarnings) {
        List<RoadProfileIntersection> intersections = RoadProfileIntersectionResolver.forEdge(
            network, road, edge, config, edgeResult);
        if (!enrichSteepGradeWarnings || intersections.isEmpty()) {
            return intersections;
        }
        long cacheKey = intersectionsCacheKey(ctx, edge.getId(), config);
        if (edge.getId().equals(cachedIntersectionsEdgeId) && cacheKey == cachedIntersectionsKey) {
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
        cachedIntersectionsEdgeId = edge.getId();
        cachedIntersectionsKey = cacheKey;
        cachedIntersections = resolved;
        return resolved;
    }

    private void invalidateIntersectionCache() {
        cachedIntersectionsEdgeId = "";
        cachedIntersectionsKey = Long.MIN_VALUE;
        cachedIntersections = List.of();
        cachedGradeSeparationGenerator = null;
        cachedGradeSeparationGeneratorKey = Long.MIN_VALUE;
    }

    private static long intersectionsCacheKey(
            RoadUiContext ctx,
            String edgeId,
            RoadSystemConfig config) {
        return Objects.hash(
            edgeId,
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
            RoadEdge edge,
            RoadGenerationResult edgeResult,
            RoadLongitudinalProfileRenderer.ControlInteraction interaction) {
        if (!interaction.addPointRequested()
                || interaction.addPointLocalDistance() == null
                || interaction.addPointElevation() == null) {
            return;
        }
        beginProfileNetworkEdit(ctx);
        double roadLength = RoadStationing.canonicalLength(network, road);
        double geometryLocal = ProfileChartCoordinates.profileDistanceToGeometryLocal(
            edge, edgeResult, interaction.addPointLocalDistance());
        double insertStation = roadStationAtLocal(network, road, edge, geometryLocal);
        double startElevation = sampleProfileElevation(edgeResult, 0.0);
        double endElevation = sampleProfileElevation(
            edgeResult, edgeResult.profileDistances.getLast());
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

    private void applyProfileCurveHandleDrag(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadLongitudinalProfileRenderer.ControlInteraction interaction) {
        if (interaction.draggedCurveLength() == null
                || interaction.activeCurveHandlePvi() < 0
                || road.getVerticalAlignment() == null) {
            return;
        }
        road.setVerticalAlignment(VerticalProfileControlPoints.withCurveLength(
            road.getVerticalAlignment(),
            interaction.activeCurveHandlePvi(),
            interaction.draggedCurveLength()));
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
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

    private static List<RoadLongitudinalProfileRenderer.CurveHandle> buildCurveHandles(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            List<VerticalProfileControlPoints.ControlPoint> points,
            int selectedPvi) {
        if (road == null || road.getVerticalAlignment() == null || selectedPvi <= 0) {
            return List.of();
        }
        if (selectedPvi >= road.getVerticalAlignment().pviCount() - 1) {
            return List.of();
        }
        VerticalProfileControlPoints.ControlPoint selected = points.stream()
            .filter(point -> point.pviIndex() == selectedPvi)
            .findFirst()
            .orElse(null);
        if (selected == null
                || !VerticalProfileControlPoints.isEditablePvi(network, road, selected)) {
            return List.of();
        }
        PointOfVerticalIntersection pvi = road.getVerticalAlignment().getPvis().get(selectedPvi);
        double half = pvi.hasCurve() ? pvi.getCurveLength() * 0.5 : 4.0;
        double bvcStation = pvi.getStation() - half;
        double evcStation = pvi.getStation() + half;
        return RoadStationing.orientedSegment(network, road, edge.getId()).map(segment -> {
            double bvcLocal = segment.geometryLocalAtRoadStation(bvcStation).orElse(
                selected.localDistance() - half);
            double evcLocal = segment.geometryLocalAtRoadStation(evcStation).orElse(
                selected.localDistance() + half);
            double bvcElevation = VerticalAlignmentGeometry
                .elevationAt(road.getVerticalAlignment(), bvcStation)
                .orElse(pvi.getElevation());
            double evcElevation = VerticalAlignmentGeometry
                .elevationAt(road.getVerticalAlignment(), evcStation)
                .orElse(pvi.getElevation());
            return List.of(
                new RoadLongitudinalProfileRenderer.CurveHandle(
                    selectedPvi, bvcLocal, bvcElevation, true),
                new RoadLongitudinalProfileRenderer.CurveHandle(
                    selectedPvi, evcLocal, evcElevation, false));
        }).orElse(List.of());
    }

    private static double roadStationAtLocal(
            RoadNetwork network,
            Road road,
            RoadEdge edge,
            double localDistance) {
        return RoadStationing.orientedSegment(network, road, edge.getId())
            .map(segment -> segment.roadStationAtGeometryLocal(localDistance))
            .orElse(localDistance);
    }

    private static double sampleProfileElevation(RoadGenerationResult result, double localDistance) {
        if (result == null || result.profileDistances.isEmpty()) {
            return TerrainSampler.DEFAULT_SEA_LEVEL;
        }
        List<Double> distances = result.profileDistances;
        List<Integer> ground = result.profileGroundHeights;
        if (localDistance <= distances.getFirst()) {
            return ground.getFirst();
        }
        if (localDistance >= distances.getLast()) {
            return ground.getLast();
        }
        for (int i = 1; i < distances.size(); i++) {
            double end = distances.get(i);
            if (localDistance <= end) {
                double start = distances.get(i - 1);
                double ratio = (localDistance - start) / (end - start);
                return ground.get(i - 1) + ratio * (ground.get(i) - ground.get(i - 1));
            }
        }
        return ground.getLast();
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
