package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadLongitudinalProfileRenderer;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadGradeSeparationEvaluation;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.RoadProfileIntersectionDragEditor;
import com.plot.plugin.road.profile.RoadProfileIntersectionResolver;
import com.plot.plugin.road.profile.RoadProfileIntersectionWarningResolver;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.world.World;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalAlignmentProfileOverlay;
import com.plot.plugin.road.vertical.VerticalProfileAutoFixer;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;
import com.plot.plugin.road.vertical.VerticalProfileCurveFitter;
import com.plot.plugin.road.vertical.VerticalProfileNetworkPropagator;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 生成 Tab 纵断面：内联只读概览 + 独立编辑器窗口（放大交互）。
 */
final class VerticalProfileEditor {

    private static final float INLINE_CHART_HEIGHT = 96f;
    private static final float MIN_EDITOR_CHART_HEIGHT = 220f;
    private static final int EDITOR_WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

    private final ImBoolean editorWindowOpen = new ImBoolean(false);
    private String editorEdgeId = "";
    private boolean focusEditorOnOpen = false;

    private RoadGenerationResult cachedEditProfile;
    private String cachedEditProfileEdgeId = "";
    private int selectedProfilePvi = -1;
    private int activeProfilePvi = -1;
    private int selectedIntersectionIndex = -1;
    private final float[] selectedProfileElevation = {64f};
    private String profileAutoFixMessage = "";
    private RoadGradeSeparationControls gradeSeparationControls;
    private boolean profileRecalcSuggested = false;
    private int activeIntersectionDragIndex = -1;
    private RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget
        activeIntersectionDragTarget =
            RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget.NONE;

    void clearCache() {
        cachedEditProfile = null;
        cachedEditProfileEdgeId = "";
        selectedProfilePvi = -1;
        activeProfilePvi = -1;
        selectedIntersectionIndex = -1;
        profileAutoFixMessage = "";
        profileRecalcSuggested = false;
        activeIntersectionDragIndex = -1;
        activeIntersectionDragTarget =
            RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget.NONE;
    }

    void openEditorForEdge(String edgeId) {
        if (edgeId == null || edgeId.isBlank()) {
            return;
        }
        editorEdgeId = edgeId;
        editorWindowOpen.set(true);
        focusEditorOnOpen = true;
    }

    boolean isEditorOpen() {
        return editorWindowOpen.get();
    }

    /** 编辑器打开时当前聚焦的分段，供生成 Tab 内联区同步。 */
    String getFocusedEdgeId() {
        return editorWindowOpen.get() ? editorEdgeId : "";
    }

    void renderInline(RoadUiContext ctx, RoadNetwork network, RoadEdge edge) {
        if (!ctx.previewManager().hasValidPreview()
                && edge.getId().equals(cachedEditProfileEdgeId)) {
            clearCache();
        }
        ImGui.spacing();
        if (!ImGui.collapsingHeader(
                PlotI18n.tr("plugin.road.vertical_alignment_profile_editor"),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            return;
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
            ctx, network, road, edge, config, edgeResult);

        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            RoadEdgeListHelper.formatEdgeLabel(network, edge));
        RoadLongitudinalProfileRenderer.renderOverview(
            edgeResult, design, intersections, INLINE_CHART_HEIGHT);
        renderInlineLegend(design, intersections);
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.vertical_alignment_inline_preview_hint"));
        if (ImGui.button(PlotI18n.tr("plugin.road.vertical_alignment_open_editor"),
                ImGui.getContentRegionAvailX(), 0)) {
            openEditorForEdge(edge.getId());
        }
    }

