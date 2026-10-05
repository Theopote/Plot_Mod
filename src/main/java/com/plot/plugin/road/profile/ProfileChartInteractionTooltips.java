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
            double maxGradePercent) {
        if (point == null) {
            return;
        }
        ImGui.beginTooltip();
        ImGui.text(PlotI18n.tr(
            "plugin.road.profile_pvi_hover_title",
            point.pviIndex() + 1));
        ImGui.text(PlotI18n.tr(
            "plugin.road.profile_pvi_hover_station",
            point.roadStation()));
        ImGui.text(PlotI18n.tr(
            "plugin.road.profile_pvi_hover_elevation",
            point.elevation()));
        String left = point.leftGradePercent() != null
            ? String.format("%.1f%%", point.leftGradePercent()) : "—";
        String right = point.rightGradePercent() != null
            ? String.format("%.1f%%", point.rightGradePercent()) : "—";
        ImGui.text(PlotI18n.tr("plugin.road.profile_pvi_hover_grades", left, right));
        if (VerticalProfileControlPoints.exceedsGradeLimit(point, maxGradePercent)) {
            ImGui.textColored(PluginUiColors.ERROR, PlotI18n.tr("plugin.road.profile_pvi_grade_exceeds_hint"));
        }
        ImGui.separator();
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.profile_pvi_hover_drag_y"));
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.profile_pvi_hover_drag_shift"));
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.profile_pvi_hover_right_click"));
        ImGui.endTooltip();
    }
}
