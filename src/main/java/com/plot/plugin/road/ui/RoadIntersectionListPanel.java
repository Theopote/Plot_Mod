package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.graph.RoadGraphQueries;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 路径 Tab：交叉点列表与高程关系编辑。 */
public final class RoadIntersectionListPanel {
    private final RoadUiContext ctx;
    private final RoadNodePropertyPanel nodePropertyPanel;

    public RoadIntersectionListPanel(RoadUiContext ctx, RoadNodePropertyPanel nodePropertyPanel) {
        this.ctx = ctx;
        this.nodePropertyPanel = nodePropertyPanel;
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        List<RoadNode> junctions = listJunctionNodes(network);
        if (junctions.isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.path.no_intersections"));
            return;
        }

        String selectedNodeId = ctx.networkManager().getSelectedNodeId();
        for (RoadNode node : junctions) {
            renderJunctionRow(network, node, node.getId().equals(selectedNodeId));
        }
    }

    private List<RoadNode> listJunctionNodes(RoadNetwork network) {
        List<RoadNode> junctions = new ArrayList<>();
        for (RoadNode node : network.getNodes().values()) {
            if (node == null || node.getDegree() < 2) {
                continue;
            }
            if (node.isJunction() || RoadGraphQueries.isSimpleCrossing(node, network)) {
                junctions.add(node);
            }
        }
        junctions.sort(Comparator.comparingInt(RoadNode::getDegree).reversed());
        return junctions;
    }

    private void renderJunctionRow(RoadNetwork network, RoadNode node, boolean selected) {
        RoadNetworkBuilder.JunctionType type =
            ctx.networkManager().getNetworkBuilder().classify(node);
        String title = formatJunctionTitle(network, node, type);
        int flags = selected ? ImGuiTreeNodeFlags.DefaultOpen : ImGuiTreeNodeFlags.None;
        if (!ImGui.collapsingHeader(title + "##junction_" + node.getId(), flags)) {
            return;
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.path.focus_junction") + "##focus_" + node.getId())) {
            focusJunction(network, node);
        }

        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.junction_topology_type", RoadNetworkManager.junctionTypeLabel(type)));

        if (RoadGraphQueries.isSimpleCrossing(node, network)) {
            nodePropertyPanel.renderGradeSeparationForNode(node);
        } else if (node.isGradeSeparated()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.road.path.complex_grade_separation_hint"));
        }
    }

    private void focusJunction(RoadNetwork network, RoadNode node) {
        ctx.networkManager().handleNodeSelect(node.getId());
        for (String roadId : network.getDistinctRoadIdsAtNode(node.getId())) {
            Road road = network.getRoad(roadId);
            if (road != null && !road.getOrderedSegmentIds().isEmpty()) {
                ctx.networkManager().handleEdgeSelect(road.getOrderedSegmentIds().get(0), true);
            }
        }
        ctx.requestOverlayRefresh();
    }

    private String formatJunctionTitle(
            RoadNetwork network,
            RoadNode node,
            RoadNetworkBuilder.JunctionType type) {
        List<String> roadIds = new ArrayList<>(network.getDistinctRoadIdsAtNode(node.getId()));
        if (roadIds.size() >= 2) {
            String a = formatRoadLabel(network, roadIds.get(0));
            String b = formatRoadLabel(network, roadIds.get(1));
            if (roadIds.size() == 2) {
                return PlotI18n.tr("plugin.road.path.intersection_pair", a, b);
            }
            return PlotI18n.tr(
                "plugin.road.path.intersection_multi",
                a,
                b,
                roadIds.size());
        }
        return RoadNetworkManager.junctionTypeLabel(type);
    }

    private static String formatRoadLabel(RoadNetwork network, String roadId) {
        Road road = network.getRoad(roadId);
        if (road == null) {
            return roadId;
        }
        return RoadEdgeListHelper.formatRoadLabel(network, road);
    }
}
