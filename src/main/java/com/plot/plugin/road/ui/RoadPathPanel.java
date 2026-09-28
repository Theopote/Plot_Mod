package com.plot.plugin.road.ui;

import com.plot.plugin.road.graph.RoadGraphQueries;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 路径 Tab：道路在哪里、当前选择、路径列表与交叉关系（不含风格编辑）。
 */
public final class RoadPathPanel {
    private final RoadUiContext ctx;
    private final RoadAdoptPanel adoptPanel;
    private final RoadEdgeListPanel edgeListPanel;
    private final RoadOverviewPanel overviewPanel;
    private final RoadIntersectionDetailPanel intersectionDetailPanel;
    private final RoadIntersectionListPanel intersectionListPanel;
    private final RoadPathVerticalPropertyPanel verticalPropertyPanel =
        new RoadPathVerticalPropertyPanel(new RoadVerticalStrategySwitchDialog());

    public RoadPathPanel(
            RoadUiContext ctx,
            RoadAdoptPanel adoptPanel,
            RoadEdgeListPanel edgeListPanel,
            RoadOverviewPanel overviewPanel,
            RoadIntersectionDetailPanel intersectionDetailPanel,
            RoadIntersectionListPanel intersectionListPanel) {
        this.ctx = ctx;
        this.adoptPanel = adoptPanel;
        this.edgeListPanel = edgeListPanel;
        this.overviewPanel = overviewPanel;
        this.intersectionDetailPanel = intersectionDetailPanel;
        this.intersectionListPanel = intersectionListPanel;
    }

    public void render() {
        RoadPathHeader.render(ctx, adoptPanel);
        verticalPropertyPanel.render(ctx);

        ImGui.separator();
        renderRoadList();

        RoadNetwork network = ctx.networkManager().getNetwork();
        if (!network.getEdges().isEmpty()) {
            ImGui.separator();
            RoadUiSections.section("plugin.road.path.intersections");
            renderSelectedIntersection(network);
            intersectionListPanel.render();
        }
    }

    private void renderSelectedIntersection(RoadNetwork network) {
        String selectedNodeId = ctx.networkManager().getSelectedNodeId();
        if (selectedNodeId == null || selectedNodeId.isBlank()) {
            RoadUiWidgets.textWrappedColored(
                com.plot.plugin.ui.PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.path.intersection_select_hint"));
            ImGui.spacing();
            return;
        }
        RoadNode node = network.getNode(selectedNodeId);
        if (node == null || node.getDegree() < 2) {
            return;
        }
        if (!node.isJunction() && !RoadGraphQueries.isSimpleCrossing(node, network)) {
            return;
        }
        intersectionDetailPanel.render(network, node);
        ImGui.spacing();
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

        overviewPanel.renderNetworkMap(network);
        ImGui.spacing();

        RoadUiSections.section("plugin.road.path.road_list");
        edgeListPanel.renderPathList("path_edge_list");
    }

    public void renderDeferredModals() {
        verticalPropertyPanel.renderDeferredModals(ctx);
    }

}