    void renderEditorWindow(RoadUiContext ctx, RoadNetwork network) {
        if (!editorWindowOpen.get()) {
            return;
        }
        List<String> profileEdgeIds = listProfileEdgeIds(ctx);
        ensureEditorEdgeSelection(ctx, network, profileEdgeIds);
        RoadEdge edge = network.getEdge(editorEdgeId);
        if (edge == null) {
            editorWindowOpen.set(false);
            return;
        }
        Road road = network.getRoadForEdge(edge);
        String title = PlotI18n.tr(
            "plugin.road.vertical_alignment_editor_window_title",
            road != null
                ? RoadEdgeListHelper.formatRoadLabel(network, road)
                : editorEdgeId);

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

        if (!ImGui.begin(title, editorWindowOpen, EDITOR_WINDOW_FLAGS)) {
            ImGui.end();
            return;
        }
        try {
            renderEditorEdgeSelector(ctx, network, profileEdgeIds);
            ImGui.spacing();

            edge = network.getEdge(editorEdgeId);
            if (edge == null) {
                editorWindowOpen.set(false);
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
            renderInteractiveEditor(ctx, network, edge, road, edgeResult, design, chartHeight);
        } finally {
            ImGui.end();
        }
    }

    private List<String> listProfileEdgeIds(RoadUiContext ctx) {
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
        if (edgeIds.size() == 1) {
            RoadEdge only = network.getEdge(edgeIds.getFirst());
            if (only != null) {
                ImGui.textColored(
                    PluginUiColors.HINT_GRAY,
                    formatProfileEdgeOptionLabel(network, only));
            }
            return;
        }
        RoadEdge current = network.getEdge(editorEdgeId);
        String previewLabel = current != null
            ? formatProfileEdgeOptionLabel(network, current)
            : editorEdgeId;

        if (ImGui.beginCombo(
                PlotI18n.tr("plugin.road.vertical_alignment_editor_edge_select") + "##profile_editor_edge",
                previewLabel)) {
            for (String edgeId : edgeIds) {
                RoadEdge edge = network.getEdge(edgeId);
                if (edge == null) {
                    continue;
                }
                String label = formatProfileEdgeOptionLabel(network, edge);
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

    private static String formatProfileEdgeOptionLabel(RoadNetwork network, RoadEdge edge) {
        Road road = network.getRoadForEdge(edge);
        String roadLabel = road != null
            ? RoadEdgeListHelper.formatRoadLabel(network, road)
            : PlotI18n.tr("plugin.road.road_label_fallback", edge.getRoadId());
        return roadLabel + " · " + RoadEdgeListHelper.formatEdgeLabel(network, edge);
    }

    private void selectEditorEdge(RoadUiContext ctx, RoadNetwork network, String edgeId) {
        if (edgeId == null || edgeId.isBlank() || edgeId.equals(editorEdgeId)) {
            return;
        }
        if (network.getEdge(edgeId) == null) {
            return;
        }
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
        selectedProfilePvi = -1;
        activeProfilePvi = -1;
        selectedIntersectionIndex = -1;
        profileAutoFixMessage = "";
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
        RoadGenerationResult edgeResult = ctx.previewManager().getLastEdgeResult(edge.getId());
        if (edgeResult != null && edgeResult.hasProfileData()) {
            cachedEditProfile = edgeResult;
            cachedEditProfileEdgeId = edge.getId();
            profileRecalcSuggested = false;
            return edgeResult;
        }
        if (edge.getId().equals(cachedEditProfileEdgeId)
                && ctx.previewManager().hasValidPreview()) {
            return cachedEditProfile;
        }
        return null;
    }

    private static void renderInlineLegend(
            VerticalAlignmentProfileOverlay design,
            List<RoadProfileIntersection> intersections) {
        ImGui.textColored(0xFF8B5A2B, "■ " + PlotI18n.tr("plugin.road.profile_ground"));
        ImGui.sameLine();
        ImGui.textColored(0xFF4DA3FF, "--- " + PlotI18n.tr("plugin.road.profile_guide"));
        ImGui.sameLine();
        ImGui.textColored(0xFFB0B0B0, "■ " + PlotI18n.tr("plugin.road.profile_target"));
        if (design != null && !design.isEmpty()) {
            ImGui.sameLine();
            ImGui.textColored(0xFF5FD35F, "■ " + PlotI18n.tr("plugin.road.profile_design"));
        }
        if (intersections != null && intersections.stream().anyMatch(RoadProfileIntersection::gradeSeparated)) {
            ImGui.textColored(0xFFFF9966, "\u25C7 " + PlotI18n.tr("plugin.road.profile_intersection_marker_grade"));
        }
    }

    private void renderInteractiveEditor(
            RoadUiContext ctx,
            RoadNetwork network,
            RoadEdge edge,
            Road road,
            RoadGenerationResult edgeResult,
            VerticalAlignmentProfileOverlay design,
            float chartHeight) {
        List<VerticalProfileControlPoints.ControlPoint> points =
            VerticalProfileControlPoints.forEdge(network, road, edge);
        if (points.isEmpty()) {
            return;
        }
        float maxGrade = road.getMaxSlope() != null
            ? road.getMaxSlope()
            : ctx.networkManager().getConfig().getMaxSlope();
        RoadSystemConfig config = ctx.networkManager().getConfig();
        List<RoadProfileIntersection> intersections = resolveIntersections(
            ctx, network, road, edge, config, edgeResult);
        RoadLongitudinalProfileRenderer.ControlInteraction interaction =
            RoadLongitudinalProfileRenderer.renderInteractive(
                edgeResult, design, points, selectedProfilePvi, activeProfilePvi, maxGrade,
                intersections, selectedIntersectionIndex, chartHeight,
                activeIntersectionDragIndex, activeIntersectionDragTarget);
        if (interaction.dragStarted() || interaction.intersectionDragStarted()) {
            ctx.beginNetworkEdit();
        }
        selectedProfilePvi = interaction.selectedPviIndex();
        activeProfilePvi = interaction.activePviIndex();
        if (interaction.activeIntersectionDragIndex() >= 0) {
            activeIntersectionDragIndex = interaction.activeIntersectionDragIndex();
            activeIntersectionDragTarget = interaction.activeIntersectionDragTarget();
        }
        if (interaction.draggedIntersectionElevation() != null
                && activeIntersectionDragIndex >= 0
                && activeIntersectionDragIndex < intersections.size()
                && activeIntersectionDragTarget
                    == RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget.OTHER) {
            RoadProfileIntersection intersection = intersections.get(activeIntersectionDragIndex);
            if (RoadProfileIntersectionDragEditor.applyDraggedElevation(
                    network, road, intersection,
                    RoadProfileIntersectionDragEditor.DragTarget.OTHER,
                    interaction.draggedIntersectionElevation(),
                    config)) {
                profileRecalcSuggested = true;
                intersections = resolveIntersections(
                    ctx, network, road, edge, config, edgeResult);
            }
        }
        if (interaction.draggedElevation() != null && interaction.draggedLocalDistance() != null
                && selectedProfilePvi >= 0
                && road.getVerticalAlignment() != null
                && selectedProfilePvi < road.getVerticalAlignment().pviCount()) {
            selectedProfileElevation[0] = interaction.draggedElevation().floatValue();
            double currentStation = road.getVerticalAlignment().getPvis()
                .get(selectedProfilePvi).getStation();
            double requestedStation = RoadStationing.orientedSegment(network, road, edge.getId())
                .map(segment -> segment.roadStationAtGeometryLocal(
                    interaction.draggedLocalDistance()))
                .orElse(currentStation);
            VerticalProfileControlPoints.ControlPoint draggedPoint = points.stream()
                .filter(point -> point.pviIndex() == selectedProfilePvi)
                .findFirst()
                .orElse(null);
            if (draggedPoint != null && draggedPoint.sharedJunction()) {
                requestedStation = currentStation;
            }
            road.setVerticalAlignment(VerticalProfileControlPoints.move(
                road.getVerticalAlignment(), selectedProfilePvi, requestedStation,
                interaction.draggedElevation(), RoadStationing.canonicalLength(network, road)));
            road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        }
        if (interaction.dragFinished()) {
            ctx.finishNetworkEdit();
            propagateJunctionGrades(ctx, network, road);
        }
        if (interaction.intersectionDragFinished()) {
            ctx.finishNetworkEdit();
            Road otherRoad = activeIntersectionDragIndex >= 0
                    && activeIntersectionDragIndex < intersections.size()
                ? network.getRoad(intersections.get(activeIntersectionDragIndex).otherRoadId())
                : null;
            if (otherRoad != null) {
                VerticalProfileNetworkPropagator.propagate(
                    network, otherRoad, connected -> connected.getEffectiveMaxSlope(config));
            }
            activeIntersectionDragIndex = -1;
            activeIntersectionDragTarget =
                RoadLongitudinalProfileRenderer.ControlInteraction.IntersectionDragTarget.NONE;
            ctx.previewManager().invalidatePreview();
            ctx.requestOverlayRefresh();
        }
        if (interaction.selectedIntersectionIndex() >= 0) {
            selectedIntersectionIndex = interaction.selectedIntersectionIndex();
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.vertical_alignment_profile_legend_hint"));
        renderIntersectionLegend(intersections);
        renderIntersectionDetail(ctx, network, intersections, interaction, config);
        ImGui.text(PlotI18n.tr("plugin.road.vertical_alignment_control_points"));
        for (VerticalProfileControlPoints.ControlPoint point : points) {
            boolean invalid = VerticalProfileControlPoints.exceedsGradeLimit(point, maxGrade);
            String grades = formatControlPointGrades(point);
            if (point.sharedJunction()) {
                grades += " · " + PlotI18n.tr("plugin.road.vertical_alignment_shared_junction");
            }
            String label = PlotI18n.tr(
                "plugin.road.vertical_alignment_control_point",
                point.pviIndex() + 1,
                point.localDistance(),
                point.elevation(),
                grades);
            if (invalid) {
                ImGui.pushStyleColor(ImGuiCol.Text, PluginUiColors.INVALID);
            }
            if (ImGui.selectable(label + "##profile_pvi_" + point.pviIndex(),
                    selectedProfilePvi == point.pviIndex())) {
                selectedProfilePvi = point.pviIndex();
                selectedProfileElevation[0] = (float) point.elevation();
            }
            if (invalid) {
                ImGui.popStyleColor();
            }
        }
        if (selectedProfilePvi < 0 || road.getVerticalAlignment() == null
                || selectedProfilePvi >= road.getVerticalAlignment().pviCount()) {
            return;
        }
        VerticalProfileControlPoints.ControlPoint selectedPoint = points.stream()
            .filter(point -> point.pviIndex() == selectedProfilePvi)
            .findFirst()
            .orElse(null);
        if (VerticalProfileControlPoints.exceedsGradeLimit(selectedPoint, maxGrade)
                && ImGui.button(PlotI18n.tr("plugin.road.vertical_alignment_auto_fix_grade"))) {
            ctx.editNetwork(() -> {
                VerticalProfileAutoFixer.Result fixed = VerticalProfileAutoFixer.extendAdjacentRuns(
                    road.getVerticalAlignment(), selectedProfilePvi,
                    RoadStationing.canonicalLength(network, road), maxGrade);
                road.setVerticalAlignment(fixed.alignment());
                road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
                propagateJunctionGrades(ctx, network, road);
                profileAutoFixMessage = PlotI18n.tr(fixed.fullyResolved()
                    ? "plugin.road.vertical_alignment_auto_fix_success"
                    : "plugin.road.vertical_alignment_auto_fix_insufficient");
            });
        }
        if (selectedProfilePvi > 0
                && selectedProfilePvi < road.getVerticalAlignment().pviCount() - 1
                && ImGui.button(PlotI18n.tr("plugin.road.vertical_alignment_auto_smooth"))) {
            ctx.editNetwork(() -> {
                VerticalProfileCurveFitter.Result fitted = VerticalProfileCurveFitter.fitAt(
                    road.getVerticalAlignment(), selectedProfilePvi);
                road.setVerticalAlignment(fitted.alignment());
                road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
                profileAutoFixMessage = PlotI18n.tr(fitted.hasSpace()
                    ? "plugin.road.vertical_alignment_auto_smooth_success"
                    : "plugin.road.vertical_alignment_auto_smooth_no_space");
            });
        }
        if (!profileAutoFixMessage.isBlank()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                profileAutoFixMessage);
        }
        ImGui.dragFloat(
            PlotI18n.tr("plugin.road.vertical_alignment_selected_elevation"),
            selectedProfileElevation,
            0.25f,
            RoadParameterLimits.ELEVATION_MIN,
            RoadParameterLimits.ELEVATION_MAX,
            "Y=%.2f");
        if (ImGui.button(PlotI18n.tr("plugin.road.vertical_alignment_apply_control_point"))) {
            ctx.editNetwork(() -> {
                double station = road.getVerticalAlignment().getPvis()
                    .get(selectedProfilePvi).getStation();
                road.setVerticalAlignment(VerticalProfileControlPoints.move(
                    road.getVerticalAlignment(), selectedProfilePvi, station,
                    selectedProfileElevation[0], RoadStationing.canonicalLength(network, road)));
                road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
                propagateJunctionGrades(ctx, network, road);
                profileAutoFixMessage = "";
            });
        }
    }

    private static String formatControlPointGrades(
            VerticalProfileControlPoints.ControlPoint point) {
        String left = point.leftGradePercent() != null
            ? String.format("%.1f%%", point.leftGradePercent()) : "—";
        String right = point.rightGradePercent() != null
            ? String.format("%.1f%%", point.rightGradePercent()) : "—";
        return left + " / " + right;
    }

    private static void renderIntersectionLegend(List<RoadProfileIntersection> intersections) {
        if (intersections == null || intersections.isEmpty()) {
            return;
        }
        boolean hasGradeSeparated = intersections.stream().anyMatch(RoadProfileIntersection::gradeSeparated);
        boolean hasWarning = intersections.stream().anyMatch(RoadProfileIntersection::steepGradeWarning);
        if (!hasGradeSeparated) {
            return;
        }
        ImGui.textColored(0xFFFF9966, "\u25C7 " + PlotI18n.tr("plugin.road.profile_intersection_marker_grade"));
        if (hasWarning) {
            ImGui.sameLine();
            ImGui.textColored(PluginUiColors.WARNING, "\u25C7! " + PlotI18n.tr(
                "plugin.road.profile_intersection_marker_warning"));
        }
    }

    private void renderIntersectionDetail(
            RoadUiContext ctx,
            RoadNetwork network,
            List<RoadProfileIntersection> intersections,
            RoadLongitudinalProfileRenderer.ControlInteraction interaction,
            RoadSystemConfig config) {
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
                PlotI18n.tr("plugin.road.profile_intersection_drag_other_hint"));
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
            ImGui.text(PlotI18n.tr(
                "plugin.road.profile_intersection_clearance",
                String.format("%.1f", intersection.clearanceGap())));
            if (intersection.steepGradeWarning()) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.WARNING,
                    PlotI18n.tr("plugin.road.crossing_warning_steep"));
            }
        } else if (!editable) {
            ImGui.text(PlotI18n.tr("plugin.road.profile_intersection_at_grade"));
        }
        if (editable) {
            RoadNode node = network.getNode(intersection.nodeId());
            if (node != null) {
                if (gradeSeparationControls == null) {
                    gradeSeparationControls = new RoadGradeSeparationControls(ctx);
                }
                boolean changed = gradeSeparationControls.render(
                    node, network, config, RoadGradeSeparationControls.Layout.PROFILE);
                if (changed) {
                    profileRecalcSuggested = true;
                }
            }
        }
        if (profileRecalcSuggested && !ctx.previewManager().hasValidPreview()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_intersection_recalculate_hint"));
            if (ImGui.button(PlotI18n.tr("plugin.road.recalculate_preview") + "##profile_recalc")) {
                ctx.previewManager().startNetworkPreview(network);
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
            RoadGenerationResult edgeResult) {
        List<RoadProfileIntersection> intersections = RoadProfileIntersectionResolver.forEdge(
            network, road, edge, config, edgeResult);
        if (intersections.isEmpty()) {
            return intersections;
        }
        RoadGenerator generator = new RoadGenerator(
            config, ctx.host().coordinates(), ctx.host().projection());
        TerrainSampler terrain = resolveTerrainSampler(generator);
        Map<String, RoadGradeSeparationEvaluation> evaluationCache = new HashMap<>();
        return RoadProfileIntersectionWarningResolver.withSteepGradeWarnings(
            intersections,
            network,
            nodeId -> evaluationCache.computeIfAbsent(nodeId, id -> {
                RoadNode node = network.getNode(id);
                if (node == null || !node.isGradeSeparated()) {
                    return null;
                }
                return generator.evaluateGradeSeparation(node, network, terrain);
            }));
    }

    private static TerrainSampler resolveTerrainSampler(RoadGenerator generator) {
        World world = RoadNetworkGenerator.getClientWorld();
        if (world != null) {
            return generator.createTerrainSampler(world);
        }
        return new FlatTerrainSampler(TerrainSampler.DEFAULT_SEA_LEVEL);
    }

    private void propagateJunctionGrades(RoadUiContext ctx, RoadNetwork network, Road road) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        VerticalProfileNetworkPropagator.Result result =
            VerticalProfileNetworkPropagator.propagate(
                network, road, connected -> connected.getEffectiveMaxSlope(config));
        if (result.limitReached()) {
            profileAutoFixMessage = PlotI18n.tr(
                "plugin.road.vertical_alignment_network_limit",
                VerticalProfileNetworkPropagator.MAX_PROPAGATION_PASSES);
        } else if (result.unresolvedRoadCount() > 0) {
            profileAutoFixMessage = PlotI18n.tr(
                "plugin.road.vertical_alignment_network_unresolved",
                result.unresolvedRoadCount());
        } else if (result.adjustedRoadCount() > 0) {
            profileAutoFixMessage = PlotI18n.tr(
                "plugin.road.vertical_alignment_network_adjusted",
                result.adjustedRoadCount());
        }
    }
}
