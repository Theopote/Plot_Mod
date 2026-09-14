package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.PowerLinePathSelectionAnalysis;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 电力线路路径拾取（Route Tab）。 */
public final class PowerLineAdoptPanel {
    private final PowerLineUiContext ctx;

    public PowerLineAdoptPanel(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    public void render(PowerLineFootprint line) {
        PowerLinePathSelectionAnalysis selection = ctx.pathSelection();

        if (ctx.pathPickSession().isActive()) {
            renderPickingState(selection);
            return;
        }
        renderIdleState(line, selection);
    }

    private void renderIdleState(PowerLineFootprint line, PowerLinePathSelectionAnalysis selection) {
        renderSelectionErrors(selection);
        if (renderPickPathButton("powerline_path_pick_idle")) {
            ctx.activatePathPickTool();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.powerline.pick_path_hint"));
        }
        if (line != null) {
            ImGui.spacing();
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.path.replace_hint"));
        } else if (ctx.project().getLineCount() == 0) {
            ImGui.spacing();
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.path.no_lines"));
        }
    }

    private void renderPickingState(PowerLinePathSelectionAnalysis selection) {
        int count = ctx.pathPickSession().getAccumulatedCount();
        String statusKey = count > 0
            ? "plugin.powerline.path.picking_count"
            : "plugin.powerline.path.picking_active";
        if (count > 0) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr(statusKey, count));
        } else {
            PowerLineUiWidgets.textColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr(statusKey));
        }
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.path.right_click_finish"));
        if (ImGui.button(PlotI18n.tr("plugin.powerline.path.cancel_pick") + "##powerline_path_cancel_pick", 0, 0)) {
            ctx.cancelPathPick();
        }
        renderSelectionErrors(selection);
    }

    private boolean renderPickPathButton(String idSuffix) {
        boolean blocked = ctx.isPathPickActivationBlocked();
        if (blocked) {
            ImGui.beginDisabled();
        }
        boolean clicked = ImGui.button(
            PlotI18n.tr("plugin.powerline.pick_path") + "##" + idSuffix,
            0,
            0);
        if (blocked) {
            ImGui.endDisabled();
        }
        return clicked && !blocked;
    }

    private void renderSelectionErrors(PowerLinePathSelectionAnalysis selection) {
        if (!selection.rejectedCurves().isEmpty()) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.powerline.path.invalid_selection"));
            return;
        }
        if (!selection.unsupported().isEmpty() && !selection.canAdopt()) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.powerline.path.invalid_selection"));
        }
    }
}
