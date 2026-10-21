package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.ImVec2;
/** Edit Tab 单路编辑顶部概览条：名称、长度、预设、纵断模式、警告与高级项计数。 */
final class RoadEditOverviewStrip {
    private RoadEditOverviewStrip() {
    }

    static void render(RoadUiContext ctx, RoadNetwork network, Road road) {
        if (road == null) {
            return;
        }
        float width = ImGui.getContentRegionAvail().x;
        ImVec2 origin = ImGui.getCursorScreenPos();
        float height = ImGui.getFrameHeightWithSpacing() * 3.2f;
        var drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(
            origin.x,
            origin.y,
            origin.x + width,
            origin.y + height,
            PluginUiColors.PANEL_BG_DARK);
        drawList.addRect(
            origin.x,
            origin.y,
            origin.x + width,
            origin.y + height,
            PluginUiColors.PANEL_BORDER);

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 8f, 6f);
        ImGui.beginChild("##road_edit_overview_strip", width, height, false);
        try {
            ImGui.text(RoadEdgeListHelper.formatRoadLabel(network, road));
            double length = RoadStationing.isStationable(network, road)
                ? RoadStationing.canonicalLength(network, road)
                : RoadEdgeListHelper.computeRoadWorldLength(network, road, ctx.host().coordinates());
            int segmentCount = road.getSegmentIds().size();
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.edit.overview_length_segments", segmentCount, length));

            String presetName = resolvePresetName(road);
            String verticalMode = RoadVerticalStrategy.fromRoad(road).label();
            int issueCount = RoadEditDiagnosticsBanner.countIssues(ctx, network, road);
            int varSectionCount = variableSectionCount(road);
            int facilityCount = facilityCount(road);

            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.road.edit.overview_preset_vertical",
                    presetName,
                    verticalMode));
            String metrics = PlotI18n.tr(
                "plugin.road.edit.overview_metrics",
                varSectionCount,
                facilityCount);
            if (issueCount > 0) {
                ImGui.sameLine();
                ImGui.textColored(
                    PluginUiColors.WARNING,
                    PlotI18n.tr("plugin.road.edit.overview_warnings", issueCount));
                ImGui.sameLine();
                ImGui.textColored(PluginUiColors.HINT_GRAY, " · " + metrics);
            } else {
                ImGui.textColored(PluginUiColors.HINT_GRAY, metrics);
            }
        } finally {
            ImGui.endChild();
            ImGui.popStyleVar();
        }
        ImGui.spacing();
    }

    private static String resolvePresetName(Road road) {
        String styleId = road.getStyleId();
        if (styleId != null && !styleId.isBlank()) {
            return PlotI18n.tr("preset.road." + styleId);
        }
        return PlotI18n.tr("plugin.road.preset_custom");
    }

    private static int variableSectionCount(Road road) {
        RoadVariableCrossSections variable = road.getVariableCrossSections();
        return variable != null ? variable.stationCount() : 0;
    }

    private static int facilityCount(Road road) {
        RoadStationFacilities facilities = road.getStationFacilities();
        return facilities != null ? facilities.runCount() : 0;
    }
}
