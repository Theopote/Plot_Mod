package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadNetworkOverviewRenderer;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路概览 Tab：路网统计、缩略图、节点标高与边列表。
 */
public final class RoadOverviewPanel {
    private final RoadUiContext ctx;
    private final RoadEdgeListPanel edgeListPanel;
    private final RoadJunctionPanel junctionPanel;

    public RoadOverviewPanel(RoadUiContext ctx, RoadEdgeListPanel edgeListPanel, RoadJunctionPanel junctionPanel) {
        this.ctx = ctx;
        this.edgeListPanel = edgeListPanel;
        this.junctionPanel = junctionPanel;
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

        junctionPanel.renderSummary();
        renderNodeElevationEditor();

        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.road.edge_list"));
        edgeListPanel.renderToolbar("##overview");
        edgeListPanel.renderList(180, true, "edge_list");
    }

    private void renderNodeElevationEditor() {
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.node_elevation_settings"))) {
            return;
        }

        RoadNetwork network = ctx.networkManager().getNetwork();
        List<RoadNode> nodes = new ArrayList<>(network.getNodes().values());
        if (nodes.isEmpty()) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.no_nodes"));
            return;
        }

        ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.node_elevation_hint"));
        ImGui.beginChild("node_elevation_list", 0, 120, true);
        for (RoadNode node : nodes) {
            ImGui.pushID(node.getId());
            Vec2d pos = node.getPosition();
            String label = String.format("(%.0f, %.0f) deg=%d", pos.x, pos.y, node.getDegree());
            ImGui.text(label);
            ImGui.sameLine();

            boolean autoMode = node.getManualElevation() == null;
            ImBoolean autoRef = new ImBoolean(autoMode);
            if (ImGui.checkbox(PlotI18n.tr("plugin.road.node_elevation_auto") + "##auto", autoRef)) {
                ctx.networkManager().pushHistory();
                if (autoRef.get()) {
                    node.setManualElevation(null);
                } else {
                    node.setManualElevation(64.0);
                }
            }

            if (!autoRef.get()) {
                ImGui.sameLine();
                int initial = node.getManualElevation() != null
                    ? (int) Math.round(node.getManualElevation())
                    : 64;
                int[] elevation = {initial};
                if (ImGui.sliderInt("##elevation", elevation, -64, 320, "Y=%d")) {
                    node.setManualElevation((double) elevation[0]);
                }
                if (ImGui.isItemActivated()) {
                    ctx.networkManager().pushHistory();
                }
            }

            ImGui.popID();
        }
        ImGui.endChild();
    }
}
