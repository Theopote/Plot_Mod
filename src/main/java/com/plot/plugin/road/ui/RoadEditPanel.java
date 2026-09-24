package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 编辑 Tab：道路类型、横断面、材质与高级道路设计（围绕当前选择）。
 */
public final class RoadEditPanel {
    private final RoadUiContext ctx;
    private final RoadJunctionPanel junctionPanel;
    private final RoadNodePropertyPanel nodePropertyPanel;
    private final RoadNetworkToolsPanel networkToolsPanel;
    private final RoadDesignPanel designPanel;
    private final RoadEditWorkspace editWorkspace;

    public RoadEditPanel(
            RoadUiContext ctx,
            RoadJunctionPanel junctionPanel,
            RoadNodePropertyPanel nodePropertyPanel,
            RoadDefaultParamsPanel defaultParamsPanel) {
        this.ctx = ctx;
        this.junctionPanel = junctionPanel;
        this.nodePropertyPanel = nodePropertyPanel;
        this.networkToolsPanel = new RoadNetworkToolsPanel(ctx);
        this.designPanel = new RoadDesignPanel(ctx);
        this.editWorkspace = new RoadEditWorkspace(ctx, designPanel, defaultParamsPanel);
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        if (network.getEdges().isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.style.no_roads_hint"));
            return;
        }

        ctx.networkManager().ensureSelectionValid();
        RoadSelectionHeader.render(ctx);
        ImGui.separator();

        String selectedNodeId = ctx.networkManager().getSelectedNodeId();
        if (selectedNodeId != null && !selectedNodeId.isBlank()) {
            renderNodeJunctionSection();
            return;
        }

        RoadSelectionHeader.Mode mode = RoadSelectionHeader.resolveMode(ctx);
        switch (mode) {
            case NONE -> editWorkspace.renderNoSelectionDefaults();
            case SINGLE -> {
                Road road = ctx.networkManager().getPrimarySelectedRoad();
                if (road != null) {
                    editWorkspace.renderSingle(network, road);
                }
            }
            case MULTI -> editWorkspace.renderMulti();
        }

        ImGui.separator();
        renderAdvancedNetworkSection();
    }

    RoadDesignPanel designPanel() {
        return designPanel;
    }

    void renderNodeJunctionSection() {
        RoadUiSections.group("plugin.road.section.node_junction");
        nodePropertyPanel.renderForSelectedNode(junctionPanel);
    }

    void renderAdvancedNetworkSection() {
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.style.advanced_network"))) {
            return;
        }
        RoadNetwork network = ctx.networkManager().getNetwork();
        networkToolsPanel.render(network);
        ImGui.separator();
        nodePropertyPanel.renderAllNodesCollapsibleList();
    }

    public void renderUniformElevationConfirmPopup() {
        networkToolsPanel.renderConfirmPopup();
    }
}
