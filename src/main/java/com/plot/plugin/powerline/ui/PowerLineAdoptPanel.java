package com.plot.plugin.powerline.ui;

import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 电力线路路径认领（Route Tab）。 */
public final class PowerLineAdoptPanel {
    private final PowerLineUiContext ctx;

    public PowerLineAdoptPanel(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.adopt_hint"));
        ImGui.spacing();
        ctx.updateSelectedPaths();
        var selection = ctx.pathSelection();

        if (selection.canAdopt()) {
            ImGui.text(PlotI18n.tr(
                "plugin.powerline.paths_selected",
                selection.adoptable().size()));
        }
        if (!selection.rejectedCurves().isEmpty()) {
            ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.powerline.adopt_reject_curve"));
        }
        if (!selection.unsupported().isEmpty() && !selection.canAdopt()) {
            ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.powerline.adopt_unsupported_hint"));
        } else if (!selection.hasCanvasSelection()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.draw_path_hint"));
        }

        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.pick_path"), 0, 0)) {
            ctx.activatePathPickTool();
        }
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.pick_mode_hint"));

        ImGui.spacing();
        boolean adoptDisabled = !selection.canAdopt();
        if (adoptDisabled) {
            ImGui.beginDisabled();
        }
        String label = selection.adoptable().size() > 1
            ? PlotI18n.tr("plugin.powerline.adopt_batch", selection.adoptable().size())
            : PlotI18n.tr("plugin.powerline.adopt");
        if (ImGui.button(label, 0, 0)) {
            ctx.adoptSelectedPaths();
        }
        if (adoptDisabled && ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip(PlotI18n.tr("plugin.powerline.adopt_unsupported_hint"));
        }
        if (adoptDisabled) {
            ImGui.endDisabled();
        }
    }
}
