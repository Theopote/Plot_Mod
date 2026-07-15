package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadNetworkOverviewRenderer;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 道路概览 Tab：路网统计、缩略图点选（只读，编辑请切换到「编辑属性」Tab）。
 */
public final class RoadOverviewPanel {
    private final RoadUiContext ctx;

    public RoadOverviewPanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        ImGui.text(PlotI18n.tr("plugin.road.network_stats",
            network.getNodes().size(),
            network.getEdges().size(),
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
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.network_map_hint"));
    }
}
