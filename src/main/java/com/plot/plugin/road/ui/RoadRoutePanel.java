package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.ArrayList;
import java.util.List;

/**
 * 路线 Tab：当前道路、拾取路径、路网摘要、新道路默认类型、全部道路列表。
 */
public final class RoadRoutePanel {
    private final RoadUiContext ctx;
    private final RoadAdoptPanel adoptPanel;
    private final RoadDefaultParamsPanel defaultParamsPanel;
    private final RoadEdgeListPanel edgeListPanel;
    private final RoadOverviewPanel overviewPanel;

    public RoadRoutePanel(
            RoadUiContext ctx,
            RoadAdoptPanel adoptPanel,
            RoadDefaultParamsPanel defaultParamsPanel,
            RoadEdgeListPanel edgeListPanel,
            RoadOverviewPanel overviewPanel) {
        this.ctx = ctx;
        this.adoptPanel = adoptPanel;
        this.defaultParamsPanel = defaultParamsPanel;
        this.edgeListPanel = edgeListPanel;
        this.overviewPanel = overviewPanel;
    }

    public void render() {
        renderCurrentRoadHeader();

        ImGui.separator();
        adoptPanel.render();

        ImGui.separator();
        overviewPanel.renderCompactNetworkSummary();
        overviewPanel.renderHealthWarnings(ctx.networkManager().getNetwork());

        ImGui.separator();
        defaultParamsPanel.renderRoadTypeStep();

        ImGui.separator();
        renderAllRoadsCollapsible();
    }

    private void renderCurrentRoadHeader() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        ImGui.text(PlotI18n.tr("plugin.road.route.current_road"));
        ImGui.sameLine();
        Road road = ctx.networkManager().getPrimarySelectedRoad();
        if (road != null) {
            ImGui.textColored(
                PluginUiColors.INFO_BLUE,
                RoadEdgeListHelper.formatRoadLabel(network, road));
        } else {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.route.current_road_empty"));
        }
    }

    private void renderAllRoadsCollapsible() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        List<com.plot.plugin.road.model.RoadEdge> allEdges =
            new ArrayList<>(network.getEdges().values());
        int flags = ImGuiTreeNodeFlags.None;
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.route.all_roads"), flags)) {
            return;
        }
        if (allEdges.isEmpty()) {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.no_edges"));
            return;
        }
        edgeListPanel.renderToolbar("##route");
        edgeListPanel.renderList(true, "route_edge_list");
    }
}
