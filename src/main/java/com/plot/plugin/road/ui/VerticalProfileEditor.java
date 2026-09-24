package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.RoadLongitudinalProfileRenderer;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.profile.RoadProfileIntersection;
import com.plot.plugin.road.profile.RoadProfileIntersectionResolver;
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
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.List;

/**
 * 编辑 Tab 内联纵断面 / PVI 交互编辑器。
 */
final class VerticalProfileEditor {

    private RoadGenerationResult cachedEditProfile;
    private String cachedEditProfileEdgeId = "";
    private int selectedProfilePvi = -1;
    private int activeProfilePvi = -1;
    private int selectedIntersectionIndex = -1;
    private final float[] selectedProfileElevation = {64f};
    private String profileAutoFixMessage = "";
    private RoadGradeSeparationControls gradeSeparationControls;

    void renderInline(RoadUiContext ctx, RoadNetwork network, RoadEdge edge) {
        ImGui.spacing();
        if (!ImGui.collapsingHeader(
                PlotI18n.tr("plugin.road.vertical_alignment_profile_editor"),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            return;
        }
        RoadGenerationResult edgeResult = ctx.previewManager().getLastEdgeResult(edge.getId());
        if (edgeResult != null && edgeResult.hasProfileData()) {
            cachedEditProfile = edgeResult;
            cachedEditProfileEdgeId = edge.getId();
        } else if (edge.getId().equals(cachedEditProfileEdgeId)) {
            edgeResult = cachedEditProfile;
        }
        if (edgeResult == null || !edgeResult.hasProfileData()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.vertical_alignment_profile_preview_required"));
            if (ImGui.button(PlotI18n.tr("plugin.road.vertical_alignment_calculate_profile"))) {
                ctx.previewManager().startNetworkPreview(network, false);
            }
            return;
        }
        VerticalAlignmentProfileOverlay design =
            VerticalAlignmentProfileOverlay.forEdge(network, edge).orElse(null);
        renderControlPoints(ctx, network, edge, edgeResult, design);
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.vertical_alignment_profile_legend_hint"));
    }

    private void renderControlPoints(
            RoadUiContext ctx,
            RoadNetwork network,
            RoadEdge edge,
            RoadGenerationResult edgeResult,
            VerticalAlignmentProfileOverlay design) {
        Road road = network.getRoadForEdge(edge);
        if (road == null) {
            return;
        }
        List<VerticalProfileControlPoints.ControlPoint> points =
            VerticalProfileControlPoints.forEdge(network, road, edge);
        if (points.isEmpty()) {
            return;
        }
        float maxGrade = road.getMaxSlope() != null
            ? road.getMaxSlope()
            : ctx.networkManager().getConfig().getMaxSlope();
        RoadSystemConfig config = ctx.networkManager().getConfig();
        List<RoadProfileIntersection> intersections = RoadProfileIntersectionResolver.forEdge(
            network, road, edge, config, edgeResult);
        RoadLongitudinalProfileRenderer.ControlInteraction interaction =
            RoadLongitudinalProfileRenderer.renderInteractive(
                edgeResult, design, points, selectedProfilePvi, activeProfilePvi, maxGrade,
                intersections, selectedIntersectionIndex);
        if (interaction.dragStarted()) {
            ctx.beginNetworkEdit();
        }
        selectedProfilePvi = interaction.selectedPviIndex();
        activeProfilePvi = interaction.activePviIndex();
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
        if (interaction.selectedIntersectionIndex() >= 0) {
            selectedIntersectionIndex = interaction.selectedIntersectionIndex();
        }
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
                if (changed && ctx.previewManager().hasValidPreview()) {
                    RoadUiWidgets.textWrappedColored(
                        PluginUiColors.HINT_GRAY,
                        PlotI18n.tr("plugin.road.profile_intersection_recalculate_hint"));
                }
            }
        }
        ImGui.text(PlotI18n.tr(
            "plugin.road.profile_intersection_other_section",
            intersection.otherCrossSection().laneCount,
            Math.round(RoadCrossSectionPreviewRenderer.CrossSectionLayout
                .fromResolved(intersection.otherCrossSection(), 0f)
                .totalWidthBlocks())));
        float previewWidth = Math.min(ImGui.getContentRegionAvail().x, 220f);
        float previewHeight = 44f;
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
