package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.graph.RoadGraphQueries;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.terrain.FlatTerrainSampler;
import com.plot.plugin.road.terrain.TerrainSampler;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点级属性编辑：选中节点详情 + 可折叠全网节点列表（巡查用）。
 */
public final class RoadNodePropertyPanel {
    private final RoadUiContext ctx;

    public RoadNodePropertyPanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    public void renderForSelectedNode(RoadJunctionPanel junctionPanel) {
        String selectedNodeId = ctx.networkManager().getSelectedNodeId();
        if (selectedNodeId == null || selectedNodeId.isBlank()) {
            return;
        }

        RoadNetwork network = ctx.networkManager().getNetwork();
        RoadNode node = network.getNode(selectedNodeId);
        if (node == null) {
            return;
        }

        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.road.node_selected_detail"));
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.node_elevation_hint"));

        RoadSystemConfig config = ctx.networkManager().getConfig();
        renderNodeElevationControls(node, network, config, false);

        junctionPanel.renderEditor();
    }

    public void renderAllNodesCollapsibleList() {
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.all_nodes"))) {
            return;
        }

        RoadNetwork network = ctx.networkManager().getNetwork();
        RoadSystemConfig config = ctx.networkManager().getConfig();
        List<RoadNode> nodes = new ArrayList<>(network.getNodes().values());
        if (nodes.isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.no_nodes"));
            return;
        }

