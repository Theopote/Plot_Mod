package com.plot.plugin.building.ui;

import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 建筑认领 Tab 与画布选区会话。 */
public final class BuildingAdoptPanel {
    private final BuildingUiContext ctx;

    public BuildingAdoptPanel(BuildingUiContext ctx) {
        this.ctx = ctx;
    }

    public void tickPickSession() {
        ctx.handlePickSessionTick();
    }

    public void render() {
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.adopt_hint"));
        ImGui.spacing();

        if (ctx.pickSession().isActive()) {
            int count = ctx.pickSession().getAccumulatedCount();
            if (count > 0) {
                ImGui.text(String.format(PlotI18n.tr("plugin.building.footprints_selected"), count));
            }
        } else {
            ctx.updateSelectedFootprints();
        }

        if (!ctx.selectedFootprints().isEmpty()) {
            ImGui.text(PlotI18n.tr(
                "plugin.building.footprints_selected_detail",
                ctx.selectedFootprints().size(),
                String.format("%.1f", ctx.computeSelectedFootprintArea())));
        } else {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.draw_footprint_hint"));
        }

        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.building.select_all_closed"), 0, 0)) {
            ctx.selectAllClosedShapesOnCanvas();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.building.pick_footprint"), 0, 0)) {
            ctx.startPickSession();
        }
        ImGui.sameLine();
        boolean adoptDisabled = ctx.selectedFootprints().isEmpty();
        if (adoptDisabled) {
            ImGui.beginDisabled();
        }
        String adoptLabel = ctx.selectedFootprints().size() > 1
            ? PlotI18n.tr("plugin.building.adopt_footprint_batch", ctx.selectedFootprints().size())
            : PlotI18n.tr("plugin.building.adopt_footprint");
        if (ImGui.button(adoptLabel, 0, 0)) {
            ctx.adoptSelectedFootprints();
        }
        if (adoptDisabled) {
            ImGui.endDisabled();
        }
    }
}
