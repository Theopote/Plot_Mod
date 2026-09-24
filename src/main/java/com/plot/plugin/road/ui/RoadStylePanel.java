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
    private final RoadDefaultParamsPanel defaultParamsPanel;
    private final RoadEditPanel editPanel;

    public RoadStylePanel(
            RoadUiContext ctx,
            RoadDefaultParamsPanel defaultParamsPanel,
            RoadEditPanel editPanel) {
        this.ctx = ctx;
        this.defaultParamsPanel = defaultParamsPanel;
        this.editPanel = editPanel;
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        renderStyleHeader(network);

        if (network.getEdges().isEmpty()) {
            ImGui.separator();
            defaultParamsPanel.renderCrossSectionStep();
            return;
        }

        ImGui.separator();
        editPanel.render();
    }

    public void renderUniformElevationConfirmPopup() {
        editPanel.renderUniformElevationConfirmPopup();
    }

    private void renderStyleHeader(RoadNetwork network) {
        if (network.getEdges().isEmpty()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.style.no_roads_hint"));
            return;
        }
        var roadIds = ctx.networkManager().getSelectedRoadIds();
        String selectedNodeId = ctx.networkManager().getSelectedNodeId();
        if (roadIds.isEmpty() && (selectedNodeId == null || selectedNodeId.isBlank())) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.style.select_road_hint"));
            return;
        }
        if (roadIds.size() == 1) {
            var road = network.getRoad(roadIds.getFirst());
            if (road != null) {
                RoadUiWidgets.textWrappedColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr("plugin.road.style.editing_road", road.getName()));
            }
        } else if (roadIds.size() > 1) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.style.editing_multi", roadIds.size()));
        }
    }
}
