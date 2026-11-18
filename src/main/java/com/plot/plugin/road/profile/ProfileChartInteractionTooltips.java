package com.plot.plugin.road.profile;

import com.plot.plugin.road.vertical.VerticalProfileControlPoints;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** Hover tooltips for profile chart control points. */
final class ProfileChartInteractionTooltips {

    private ProfileChartInteractionTooltips() {
    }

    static void renderPviHover(
            ProfileControlPoint point,
            double maxGradePercent,
            double stationDisplayScale) {
        if (point == null) {
            return;
        }
        double scale = RoadProfileChartRenderer.sanitizeStationDisplayScale(stationDisplayScale);
        ImGui.beginTooltip();
        ImGui.text(resolveHoverTitle(point));
        // 翻译串用 %s：先格式化为两位小数，避免 MC 对 %.nf 处理不一致打出全精度。
        ImGui.text(PlotI18n.tr(
            "plugin.road.profile_pvi_hover_station",
            String.format("%.2f", point.roadStation() * scale)));
        ImGui.text(PlotI18n.tr(
            "plugin.road.profile_pvi_hover_elevation",
            String.format("%.2f", point.elevation())));
        String left = point.leftGradePercent() != null
            ? String.format("%.1f%%", point.leftGradePercent()) : "—";
        String right = point.rightGradePercent() != null
            ? String.format("%.1f%%", point.rightGradePercent()) : "—";
        ImGui.text(PlotI18n.tr("plugin.road.profile_pvi_hover_grades", left, right));
        if (VerticalProfileControlPoints.exceedsGradeLimit(point, maxGradePercent)) {
            ImGui.textColored(PluginUiColors.ERROR, PlotI18n.tr("plugin.road.profile_pvi_grade_exceeds_hint"));
        }
        ImGui.separator();
        if (point.endpoint()) {
            renderEndpointHints(point);
        } else if (point.role() == ProfilePointRole.JUNCTION_FIXED || point.sharedJunction()) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_pvi_junction_fixed_hint"));
        } else if (point.role() == ProfilePointRole.USER_LOCKED) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_pvi_user_locked_hint"));
        } else if (!point.elevationEditable()) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_pvi_not_editable_hint"));
        } else {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.profile_pvi_hover_drag_y"));
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.profile_pvi_hover_drag_shift"));
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.profile_pvi_hover_right_click"));
        }
        ImGui.endTooltip();
    }

    private static String resolveHoverTitle(ProfileControlPoint point) {
        if (point.role() == ProfilePointRole.START_ENDPOINT
                || point.role() == ProfilePointRole.LOOP_SEAM_START) {
            return PlotI18n.tr("plugin.road.profile_pvi_hover_title_start");
        }
        if (point.role() == ProfilePointRole.END_ENDPOINT
                || point.role() == ProfilePointRole.LOOP_SEAM_END) {
            return PlotI18n.tr("plugin.road.profile_pvi_hover_title_end");
        }
        return PlotI18n.tr("plugin.road.profile_pvi_hover_title", point.pviIndex() + 1);
    }

    private static void renderEndpointHints(ProfileControlPoint point) {
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.profile_pvi_endpoint_hint"));
        if (point.elevationEditable()) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.profile_pvi_hover_drag_y"));
        }
    }
}
