package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.profile.edit.ProfileEditSession;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.road.vertical.RoadWorldElevationBounds;
import com.plot.plugin.road.vertical.VerticalProfileAutoFixer;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;
import com.plot.plugin.road.vertical.VerticalProfileCurveFitter;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.List;

/** 地形适应道路纵断面编辑器：选中 PVI 属性面板。 */
final class AdaptiveProfileControls {

    private static final int ADVANCED_LIST_THRESHOLD = 3;

    void render(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            ProfileEditSession session,
            List<VerticalProfileControlPoints.ControlPoint> points,
            float maxGrade,
            ProfileEditorState state,
            Runnable propagateJunctionGrades,
            Runnable onAlignmentCommitted,
            Runnable deleteSelectedPvi) {
        if (road == null || RoadVerticalStrategy.fromRoad(road) == RoadVerticalStrategy.FLAT) {
            return;
        }
        if (points.isEmpty()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.vertical_alignment_none"));
            return;
        }
        RoadElevationBounds bounds = session.elevationBounds();
        if (bounds == null) {
            bounds = RoadWorldElevationBounds.fallback();
            session.setElevationBounds(bounds);
        }
        if (points.size() > ADVANCED_LIST_THRESHOLD
                && ImGui.collapsingHeader(
                    PlotI18n.tr("plugin.road.profile_control_points_collapsed", points.size())
                        + "##profile_control_points_advanced")) {
            for (VerticalProfileControlPoints.ControlPoint point : points) {
                renderControlPointSelectable(point, maxGrade, state);
            }
        }
        if (state.selectedProfilePvi < 0 || road.getVerticalAlignment() == null
                || state.selectedProfilePvi >= road.getVerticalAlignment().pviCount()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_select_pvi_hint"));
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
            ctx.beginNetworkEdit(com.plot.plugin.road.manager.RoadChangeKind.VERTICAL_PROFILE);
            VerticalProfileAutoFixer.Result fixed = VerticalProfileAutoFixer.extendAdjacentRuns(
                road.getVerticalAlignment(), state.selectedProfilePvi,
                RoadStationing.canonicalLength(network, road), maxGrade);
            road.setVerticalAlignment(fixed.alignment());
            road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
            state.profileAutoFixMessage = PlotI18n.tr(fixed.fullyResolved()
                ? "plugin.road.vertical_alignment_auto_fix_success"
                : "plugin.road.vertical_alignment_auto_fix_insufficient");
            ctx.finishNetworkEdit();
            propagateJunctionGrades.run();
            ctx.previewManager().markBuildPreviewStalePreservingProfile();
        }
        if (VerticalProfileControlPoints.canAutoSmooth(network, road, selectedPoint)
                && ImGui.button(PlotI18n.tr("plugin.road.vertical_alignment_auto_smooth"))) {
            ctx.beginNetworkEdit(com.plot.plugin.road.manager.RoadChangeKind.VERTICAL_PROFILE);
            VerticalProfileCurveFitter.Result fitted = VerticalProfileCurveFitter.fitAt(
                road.getVerticalAlignment(), state.selectedProfilePvi);
            road.setVerticalAlignment(fitted.alignment());
            road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
            state.profileAutoFixMessage = PlotI18n.tr(fitted.hasSpace()
                ? "plugin.road.vertical_alignment_auto_smooth_success"
                : "plugin.road.vertical_alignment_auto_smooth_no_space");
            ctx.finishNetworkEdit();
            ctx.previewManager().markBuildPreviewStalePreservingProfile();
        }
        boolean canDelete = VerticalProfileEditor.canDeleteProfilePvi(road, state.selectedProfilePvi);
        if (!canDelete) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_pvi_delete"))) {
            deleteSelectedPvi.run();
            state.profileAutoFixMessage = "";
        }
        if (!canDelete) {
            ImGui.endDisabled();
            if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) {
                ImGui.setTooltip(PlotI18n.tr("plugin.road.profile_pvi_delete_disabled"));
            }
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
        if (RoadElevationInput.renderDragFloat(
                PlotI18n.tr("plugin.road.vertical_alignment_selected_elevation"),
                state.selectedProfileElevation,
                bounds,
                0.25f,
                "Y=%.2f")) {
            // value updated in-place
        }
        if (ImGui.isItemActivated()) {
            session.beginNumericEdit(road);
            state.elevationEditPending = true;
        }
        if (state.elevationEditPending && ImGui.isItemActive()) {
            session.updatePviElevation(
                network, road, state.selectedProfilePvi, state.selectedProfileElevation[0]);
        }
        if (ImGui.isItemDeactivatedAfterEdit() && state.elevationEditPending) {
            session.commitEdit(
                ctx, network, road, config, propagateJunctionGrades, onAlignmentCommitted);
            state.elevationEditPending = false;
            state.profileAutoFixMessage = "";
        }
    }

    void onPviSelected(ProfileEditorState state, VerticalProfileControlPoints.ControlPoint point) {
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
