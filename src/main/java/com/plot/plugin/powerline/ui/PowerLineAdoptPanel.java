package com.plot.plugin.powerline.ui;

import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 电力线路认领 Tab。 */
public final class PowerLineAdoptPanel {
    private final PowerLineUiContext ctx;

    public PowerLineAdoptPanel(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.adopt_hint"));
        ImGui.spacing();
        ctx.updateSelectedPaths();

        if (!ctx.selectedPaths().isEmpty()) {
            ImGui.text(PlotI18n.tr(
                "plugin.powerline.paths_selected",
                ctx.selectedPaths().size()));
        } else {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.draw_path_hint"));
        }

        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.pick_path"), 0, 0)) {
            ctx.activatePathPickTool();
        }

        ImGui.spacing();
        boolean adoptDisabled = ctx.selectedPaths().isEmpty();
        if (adoptDisabled) {
            ImGui.beginDisabled();
        }
        String label = ctx.selectedPaths().size() > 1
            ? PlotI18n.tr("plugin.powerline.adopt_batch", ctx.selectedPaths().size())
            : PlotI18n.tr("plugin.powerline.adopt");
        if (ImGui.button(label, 0, 0)) {
            ctx.adoptSelectedPaths();
        }
        if (adoptDisabled) {
            ImGui.endDisabled();
        }
    }
}
