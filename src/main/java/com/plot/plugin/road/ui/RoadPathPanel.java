package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.RoadNetwork;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

/**
 * 路径 Tab：道路在哪里、当前选择、路径列表与交叉关系（不含风格编辑）。
 */
public final class RoadPathPanel {
    private final RoadUiContext ctx;
    private final RoadAdoptPanel adoptPanel;
    private final RoadDefaultParamsPanel defaultParamsPanel;
    private final RoadEdgeListPanel edgeListPanel;
    private final RoadOverviewPanel overviewPanel;
    private final RoadIntersectionListPanel intersectionListPanel;

    public RoadPathPanel(
            RoadUiContext ctx,
            RoadAdoptPanel adoptPanel,
            RoadDefaultParamsPanel defaultParamsPanel,
            RoadEdgeListPanel edgeListPanel,
            RoadOverviewPanel overviewPanel,
            RoadIntersectionListPanel intersectionListPanel) {
        this.ctx = ctx;
        this.adoptPanel = adoptPanel;
        this.defaultParamsPanel = defaultParamsPanel;
        this.edgeListPanel = edgeListPanel;
        this.overviewPanel = overviewPanel;
        this.intersectionListPanel = intersectionListPanel;
    }

    public void render() {
        RoadSelectionHeader.render(ctx);

        ImGui.separator();
        adoptPanel.render();

        ImGui.separator();
        renderRoadList();

        if (!ctx.networkManager().getNetwork().getEdges().isEmpty()) {
            ImGui.separator();
            RoadUiSections.section("plugin.road.path.intersections");
            intersectionListPanel.render();
        }

        ImGui.separator();
        renderAdvancedPathManagement();
    }

    private void renderRoadList() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        if (network.getEdges().isEmpty()) {
            RoadUiSections.section("plugin.road.path.road_list");
            ImGui.textColored(
                com.plot.plugin.ui.PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.path.empty_hint"));
            return;
        }

        RoadUiSections.section("plugin.road.path.overview");
        RoadUiWidgets.textWrapped(PlotI18n.tr("plugin.road.network_stats",
            network.getRoads().size(),
            network.getJunctionCount(),
            String.format("%.1f", network.getTotalLength())));
        overviewPanel.renderNetworkMap(network);
        ImGui.spacing();

        RoadUiSections.section("plugin.road.path.road_list");
        edgeListPanel.renderPathList("path_edge_list");
    }

    private void renderAdvancedPathManagement() {
        if (!ImGui.collapsingHeader(
            PlotI18n.tr("plugin.road.path.advanced_management"),
            ImGuiTreeNodeFlags.None)) {
            return;
        }
        RoadNetwork network = ctx.networkManager().getNetwork();
        overviewPanel.renderCompactNetworkSummary();
        overviewPanel.renderHealthWarnings(network);
        ImGui.separator();
        RoadUiSections.section("plugin.road.route.new_road_defaults");
        defaultParamsPanel.renderRoutePrimary();
    }
}
