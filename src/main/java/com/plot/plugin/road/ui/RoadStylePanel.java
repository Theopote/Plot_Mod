package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 样式 Tab：横断面与道路设计（选中道路的设计栈、节点/批量编辑、默认横断面）。
 */
public final class RoadStylePanel {
    private final RoadUiContext ctx;
    private final RoadEditPanel editPanel;

    public RoadStylePanel(
            RoadUiContext ctx,
            RoadDefaultParamsPanel defaultParamsPanel,
            RoadEditPanel editPanel) {
        this.ctx = ctx;
        this.editPanel = editPanel;
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();

        if (network.getEdges().isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.style.no_roads_hint"));
            return;
        }

        editPanel.render();
    }

    public void renderUniformElevationConfirmPopup() {
        editPanel.renderUniformElevationConfirmPopup();
    }
}
