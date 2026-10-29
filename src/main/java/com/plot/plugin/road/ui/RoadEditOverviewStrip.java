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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Edit Tab 单路编辑顶部：道路下拉选择 + 长度/预设/警告摘要（对齐土方「选择分区」）。 */
final class RoadEditOverviewStrip {
    private RoadEditOverviewStrip() {
    }

    static void render(RoadUiContext ctx, RoadNetwork network, Road road) {
        render(ctx, network, road, RoadEditDiagnosticsBanner.countIssues(ctx, network, road));
    }

    static void render(RoadUiContext ctx, RoadNetwork network, Road road, int issueCount) {
        if (road == null || network == null) {
            return;
        }
        float width = ImGui.getContentRegionAvail().x;
        ImVec2 origin = ImGui.getCursorScreenPos();
        float height = ImGui.getFrameHeightWithSpacing() * 3.6f;
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
            renderRoadSelector(ctx, network, road);

            double length = RoadStationing.isStationable(network, road)
                ? RoadStationing.canonicalLength(network, road)
                : RoadEdgeListHelper.computeRoadWorldLength(network, road, ctx.host().coordinates());
            int segmentCount = road.getSegmentIds().size();
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.edit.overview_length_segments", segmentCount, length));

            String presetName = resolvePresetName(road);
            String verticalMode = RoadVerticalStrategy.fromRoad(road).label();
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

    private static void renderRoadSelector(RoadUiContext ctx, RoadNetwork network, Road current) {
        List<Road> roads = listEditableRoads(network);
        if (roads.isEmpty()) {
            ImGui.text(RoadEdgeListHelper.formatRoadLabel(network, current));
            return;
        }
        String preview = RoadEdgeListHelper.formatRoadLabel(network, current);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.beginCombo(PlotI18n.tr("plugin.road.edit.select_road"), preview)) {
            for (Road candidate : roads) {
                String label = RoadEdgeListHelper.formatRoadLabel(network, candidate);
                boolean selected = candidate.getId().equals(current.getId());
                if (ImGui.selectable(label + "##edit_road_" + candidate.getId(), selected)) {
                    if (!selected) {
                        ctx.networkManager().selectRoad(candidate.getId(), false);
                        ctx.requestOverlayRefresh();
                    }
                }
                if (selected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
    }

    private static List<Road> listEditableRoads(RoadNetwork network) {
        List<Road> roads = new ArrayList<>();
        for (Road road : network.getRoads().values()) {
            if (road == null || road.getSegmentIds().isEmpty()) {
                continue;
            }
            roads.add(road);
        }
        roads.sort(Comparator.comparing(
            road -> RoadEdgeListHelper.formatRoadLabel(network, road),
            String.CASE_INSENSITIVE_ORDER));
        return roads;
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
