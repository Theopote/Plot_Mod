package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 编辑 Tab：道路类型、横断面、材质与高级道路设计（围绕当前选择）。
 */
public final class RoadEditPanel {
    private final RoadUiContext ctx;
    private final RoadDesignPanel designPanel;
    private final RoadEditWorkspace editWorkspace;
    private final RoadIntersectionDetailPanel intersectionDetailPanel;
    private final RoadJunctionPanel junctionPanel;

    public RoadEditPanel(
            RoadUiContext ctx,
            RoadDefaultParamsPanel defaultParamsPanel,
            RoadIntersectionDetailPanel intersectionDetailPanel,
            RoadJunctionPanel junctionPanel) {
        this.ctx = ctx;
        this.designPanel = new RoadDesignPanel(ctx);
        this.editWorkspace = new RoadEditWorkspace(ctx, designPanel, defaultParamsPanel);
        this.intersectionDetailPanel = intersectionDetailPanel;
        this.junctionPanel = junctionPanel;
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        if (network.getEdges().isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.style.no_roads_hint"));
            return;
        }

        ctx.networkManager().ensureSelectionValid();
        renderFocusedIntersection(network);

        RoadSelectionHeader.Mode mode = RoadSelectionHeader.resolveMode(ctx);
        switch (mode) {
            case NONE -> editWorkspace.renderNoSelectionDefaults();
            case SINGLE -> {
                var road = ctx.networkManager().getPrimarySelectedRoad();
                if (road != null) {
                    editWorkspace.renderSingle(network, road);
                }
            }
            case MULTI -> editWorkspace.renderMulti();
        }
    }

    RoadDesignPanel designPanel() {
        return designPanel;
    }

    public void renderUniformElevationConfirmPopup() {
        designPanel.renderUniformElevationConfirmPopup();
    }

    private void renderFocusedIntersection(RoadNetwork network) {
        if (intersectionDetailPanel == null
                || !RoadIntersectionPropertySupport.hasIntersectionSelection(ctx.networkManager())) {
            return;
        }
        intersectionDetailPanel.renderFocusedSelection(
            network, RoadGradeSeparationControls.Layout.BLOCK);
        RoadIntersectionGeometrySection.render(ctx, network, junctionPanel);
        ImGui.separator();
        ImGui.spacing();
    }
}
