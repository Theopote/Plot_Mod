package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadNetworkOverviewRenderer;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.LinkedHashSet;

/**
 * 道路概览 Tab：路网统计、缩略图点选；选中后提供进入编辑/纵断面的 workflow 捷径。
 */
public final class RoadOverviewPanel {
    private final RoadUiContext ctx;

    public RoadOverviewPanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        ImGui.text(PlotI18n.tr("plugin.road.network_stats",
            network.getRoads().size(),
            network.getJunctionCount(),
            String.format("%.1f", network.getTotalLength())));

        RoadNetworkOverviewRenderer.render(
            network,
            ctx.networkManager().getNetworkBuilder(),
            ctx.networkManager().getConfig(),
            ctx.networkManager().getSelectedEdgeIds(),
            ctx.networkManager().getSelectedNodeId(),
            edgeId -> ctx.networkManager().handleEdgeSelect(edgeId, ImGui.getIO().getKeyCtrl()),
            ctx.networkManager()::handleNodeSelect
        );

        renderSelectionShortcuts(network);

        ImGui.pushStyleColor(ImGuiCol.Text, PluginUiColors.HINT_GRAY);
        ImGui.textWrapped(PlotI18n.tr("plugin.road.network_map_hint"));
        ImGui.popStyleColor();
    }

    private void renderSelectionShortcuts(RoadNetwork network) {
        String selectedNodeId = ctx.networkManager().getSelectedNodeId();
        if (selectedNodeId != null && !selectedNodeId.isBlank()) {
            return;
        }
        if (ctx.networkManager().getSelectedEdgeIds().isEmpty()) {
            return;
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.road.overview_selection_title"));

        LinkedHashSet<String> roadIds = ctx.networkManager().getSelectedRoadIds();
        if (roadIds.size() > 1) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.overview_multi_road_selected", roadIds.size()));
            if (ImGui.button(PlotI18n.tr("plugin.road.overview_edit_road") + "##overview_multi_edit")) {
                ctx.requestTab(RoadUiTab.EDIT);
            }
            return;
        }

        Road road = ctx.networkManager().getPrimarySelectedRoad();
        if (road == null) {
            return;
        }

        int segmentCount = road.getSegmentIds().size();
        double length = RoadEdgeListHelper.computeRoadLength(network, road);
        ImGui.text(PlotI18n.tr(
            "plugin.road.overview_road_summary",
            RoadEdgeListHelper.formatRoadLabel(network, road),
            segmentCount,
            length));

        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        if (ImGui.button(PlotI18n.tr("plugin.road.overview_edit_road") + "##overview_edit", buttonWidth, 0)) {
            ctx.requestTab(RoadUiTab.EDIT);
        }
        ImGui.sameLine();
        String primaryEdgeId = ctx.networkManager().getPrimarySelectedEdgeId();
        if (ImGui.button(PlotI18n.tr("plugin.road.overview_view_profile") + "##overview_profile", buttonWidth, 0)) {
            ctx.requestViewProfile(primaryEdgeId);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.overview_view_profile"));
        }
    }
}
