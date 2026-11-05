package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.profile.RoadProfileRoadList;
import com.plot.plugin.road.profile.RoadProfileRoadNavigator;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/**
 * 纵断面工作区：当前查看道路切换（下拉 + 上一/下一）。
 * <p>
 * 无有效选择时仅展示列表首条只读概览，不在渲染中改写全局道路选择。
 */
final class RoadGenerateTargetBar {
    private RoadGenerateTargetBar() {
    }

    static void render(RoadUiContext ctx, RoadNetwork network) {
        List<Road> roads = RoadProfileRoadList.listStationableRoads(network);
        if (roads.isEmpty()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.generate.no_stationable_roads"));
            return;
        }

        Road primary = ctx.networkManager().getPrimarySelectedRoad();
        boolean selectionIsStationable = primary != null
            && RoadProfileRoadNavigator.indexOf(roads, primary.getId()) >= 0;
        // 无选择 / 选择不可桩号：展示首条概览，但不自动 selectRoad。
        String viewingRoadId = selectionIsStationable
            ? primary.getId()
            : roads.getFirst().getId();
        int currentIndex = Math.max(0, RoadProfileRoadNavigator.indexOf(roads, viewingRoadId));
        Road currentRoad = roads.get(currentIndex);
        var coordinates = ctx.host().coordinates();
        String preview = RoadProfileRoadList.formatProfileRoadSummary(network, currentRoad, coordinates);

        // 第一行：道路下拉占满可用宽度，避免窄面板与 prev/next/序号挤在一行溢出。
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.beginCombo("##generate_target_road_combo", preview)) {
            for (Road candidate : roads) {
                String label = RoadProfileRoadList.formatProfileRoadSummary(network, candidate, coordinates);
                boolean selected = selectionIsStationable && candidate.getId().equals(viewingRoadId);
                if (ImGui.selectable(label, selected)) {
                    selectRoad(ctx, candidate.getId());
                }
                if (selected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }

        // 第二行：上一 / 下一 + 道路序号。
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_editor_road_nav_prev") + "##generate_target_prev")) {
            selectRoad(ctx, RoadProfileRoadNavigator.previousRoadId(roads, viewingRoadId));
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_editor_road_nav_next") + "##generate_target_next")) {
            selectRoad(ctx, RoadProfileRoadNavigator.nextRoadId(roads, viewingRoadId));
        }
        ImGui.sameLine();
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.road.profile_overview_road_index",
                currentIndex + 1,
                roads.size()));
    }

    private static void selectRoad(RoadUiContext ctx, String roadId) {
        if (roadId == null || roadId.isBlank()) {
            return;
        }
        ctx.networkManager().selectRoad(roadId, false);
        ctx.requestOverlayRefresh();
    }
}
