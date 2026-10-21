package com.plot.plugin.road.ui.design;

import com.plot.plugin.road.ui.RoadNetworkToolsPanel;
import com.plot.plugin.road.ui.RoadUiWidgets;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** Advanced terrain and network-level tools. */
public final class RoadTerrainToolsSection {
    private final RoadNetworkToolsPanel networkToolsPanel;

    public RoadTerrainToolsSection(RoadNetworkToolsPanel networkToolsPanel) {
        this.networkToolsPanel = networkToolsPanel;
    }

    public void render(RoadDesignSectionContext ctx) {
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.generate.advanced_terrain"))) {
            return;
        }
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.generate.advanced_terrain_hint"));
        networkToolsPanel.render(ctx.network);
    }

    public void renderConfirmPopup() {
        networkToolsPanel.renderConfirmPopup();
    }
}
