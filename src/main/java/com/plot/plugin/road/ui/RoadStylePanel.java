package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 样式 Tab：材质、附属设施与高级道路设计（预设与基础尺寸在路线 Tab）。
 */
public final class RoadStylePanel {
    private final RoadUiContext ctx;
    private final RoadEditPanel editPanel;
    private final RoadStyleWorkspace styleWorkspace;

    public RoadStylePanel(
            RoadUiContext ctx,
            RoadDefaultParamsPanel defaultParamsPanel,
            RoadEditPanel editPanel) {
        this.ctx = ctx;
        this.editPanel = editPanel;
        this.styleWorkspace = new RoadStyleWorkspace(ctx, editPanel.designPanel());
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
            editPanel.renderNodeJunctionSection();
            return;
        }

        RoadSelectionHeader.Mode mode = RoadSelectionHeader.resolveMode(ctx);
        if (mode == RoadSelectionHeader.Mode.NONE) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.style.select_road_hint"));
            return;
        }
        if (mode == RoadSelectionHeader.Mode.MULTI) {
            styleWorkspace.renderMulti();
        } else {
            Road road = ctx.networkManager().getPrimarySelectedRoad();
            if (road != null) {
                styleWorkspace.renderSingle(network, road);
            }
        }

        ImGui.separator();
        editPanel.renderAdvancedNetworkSection();
    }

    public void renderUniformElevationConfirmPopup() {
        editPanel.renderUniformElevationConfirmPopup();
    }
}
