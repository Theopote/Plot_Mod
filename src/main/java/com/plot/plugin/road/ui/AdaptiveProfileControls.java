package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.road.vertical.VerticalProfileAutoFixer;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;
import com.plot.plugin.road.vertical.VerticalProfileCurveFitter;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;

import java.util.List;

/** 地形适应道路纵断面编辑器：PVI 选择与编辑。 */
final class AdaptiveProfileControls {

    void render(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            List<VerticalProfileControlPoints.ControlPoint> points,
            float maxGrade,
            ProfileEditorState state,
            Runnable propagateJunctionGrades,
            Runnable beginNetworkEdit,
            java.util.function.Consumer<Runnable> finishNetworkEdit) {
        if (road == null || RoadVerticalStrategy.fromRoad(road) == RoadVerticalStrategy.FLAT) {
            return;
        }
        if (points.isEmpty()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.vertical_alignment_none"));
            return;
        }
        String header = PlotI18n.tr("plugin.road.profile_control_points_collapsed", points.size());
        if (state.controlPointsExpanded) {
            ImGui.setNextItemOpen(true, ImGuiCond.Always);
        }
        if (ImGui.collapsingHeader(header + "##profile_control_points")) {
            state.controlPointsExpanded = true;
            for (VerticalProfileControlPoints.ControlPoint point : points) {
                renderControlPointSelectable(point, maxGrade, state);
            }
        } else if (state.controlPointsExpanded) {
            state.controlPointsExpanded = false;
        }
        if (state.selectedProfilePvi < 0 || road.getVerticalAlignment() == null
                || state.selectedProfilePvi >= road.getVerticalAlignment().pviCount()) {
            return;
        }
        VerticalProfileControlPoints.ControlPoint selectedPoint = points.stream()
            .filter(point -> point.pviIndex() == state.selectedProfilePvi)
            .findFirst()
            .orElse(null);
        if (selectedPoint == null) {
            return;
        }
        renderSelectedPointDetail(selectedPoint, maxGrade);
        if (VerticalProfileControlPoints.isEditablePvi(network, road, selectedPoint)
                && VerticalProfileControlPoints.exceedsGradeLimit(selectedPoint, maxGrade)
                && ImGui.button(PlotI18n.tr("plugin.road.vertical_alignment_auto_fix_grade"))) {
            ctx.editNetwork(() -> {
                VerticalProfileAutoFixer.Result fixed = VerticalProfileAutoFixer.extendAdjacentRuns(
                    road.getVerticalAlignment(), state.selectedProfilePvi,
                    RoadStationing.canonicalLength(network, road), maxGrade);
                road.setVerticalAlignment(fixed.alignment());
                road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
                propagateJunctionGrades.run();
                state.profileAutoFixMessage = PlotI18n.tr(fixed.fullyResolved()
                    ? "plugin.road.vertical_alignment_auto_fix_success"
                    : "plugin.road.vertical_alignment_auto_fix_insufficient");
            });
        }
        if (VerticalProfileControlPoints.canAutoSmooth(network, road, selectedPoint)
                && ImGui.button(PlotI18n.tr("plugin.road.vertical_alignment_auto_smooth"))) {
            ctx.editNetwork(() -> {
                VerticalProfileCurveFitter.Result fitted = VerticalProfileCurveFitter.fitAt(
                    road.getVerticalAlignment(), state.selectedProfilePvi);
                road.setVerticalAlignment(fitted.alignment());
                road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
                state.profileAutoFixMessage = PlotI18n.tr(fitted.hasSpace()
                    ? "plugin.road.vertical_alignment_auto_smooth_success"
                    : "plugin.road.vertical_alignment_auto_smooth_no_space");
            });
        }
        if (!state.profileAutoFixMessage.isBlank()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                state.profileAutoFixMessage);
        }
        if (!VerticalProfileControlPoints.isEditablePvi(network, road, selectedPoint)) {
            if (selectedPoint.sharedJunction()) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr("plugin.road.profile_pvi_shared_junction_hint"));
            }
            return;
        }
        ImGui.dragFloat(
            PlotI18n.tr("plugin.road.vertical_alignment_selected_elevation"),
            state.selectedProfileElevation,
            0.25f,
            RoadParameterLimits.ELEVATION_MIN,
            RoadParameterLimits.ELEVATION_MAX,
            "Y=%.2f");
        if (ImGui.isItemActivated()) {
            beginNetworkEdit.run();
            state.elevationEditPending = true;
        }
        if (state.elevationEditPending && ImGui.isItemActive()) {
            double station = road.getVerticalAlignment().getPvis()
                .get(state.selectedProfilePvi).getStation();
            road.setVerticalAlignment(VerticalProfileControlPoints.move(
                road.getVerticalAlignment(), state.selectedProfilePvi, station,
                state.selectedProfileElevation[0], RoadStationing.canonicalLength(network, road)));
            road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        }
        if (ImGui.isItemDeactivatedAfterEdit() && state.elevationEditPending) {
            finishNetworkEdit.accept(propagateJunctionGrades);
            state.profileAutoFixMessage = "";
        }
    }

    void onPviSelected(ProfileEditorState state, VerticalProfileControlPoints.ControlPoint point) {
        state.controlPointsExpanded = true;
        state.selectedProfileElevation[0] = (float) point.elevation();
    }

    private static void renderControlPointSelectable(
            VerticalProfileControlPoints.ControlPoint point,
            float maxGrade,
            ProfileEditorState state) {
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
                state.selectedProfilePvi == point.pviIndex())) {
            state.selectedProfilePvi = point.pviIndex();
            state.selectedProfileElevation[0] = (float) point.elevation();
        }
        if (invalid) {
            ImGui.popStyleColor();
        }
    }

    private static void renderSelectedPointDetail(
            VerticalProfileControlPoints.ControlPoint point,
            float maxGrade) {
        ImGui.text(PlotI18n.tr(
            "plugin.road.vertical_alignment_control_point",
            point.pviIndex() + 1,
            point.localDistance(),
            point.elevation(),
            formatControlPointGrades(point)));
        if (VerticalProfileControlPoints.exceedsGradeLimit(point, maxGrade)) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.ERROR,
                PlotI18n.tr("plugin.road.profile_pvi_grade_exceeds_hint"));
        }
    }

    private static String formatControlPointGrades(VerticalProfileControlPoints.ControlPoint point) {
        String left = point.leftGradePercent() != null
            ? String.format("%.1f%%", point.leftGradePercent()) : "—";
        String right = point.rightGradePercent() != null
            ? String.format("%.1f%%", point.rightGradePercent()) : "—";
        return left + " / " + right;
    }
}
