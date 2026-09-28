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
    private final RoadDesignPanel designPanel;
    private final RoadEditWorkspace editWorkspace;

    public RoadEditPanel(RoadUiContext ctx, RoadDefaultParamsPanel defaultParamsPanel) {
        this.ctx = ctx;
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
        renderJunctionSelectionHint();
        ImGui.separator();

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
    }

    RoadDesignPanel designPanel() {
        return designPanel;
    }

    private void renderJunctionSelectionHint() {
        String selectedNodeId = ctx.networkManager().getSelectedNodeId();
        if (selectedNodeId == null || selectedNodeId.isBlank()) {
            return;
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.edit.junction_selected_hint"));
        if (ImGui.button(PlotI18n.tr("plugin.road.edit.goto_intersections"))) {
            ctx.requestTab(RoadUiTab.PATH);
        }
        ImGui.spacing();
    }
}
