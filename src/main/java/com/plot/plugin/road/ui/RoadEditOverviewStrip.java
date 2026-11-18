package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.facility.RoadStationFacilities;
import com.plot.plugin.road.model.section.RoadVariableCrossSections;
import com.plot.plugin.road.profile.RoadProfileRoadNavigator;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Edit Tab 单路编辑顶部：道路下拉选择 + 长度/预设/警告摘要（对齐土方「选择分区」）。 */
final class RoadEditOverviewStrip {
    private RoadEditOverviewStrip() {
    }

    static void render(RoadUiContext ctx, RoadNetwork network, Road road, int issueCount) {
        if (road == null || network == null) {
            return;
        }
        float width = ImGui.getContentRegionAvail().x;
        ImVec2 origin = ImGui.getCursorScreenPos();
        float height = ImGui.getFrameHeightWithSpacing() * 3.0f;
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
            renderRoadSelectorCombo(ctx, network, road);

            int segmentCount = road.getSegmentIds().size();
            String verticalMode = RoadVerticalStrategy.fromRoad(road).label();
            String lengthCompact = String.format(
                Locale.ROOT,
                "%.2fm",
                RoadLengthDisplay.roadBlocks(network, road, ctx.host().coordinates()));
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.road.edit.overview_length_vertical",
                    segmentCount,
                    lengthCompact,
                    verticalMode));

            int varSectionCount = variableSectionCount(road);
            int facilityCount = facilityCount(road);
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
                ImGui.textColored(PluginUiColors.HINT_GRAY, "·" + metrics);
            } else {
                ImGui.textColored(PluginUiColors.HINT_GRAY, metrics);
            }
        } finally {
            ImGui.endChild();
            ImGui.popStyleVar();
        }
        ImGui.spacing();
    }

    /**
     * 道路下拉选择（对齐土方「选择分区」）。{@code current} 可为 null（未选中时显示占位预览）。
     * 有当前选中时两侧放居中箭头切换按钮。
     */
    static void renderRoadSelectorCombo(RoadUiContext ctx, RoadNetwork network, Road current) {
        List<Road> roads = listEditableRoads(network);
        if (roads.isEmpty()) {
            if (current != null) {
                ImGui.text(RoadEdgeListHelper.formatRoadLabel(network, current));
            }
            return;
        }
        String preview = current != null
            ? RoadEdgeListHelper.formatRoadLabel(network, current)
            : PlotI18n.tr("plugin.road.edit.select_road_placeholder");
        String currentId = current != null ? current.getId() : "";
        float spacing = ImGui.getStyle().getItemSpacingX();
        boolean showNav = current != null && roads.size() > 1;
        float comboWidth = ImGui.getContentRegionAvailX();
        if (showNav) {
            float navWidth = RoadUiWidgets.roadNavButtonWidth();
            comboWidth = Math.max(48f, comboWidth - navWidth * 2f - spacing * 2f);
            if (RoadUiWidgets.roadNavPrevButton("##edit_road_nav_prev")) {
                selectRoad(ctx, RoadProfileRoadNavigator.previousRoadId(roads, currentId));
            }
            ImGui.sameLine(0f, spacing);
        }
        // 有前后切换时用无标签 combo，避免「选择道路」插在下拉与右箭头之间。
        String comboLabel = showNav
            ? "##edit_road_selector_combo"
            : PlotI18n.tr("plugin.road.edit.select_road");
        ImGui.setNextItemWidth(Math.max(1f, comboWidth));
        if (ImGui.beginCombo(comboLabel, preview)) {
            for (Road candidate : roads) {
                String label = RoadEdgeListHelper.formatRoadLabel(network, candidate);
                boolean selected = candidate.getId().equals(currentId);
                if (ImGui.selectable(label + "##edit_road_" + candidate.getId(), selected)) {
                    if (!selected) {
                        selectRoad(ctx, candidate.getId());
                    }
                }
                if (selected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        if (showNav) {
            ImGui.sameLine(0f, spacing);
            if (RoadUiWidgets.roadNavNextButton("##edit_road_nav_next")) {
                selectRoad(ctx, RoadProfileRoadNavigator.nextRoadId(roads, currentId));
            }
        }
    }

    private static void selectRoad(RoadUiContext ctx, String roadId) {
        if (roadId == null || roadId.isBlank()) {
            return;
        }
        ctx.networkManager().selectRoad(roadId, false);
        ctx.requestOverlayRefresh();
    }

    static List<Road> listEditableRoads(RoadNetwork network) {
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

    private static int variableSectionCount(Road road) {
        RoadVariableCrossSections variable = road.getVariableCrossSections();
        return variable != null ? variable.stationCount() : 0;
    }

    private static int facilityCount(Road road) {
        RoadStationFacilities facilities = road.getStationFacilities();
        return facilities != null ? facilities.runCount() : 0;
    }
}
