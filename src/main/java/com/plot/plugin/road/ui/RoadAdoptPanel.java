package com.plot.plugin.road.ui;

import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 路径认领后的路口修复提示。 */
public final class RoadAdoptPanel {
    private final RoadUiContext ctx;

    public RoadAdoptPanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    void renderIntersectionRepairPrompt() {
        if (!ctx.networkManager().isAdoptIntersectionRepairPending()) {
            return;
        }
        ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.road.adopt_intersection_repair_prompt"));
        if (ImGui.button(PlotI18n.tr("plugin.road.validation.reconcile_intersections") + "##adopt_reconcile")) {
            RoadTopologyWorkflow.reconcileIntersections(ctx, false);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.adopt_intersection_repair_hint"));
        }
        ImGui.spacing();
    }
}
