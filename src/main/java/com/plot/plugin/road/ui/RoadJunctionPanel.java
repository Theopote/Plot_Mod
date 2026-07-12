package com.plot.plugin.road.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.RoadNode;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 交叉口属性编辑（概览/编辑 Tab 与 PropertyPanel 共用）。
 */
public final class RoadJunctionPanel {
    private final RoadUiContext ctx;

    public RoadJunctionPanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    public void renderSummary() {
        RoadNode node = ctx.networkManager().getSelectedJunctionNode();
        if (node == null) {
            return;
        }
        ImGui.spacing();
        renderControls(node, false);
    }

    public void renderEditor() {
        RoadNode node = ctx.networkManager().getSelectedJunctionNode();
        if (node == null) {
            return;
        }
        ImGui.separator();
        renderControls(node, false);
    }

    public void renderPropertySection() {
        RoadNode node = ctx.networkManager().getSelectedJunctionNode();
        if (node == null) {
            return;
        }
        renderControls(node, true);
    }

    private void renderControls(RoadNode node, boolean compact) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        RoadNetworkBuilder.JunctionType type = ctx.networkManager().getNetworkBuilder().classify(node);
        Vec2d pos = node.getPosition();
        ImGui.text(PlotI18n.tr("plugin.road.junction_selected",
            RoadNetworkManager.junctionTypeLabel(type), pos.x, pos.y, node.getDegree()));

        if (!compact) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.junction_corner_hint"));
        }

        double effectiveRadius = node.getEffectiveCornerRadius(config.getDefaultCornerRadius());
        float[] cornerRadius = {(float) (node.getCornerRadius() != null
            ? node.getCornerRadius()
            : config.getDefaultCornerRadius())};
        if (ImGui.sliderFloat(
            PlotI18n.tr("plugin.road.junction_corner_radius", effectiveRadius),
            cornerRadius,
            0.0f,
            (float) RoadNode.MAX_CORNER_RADIUS,
            "%.1f m"
        )) {
            ctx.networkManager().pushHistory();
            node.setCornerRadius((double) cornerRadius[0]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.junction_corner_radius"));
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.junction_apply_default_radius"))) {
            ctx.networkManager().pushHistory();
            node.setCornerRadius(null);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.junction_clear_selection"))) {
            ctx.networkManager().setSelectedNodeId("");
        }
    }
}
