package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.graph.NearbyJunctionClusterAnalyzer;
import com.plot.plugin.road.graph.RoadGraphEdits;
import com.plot.plugin.road.graph.RoadGraphQueries;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 复杂多路交叉口：主界面简要说明 + 可折叠高级处理。 */
public final class ComplexJunctionGuidePanel {
    private final RoadUiContext ctx;

    public ComplexJunctionGuidePanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    public void render(RoadNetwork network, RoadNode node) {
        if (network == null || node == null) {
            return;
        }
        int roadCount = network.getDistinctRoadIdsAtNode(node.getId()).size();
        ImGui.spacing();
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.complex_junction_summary", roadCount));
        if (ImGui.button(PlotI18n.tr("plugin.road.complex_junction_node_settings") + "##complex_node")) {
            focusNode(network, node);
        }
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.complex_junction_advanced"))) {
            return;
        }
        renderAdvanced(network, node);
    }

    private void renderAdvanced(RoadNetwork network, RoadNode node) {
        RoadNetworkBuilder.JunctionType type =
            ctx.networkManager().getNetworkBuilder().classify(node);
        ImGui.bulletText(PlotI18n.tr(
            "plugin.road.complex_junction_topology",
            RoadNetworkManager.junctionTypeLabel(type),
            node.getDegree()));
        if (!RoadGraphQueries.isSimpleCrossing(node, network)) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.complex_junction_simple_crossing_only"));
        }

        NearbyJunctionClusterAnalyzer.NearbyJunctionCluster cluster =
            NearbyJunctionClusterAnalyzer.clusterContaining(
                network,
                node.getId(),
                NearbyJunctionClusterAnalyzer.DEFAULT_CLUSTER_DISTANCE_BLOCKS);
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.road.complex_junction_advanced_nearby"));
        renderNearbyCluster(network, node, cluster);

        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.road.complex_junction_advanced_pairs"));
        renderRoadPairs(network, node);

        ImGui.separator();
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.complex_junction_step4_body"));
    }

    private void renderNearbyCluster(
            RoadNetwork network,
            RoadNode node,
            NearbyJunctionClusterAnalyzer.NearbyJunctionCluster cluster) {
        if (cluster == null) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.road.complex_junction_no_nearby_cluster",
                    (int) Math.round(NearbyJunctionClusterAnalyzer.DEFAULT_CLUSTER_DISTANCE_BLOCKS)));
            return;
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.WARNING,
            PlotI18n.tr(
                "plugin.road.complex_junction_nearby_cluster_warning",
                cluster.nodeIds().size(),
                String.format("%.1f", cluster.maxSpanBlocks()),
                NearbyJunctionClusterAnalyzer.DEFAULT_CLUSTER_DISTANCE_BLOCKS));
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.complex_junction_merge_policy"));

        for (String otherNodeId : cluster.nodeIds()) {
            if (otherNodeId.equals(node.getId())) {
                continue;
            }
            RoadNode other = network.getNode(otherNodeId);
            if (other == null) {
                continue;
            }
            double distance = NearbyJunctionClusterAnalyzer.distanceBlocks(node, other);
            ImGui.bulletText(PlotI18n.tr(
                "plugin.road.complex_junction_nearby_item",
                formatNodeLabel(network, other),
                String.format("%.1f", distance)));
            ImGui.sameLine();
            if (ImGui.smallButton(PlotI18n.tr("plugin.road.path.focus_junction") + "##near_" + otherNodeId)) {
                focusNode(network, other);
            }
            ImGui.sameLine();
            if (ImGui.smallButton(PlotI18n.tr("plugin.road.complex_junction_merge_here") + "##merge_" + otherNodeId)) {
                mergeNodes(network, node.getId(), otherNodeId);
            }
        }
    }

    private void renderRoadPairs(RoadNetwork network, RoadNode node) {
        List<String> roadIds = new ArrayList<>(network.getDistinctRoadIdsAtNode(node.getId()));
        if (roadIds.size() < 2) {
            return;
        }
        for (int i = 0; i < roadIds.size(); i++) {
            for (int j = i + 1; j < roadIds.size(); j++) {
                String roadA = roadIds.get(i);
                String roadB = roadIds.get(j);
                ImGui.bulletText(PlotI18n.tr(
                    "plugin.road.complex_junction_pair_item",
                    formatRoadLabel(network, roadA),
                    formatRoadLabel(network, roadB)));
                RoadNode simpleNode = findSimpleCrossingForPair(network, roadA, roadB);
                if (simpleNode != null && !simpleNode.getId().equals(node.getId())) {
                    ImGui.indent();
                    if (ImGui.smallButton(PlotI18n.tr("plugin.road.path.focus_junction")
                            + "##pair_" + simpleNode.getId())) {
                        focusNode(network, simpleNode);
                    }
                    ImGui.sameLine();
                    ImGui.textColored(
                        PluginUiColors.HINT_GRAY,
                        PlotI18n.tr(
                            "plugin.road.complex_junction_pair_simple_hint",
                            formatNodeLabel(network, simpleNode)));
                    ImGui.unindent();
                }
            }
        }
    }

    private RoadNode findSimpleCrossingForPair(RoadNetwork network, String roadA, String roadB) {
        for (RoadNode candidate : network.getNodes().values()) {
            if (candidate == null || !RoadGraphQueries.isSimpleCrossing(candidate, network)) {
                continue;
            }
            List<String> roads = new ArrayList<>(network.getDistinctRoadIdsAtNode(candidate.getId()));
            if (roads.size() == 2 && roads.contains(roadA) && roads.contains(roadB)) {
                return candidate;
            }
        }
        return null;
    }

    private void mergeNodes(RoadNetwork network, String survivorId, String absorbedId) {
        ctx.editNetwork(() -> {
            Optional<String> merged = RoadGraphEdits.of(network).mergeJunctionNode(survivorId, absorbedId);
            if (merged.isEmpty()) {
                ctx.status().warning(PlotI18n.tr("plugin.road.complex_junction_merge_failed"));
                return;
            }
            ctx.networkManager().handleNodeSelect(merged.get());
            ctx.status().success(PlotI18n.tr("plugin.road.complex_junction_merge_success"));
            ctx.previewManager().invalidatePreview();
            ctx.requestOverlayRefresh();
        });
    }

    private void focusNode(RoadNetwork network, RoadNode node) {
        ctx.networkManager().handleNodeSelect(node.getId());
        for (String roadId : network.getDistinctRoadIdsAtNode(node.getId())) {
            Road road = network.getRoad(roadId);
            if (road != null && !road.getOrderedSegmentIds().isEmpty()) {
                ctx.networkManager().handleEdgeSelect(road.getOrderedSegmentIds().getFirst(), true);
            }
        }
        ctx.requestOverlayRefresh();
    }

    private static String formatRoadLabel(RoadNetwork network, String roadId) {
        Road road = network.getRoad(roadId);
        if (road == null) {
            return roadId;
        }
        return RoadEdgeListHelper.formatRoadLabel(network, road);
    }

    private static String formatNodeLabel(RoadNetwork network, RoadNode node) {
        List<String> roadIds = new ArrayList<>(network.getDistinctRoadIdsAtNode(node.getId()));
        if (roadIds.size() >= 2) {
            return PlotI18n.tr(
                "plugin.road.path.intersection_pair",
                formatRoadLabel(network, roadIds.get(0)),
                formatRoadLabel(network, roadIds.get(1)));
        }
        return node.getId();
    }
}