        String selectedNodeId = ctx.networkManager().getSelectedNodeId();
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.node_elevation_list_hint"));
        ImGui.beginChild("node_elevation_list", 0, 220, true);
        for (RoadNode node : nodes) {
            ImGui.pushID(node.getId());
            boolean selected = node.getId().equals(selectedNodeId);
            if (selected) {
                ImGui.textColored(PluginUiColors.STATUS_INFO, "▸ " + formatNodeLabel(node));
            } else {
                ImGui.text(formatNodeLabel(node));
            }
            ImGui.sameLine();
            renderNodeElevationControls(node, network, config, true);
            ImGui.sameLine();
            if (ImGui.smallButton(PlotI18n.tr("plugin.road.locate") + "##locate")) {
                ctx.networkManager().handleNodeSelect(node.getId());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PlotI18n.tr("plugin.road.node_locate_hint"));
            }
            ImGui.popID();
        }
        ImGui.endChild();
    }

    private void renderNodeElevationControls(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            boolean inline) {
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
            if (ImGui.sliderInt("##elevation", elevation,
                RoadParameterLimits.ELEVATION_MIN,
                RoadParameterLimits.ELEVATION_MAX,
                "Y=%d")) {
                node.setManualElevation((double) elevation[0]);
            }
            if (!inline && ImGui.isItemHovered()) {
                ImGui.setTooltip(PlotI18n.tr("hint.plot.road.node_elevation"));
            }
            if (ImGui.isItemActivated()) {
                ctx.networkManager().pushHistory();
            }
        }

        if (inline) {
            renderGradeSeparationControlsInline(node, network, config);
        } else {
            renderGradeSeparationControlsBlock(node, network, config);
        }
    }

    private static String formatNodeLabel(RoadNode node) {
        Vec2d pos = node.getPosition();
        return String.format("(%.0f, %.0f) deg=%d", pos.x, pos.y, node.getDegree());
    }

    private void renderGradeSeparationControlsInline(RoadNode node, RoadNetwork network, RoadSystemConfig config) {
        if (!RoadGraphQueries.isSimpleCrossing(node, network)) {
            return;
        }

        List<String> roadIds = new ArrayList<>(network.getDistinctRoadIdsAtNode(node.getId()));
        if (roadIds.size() != 2) {
            return;
        }

        String[] labels = buildGradeSeparationLabels(roadIds, network);
        int currentIndex = gradeSeparationIndex(node, roadIds);

        ImGui.sameLine();
        ImInt index = new ImInt(currentIndex);
        if (ImGui.combo(PlotI18n.tr("plugin.road.grade_separation") + "##grade_sep", index, labels)) {
            applyGradeSeparationSelection(node, network, config, roadIds, index.get());
        }

        if (node.isGradeSeparated()) {
            renderClearanceSlider(node, network, config, true);
            if (node.getElevatedRoadId() == null) {
                renderAutoElevatedRoadHint(node, network, config);
            }
        }

        if (node.getManualElevation() != null && node.isGradeSeparated()) {
            ImGui.textColored(
                PluginUiColors.WARNING_STRONG,
                PlotI18n.tr("plugin.road.grade_separation_manual_override"));
        }
    }

    private void renderGradeSeparationControlsBlock(RoadNode node, RoadNetwork network, RoadSystemConfig config) {
        if (!RoadGraphQueries.isSimpleCrossing(node, network)) {
            return;
        }

        List<String> roadIds = new ArrayList<>(network.getDistinctRoadIdsAtNode(node.getId()));
        if (roadIds.size() != 2) {
            return;
        }

        String[] labels = buildGradeSeparationLabels(roadIds, network);
        int currentIndex = gradeSeparationIndex(node, roadIds);

        ImInt index = new ImInt(currentIndex);
        if (ImGui.combo(PlotI18n.tr("plugin.road.grade_separation") + "##grade_sep", index, labels)) {
            applyGradeSeparationSelection(node, network, config, roadIds, index.get());
        }

        if (node.isGradeSeparated()) {
            renderClearanceSlider(node, network, config, false);
            if (node.getElevatedRoadId() == null) {
                renderAutoElevatedRoadHint(node, network, config);
            }
        }

        if (node.getManualElevation() != null && node.isGradeSeparated()) {
            ImGui.textColored(
                PluginUiColors.WARNING_STRONG,
                PlotI18n.tr("plugin.road.grade_separation_manual_override"));
        }
    }

    private String[] buildGradeSeparationLabels(List<String> roadIds, RoadNetwork network) {
        String[] labels = new String[roadIds.size() + 2];
        labels[0] = PlotI18n.tr("plugin.road.grade_separation_none");
        labels[1] = PlotI18n.tr("plugin.road.grade_separation_auto");
        for (int i = 0; i < roadIds.size(); i++) {
            labels[i + 2] = formatRoadLabel(network, roadIds.get(i));
        }
        return labels;
    }

    private static int gradeSeparationIndex(RoadNode node, List<String> roadIds) {
        if (!node.isGradeSeparated()) {
            return 0;
        }
        if (node.getElevatedRoadId() == null) {
            return 1;
        }
        int roadIndex = roadIds.indexOf(node.getElevatedRoadId());
        return roadIndex >= 0 ? roadIndex + 2 : 0;
    }

    private void applyGradeSeparationSelection(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            List<String> roadIds,
            int index) {
        ctx.networkManager().pushHistory();
        double clearance = node.getCrossingClearance() != null
            ? node.getCrossingClearance()
            : config.getDefaultCrossingClearance();
        if (index == 0) {
            network.setNodeGradeSeparation(node.getId(), false, null, null);
        } else if (index == 1) {
            network.setNodeGradeSeparation(node.getId(), true, null, clearance);
        } else {
            String selectedRoadId = roadIds.get(index - 2);
            network.setNodeGradeSeparation(node.getId(), true, selectedRoadId, clearance);
        }
    }

    private void renderClearanceSlider(
            RoadNode node,
            RoadNetwork network,
            RoadSystemConfig config,
            boolean inline) {
        double currentClearance = node.getCrossingClearance() != null
            ? node.getCrossingClearance()
            : config.getDefaultCrossingClearance();
        int[] clearance = {(int) Math.round(currentClearance)};
        if (inline) {
            ImGui.sameLine();
        }
        if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.crossing_clearance") + "##clearance",
                clearance,
                RoadParameterLimits.MIN_CROSSING_CLEARANCE,
                RoadParameterLimits.MAX_CROSSING_CLEARANCE,
                "%d")) {
            node.setCrossingClearance((double) clearance[0]);
        }
        if (ImGui.isItemActivated()) {
            ctx.networkManager().pushHistory();
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
            PluginUiColors.HINT_GRAY,
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
