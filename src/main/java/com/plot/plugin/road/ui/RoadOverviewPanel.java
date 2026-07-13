package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadNetworkOverviewRenderer;
import com.plot.plugin.road.graph.RoadGraphQueries;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import com.plot.plugin.road.terrain.TerrainSampler;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import net.minecraft.world.World;

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
        ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.edge_list_hint"));
        edgeListPanel.renderToolbar("##overview");
        edgeListPanel.renderList(180, true, "edge_list");
    }

    private void renderNodeElevationEditor() {
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.node_elevation_settings"))) {
            return;
        }

        RoadNetwork network = ctx.networkManager().getNetwork();
        RoadSystemConfig config = ctx.networkManager().getConfig();
        List<RoadNode> nodes = new ArrayList<>(network.getNodes().values());
        if (nodes.isEmpty()) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.no_nodes"));
            return;
        }

        ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.node_elevation_hint"));
        ImGui.beginChild("node_elevation_list", 0, 220, true);
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

            renderGradeSeparationControls(node, network, config);

            ImGui.popID();
        }
        ImGui.endChild();
    }

    private void renderGradeSeparationControls(RoadNode node, RoadNetwork network, RoadSystemConfig config) {
        if (!RoadGraphQueries.isSimpleCrossing(node, network)) {
            return;
        }

        List<String> roadIds = new ArrayList<>(network.getDistinctRoadIdsAtNode(node.getId()));
        if (roadIds.size() != 2) {
            return;
        }

        String[] labels = new String[roadIds.size() + 2];
        labels[0] = PlotI18n.tr("plugin.road.grade_separation_none");
        labels[1] = PlotI18n.tr("plugin.road.grade_separation_auto");
        for (int i = 0; i < roadIds.size(); i++) {
            labels[i + 2] = formatRoadLabel(network, roadIds.get(i));
        }

        int currentIndex = 0;
        if (node.isGradeSeparated()) {
            if (node.getElevatedRoadId() == null) {
                currentIndex = 1;
            } else {
                int roadIndex = roadIds.indexOf(node.getElevatedRoadId());
                if (roadIndex >= 0) {
                    currentIndex = roadIndex + 2;
                }
            }
        }

        ImGui.sameLine();
        ImInt index = new ImInt(currentIndex);
        if (ImGui.combo(PlotI18n.tr("plugin.road.grade_separation") + "##grade_sep", index, labels)) {
            ctx.networkManager().pushHistory();
            double clearance = node.getCrossingClearance() != null
                ? node.getCrossingClearance()
                : config.getDefaultCrossingClearance();
            if (index.get() == 0) {
                network.setNodeGradeSeparation(node.getId(), false, null, null);
            } else if (index.get() == 1) {
                network.setNodeGradeSeparation(node.getId(), true, null, clearance);
            } else {
                String selectedRoadId = roadIds.get(index.get() - 2);
                network.setNodeGradeSeparation(node.getId(), true, selectedRoadId, clearance);
            }
        }

        if (node.isGradeSeparated()) {
            double currentClearance = node.getCrossingClearance() != null
                ? node.getCrossingClearance()
                : config.getDefaultCrossingClearance();
            int[] clearance = {(int) Math.round(currentClearance)};
            if (ImGui.sliderInt(
                    PlotI18n.tr("plugin.road.crossing_clearance") + "##clearance",
                    clearance,
                    1,
                    10,
                    "%d")) {
                node.setCrossingClearance((double) clearance[0]);
            }
            if (ImGui.isItemActivated()) {
                ctx.networkManager().pushHistory();
            }

            if (node.getElevatedRoadId() == null) {
                renderAutoElevatedRoadHint(node, network, config);
            }
        }

        if (node.getManualElevation() != null && node.isGradeSeparated()) {
            ImGui.textColored(
                (int) 0xFFFF8800FFL,
                PlotI18n.tr("plugin.road.grade_separation_manual_override"));
        }
    }

    private void renderAutoElevatedRoadHint(RoadNode node, RoadNetwork network, RoadSystemConfig config) {
        RoadGenerator generator = new RoadGenerator(config, null);
        TerrainSampler terrain = resolveTerrainSampler(generator);
        String resolvedRoadId = generator.resolveElevatedRoadId(node, network, terrain);
        if (resolvedRoadId == null) {
            return;
        }
        ImGui.textColored(
            (int) 0xFF808080FFL,
            PlotI18n.tr(
                "plugin.road.grade_separation_auto_result",
                formatRoadLabel(network, resolvedRoadId)));
    }

    private static TerrainSampler resolveTerrainSampler(RoadGenerator generator) {
        World world = RoadNetworkGenerator.getClientWorld();
        if (world != null) {
            return generator.createTerrainSampler(world);
        }
        return new FlatTerrainSampler(TerrainSampler.DEFAULT_SEA_LEVEL);
    }

    private static String formatRoadLabel(RoadNetwork network, String roadId) {
        Road road = network.getRoad(roadId);
        if (road != null && road.getName() != null && !road.getName().isBlank()) {
            return road.getName();
        }
        String shortId = roadId.length() > 6 ? roadId.substring(0, 6) : roadId;
        return PlotI18n.tr("plugin.road.road_label_fallback", shortId);
    }
}
