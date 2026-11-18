package com.plot.plugin.road.ui;

import com.plot.api.world.ICoordinateService;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.profile.ProfileControlPoint;
import com.plot.plugin.road.profile.edit.ProfileEditSession;
import com.plot.plugin.road.profile.edit.ProfileEditorWindowSession;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.road.vertical.RoadWorldElevationBounds;
import com.plot.plugin.road.vertical.VerticalProfileAutoFixer;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;
import com.plot.plugin.road.vertical.VerticalProfileCurveFitter;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/** 地形适应道路纵断面编辑器：选中 PVI 属性面板。 */
public final class AdaptiveProfileControls {

    public void render(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            ProfileEditSession session,
            ProfileEditorWindowSession windowSession,
            List<ProfileControlPoint> points,
            float maxGrade,
            ProfileEditorState state,
            Runnable recordUndoPointBeforeEdit,
            Runnable propagateJunctionGrades,
            Runnable onWorkingProfileChanged,
            Runnable deleteSelectedPvi,
            Runnable onPviPicked) {
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
        // 变坡点列表已在左轨；此处只渲染选中项详情。
        if (state.selectedProfilePvi < 0 || road.getVerticalAlignment() == null
                || state.selectedProfilePvi >= road.getVerticalAlignment().pviCount()) {
            return;
        }
        ProfileControlPoint selectedPoint = points.stream()
            .filter(point -> point.pviIndex() == state.selectedProfilePvi)
            .findFirst()
            .orElse(null);
        if (selectedPoint == null) {
            return;
        }
        ICoordinateService coordinates = ctx.host() != null ? ctx.host().coordinates() : null;
        renderSelectedPointDetail(selectedPoint, maxGrade, network, road, coordinates);
        if (VerticalProfileControlPoints.isEditablePvi(network, road, selectedPoint)
                && VerticalProfileControlPoints.exceedsGradeLimit(selectedPoint, maxGrade)
                && ImGui.button(PlotI18n.tr("plugin.road.vertical_alignment_auto_fix_grade"))) {
            recordUndoPointBeforeEdit.run();
            VerticalProfileAutoFixer.Result fixed = VerticalProfileAutoFixer.extendAdjacentRuns(
                road.getVerticalAlignment(), state.selectedProfilePvi,
                RoadStationing.canonicalLength(network, road), maxGrade);
            road.setVerticalAlignment(fixed.alignment());
            com.plot.plugin.road.vertical.ProfileSourceEdits.markWorkingCopyEdited(road);
            if (windowSession != null) {
                windowSession.markTouched(road);
            }
            state.profileAutoFixMessage = PlotI18n.tr(fixed.fullyResolved()
                ? "plugin.road.vertical_alignment_auto_fix_success"
                : "plugin.road.vertical_alignment_auto_fix_insufficient");
            propagateJunctionGrades.run();
            onWorkingProfileChanged.run();
        }
        if (VerticalProfileControlPoints.canAutoSmooth(network, road, selectedPoint)
                && ImGui.button(PlotI18n.tr("plugin.road.vertical_alignment_auto_smooth"))) {
            recordUndoPointBeforeEdit.run();
            VerticalProfileCurveFitter.Result fitted = VerticalProfileCurveFitter.fitAt(
                road.getVerticalAlignment(), state.selectedProfilePvi);
            road.setVerticalAlignment(fitted.alignment());
            com.plot.plugin.road.vertical.ProfileSourceEdits.markWorkingCopyEdited(road);
            if (windowSession != null) {
                windowSession.markTouched(road);
            }
            state.profileAutoFixMessage = PlotI18n.tr(fitted.hasSpace()
                ? "plugin.road.vertical_alignment_auto_smooth_success"
                : "plugin.road.vertical_alignment_auto_smooth_no_space");
            propagateJunctionGrades.run();
            onWorkingProfileChanged.run();
        }
        boolean canDelete = VerticalProfileControlPoints.canDelete(
            network, road, state.selectedProfilePvi);
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
                ImGui.setTooltip(PlotI18n.tr(
                    VerticalProfileControlPoints.deleteDisabledMessageKey(
                        network, road, state.selectedProfilePvi)));
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
                network, road, config, windowSession, propagateJunctionGrades, onWorkingProfileChanged);
            state.elevationEditPending = false;
            state.profileAutoFixMessage = "";
        }
    }

    public void onPviSelected(ProfileEditorState state, ProfileControlPoint point) {
        state.selectedProfileElevation[0] = (float) point.elevation();
    }

    private static void renderSelectedPointDetail(
            ProfileControlPoint point,
            float maxGrade,
            RoadNetwork network,
            Road road,
            ICoordinateService coordinates) {
        String station = RoadLengthDisplay.formatCanvasDistance(
            point.roadStation(), network, road, coordinates);
        ImGui.text(PlotI18n.tr(
            "plugin.road.vertical_alignment_control_point",
            point.pviIndex() + 1,
            station,
            point.elevation(),
            formatControlPointGrades(point)));
        if (VerticalProfileControlPoints.exceedsGradeLimit(point, maxGrade)) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.ERROR,
                PlotI18n.tr("plugin.road.profile_pvi_grade_exceeds_hint"));
        }
    }

    private static String formatControlPointGrades(ProfileControlPoint point) {
        String left = point.leftGradePercent() != null
            ? String.format("%.1f%%", point.leftGradePercent()) : "—";
        String right = point.rightGradePercent() != null
            ? String.format("%.1f%%", point.rightGradePercent()) : "—";
        return left + " / " + right;
    }
}
