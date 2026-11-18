package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.profile.RoadProfileRoadList;
import com.plot.plugin.road.profile.RoadProfileRoadNavigator;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/**
 * 纵断面工作区道路选择条：与横断面一致，允许空选（占位下拉），不在无选择时假装已选首条。
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

        RoadSelectionHeader.Mode mode = RoadSelectionHeader.resolveMode(ctx);
        if (mode == RoadSelectionHeader.Mode.MULTI) {
            // 多选由批量 banner + 批量纵向控件承接，此处不渲染假单路目标。
            return;
        }
        if (mode == RoadSelectionHeader.Mode.NONE) {
            renderNoSelection(ctx, network, roads);
            return;
        }
        renderSingleSelection(ctx, network, roads);
    }

    private static void renderNoSelection(RoadUiContext ctx, RoadNetwork network, List<Road> roads) {
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.edit.no_selection_hint"));
        ImGui.spacing();
        renderRoadCombo(ctx, network, roads, null, ImGui.getContentRegionAvailX());
    }

    private static void renderSingleSelection(
            RoadUiContext ctx, RoadNetwork network, List<Road> roads) {
        Road primary = ctx.networkManager().getPrimarySelectedRoad();
        boolean selectionIsStationable = primary != null
            && RoadProfileRoadNavigator.indexOf(roads, primary.getId()) >= 0;
        if (!selectionIsStationable) {
            // 选中了不可桩号道路：与空选同态，避免回退显示首条。
            renderNoSelection(ctx, network, roads);
            return;
        }

        String viewingRoadId = primary.getId();
        float spacing = ImGui.getStyle().getItemSpacingX();
        float navWidth = RoadUiWidgets.roadNavButtonWidth();
        float rowWidth = Math.max(1f, ImGui.getContentRegionAvailX());
        float comboWidth = Math.max(48f, rowWidth - navWidth * 2f - spacing * 2f);

        if (RoadUiWidgets.roadNavPrevButton("##generate_target_prev")) {
            selectRoad(ctx, RoadProfileRoadNavigator.previousRoadId(roads, viewingRoadId));
        }
        ImGui.sameLine(0f, spacing);
        renderRoadCombo(ctx, network, roads, primary, comboWidth);
        ImGui.sameLine(0f, spacing);
        if (RoadUiWidgets.roadNavNextButton("##generate_target_next")) {
            selectRoad(ctx, RoadProfileRoadNavigator.nextRoadId(roads, viewingRoadId));
        }
    }

    private static void renderRoadCombo(
            RoadUiContext ctx,
            RoadNetwork network,
            List<Road> roads,
            Road current,
            float comboWidth) {
        // 仅道路名：长度与纵向方式已在下方「地形与预览」缩略图标题中展示。
        String preview = current != null
            ? RoadEdgeListHelper.formatRoadLabel(network, current)
            : PlotI18n.tr("plugin.road.edit.select_road_placeholder");
        String currentId = current != null ? current.getId() : "";

        ImGui.setNextItemWidth(Math.max(1f, comboWidth));
        if (ImGui.beginCombo("##generate_target_road_combo", preview)) {
            for (Road candidate : roads) {
                String label = RoadEdgeListHelper.formatRoadLabel(network, candidate);
                boolean selected = candidate.getId().equals(currentId);
                if (ImGui.selectable(label, selected)) {
                    selectRoad(ctx, candidate.getId());
                }
                if (selected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
    }

    private static void selectRoad(RoadUiContext ctx, String roadId) {
        if (roadId == null || roadId.isBlank()) {
            return;
        }
        ctx.networkManager().selectRoad(roadId, false);
        ctx.requestOverlayRefresh();
    }
}
