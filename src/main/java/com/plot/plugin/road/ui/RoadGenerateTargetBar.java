package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.profile.RoadProfileRoadList;
import com.plot.plugin.road.profile.RoadProfileRoadNavigator;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/** 生成 Tab：当前编辑道路切换（上一/下一 + 下拉），类似建筑生成器目标选择。 */
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
        if (primary == null || RoadProfileRoadNavigator.indexOf(roads, primary.getId()) < 0) {
            selectRoad(ctx, roads.getFirst().getId());
            primary = ctx.networkManager().getPrimarySelectedRoad();
        }
        String activeRoadId = primary != null ? primary.getId() : roads.getFirst().getId();
        int currentIndex = Math.max(0, RoadProfileRoadNavigator.indexOf(roads, activeRoadId));
        Road currentRoad = roads.get(currentIndex);

        if (ImGui.button(PlotI18n.tr("plugin.road.profile_editor_road_nav_prev") + "##generate_target_prev")) {
            selectRoad(ctx, RoadProfileRoadNavigator.previousRoadId(roads, activeRoadId));
        }
        ImGui.sameLine();

        var coordinates = ctx.host().coordinates();
        String preview = RoadProfileRoadList.formatProfileRoadSummary(network, currentRoad, coordinates);
        float indexWidth = ImGui.calcTextSize(
            PlotI18n.tr("plugin.road.profile_overview_road_index", roads.size(), roads.size())).x
            + ImGui.getStyle().getItemSpacingX() * 2f;
        float comboWidth = Math.max(
            160f,
            ImGui.getContentRegionAvailX()
                - ImGui.getFrameHeight()
                - indexWidth
                - ImGui.getStyle().getItemSpacingX() * 2f);
        ImGui.setNextItemWidth(comboWidth);
        if (ImGui.beginCombo("##generate_target_road_combo", preview)) {
            for (Road candidate : roads) {
                String label = RoadProfileRoadList.formatProfileRoadSummary(network, candidate, coordinates);
                boolean selected = candidate.getId().equals(activeRoadId);
                if (ImGui.selectable(label, selected)) {
                    selectRoad(ctx, candidate.getId());
                }
                if (selected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.profile_editor_road_nav_next") + "##generate_target_next")) {
            selectRoad(ctx, RoadProfileRoadNavigator.nextRoadId(roads, activeRoadId));
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
