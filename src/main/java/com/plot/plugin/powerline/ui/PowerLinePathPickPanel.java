package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.PowerLinePathSelectionAnalysis;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 电力线路路径拾取（Route Tab）。 */
public final class PowerLinePathPickPanel {
    private final PowerLineUiContext ctx;

    public PowerLinePathPickPanel(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    public void render(PowerLineFootprint line) {
        PowerLinePathSelectionAnalysis selection = ctx.pathSelection();

        if (ctx.isMultiLineSelection() && !ctx.pathPickSession().isActive() && !ctx.isPathReplacePending()) {
            PowerLineUiWidgets.renderMultiLineEditBlocked(ctx);
            return;
        }

        if (ctx.pathPickSession().isActive()) {
            renderPickingState(selection, line != null);
            return;
        }
        if (ctx.isPathReplacePending() && selection.canAdopt() && selection.adoptable().size() == 1) {
            renderReplaceConfirmState();
            return;
        }
        renderIdleState(line, selection);
    }

    private void renderIdleState(PowerLineFootprint line, PowerLinePathSelectionAnalysis selection) {
        renderSelectionErrors(selection);
        boolean replaceMode = line != null;
        String buttonKey = replaceMode
            ? "plugin.powerline.path.replace_path"
            : "plugin.powerline.path.create_from_path";
        String hintKey = replaceMode
            ? "plugin.powerline.path.replace_path_hint"
            : "plugin.powerline.path.create_from_path_hint";
        String idSuffix = replaceMode ? "powerline_path_replace_idle" : "powerline_path_create_idle";
        if (renderPathActionButton(buttonKey, idSuffix)) {
            if (replaceMode) {
                ctx.activatePathPickForReplace(line);
            } else {
                ctx.activatePathPickForCreate();
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr(hintKey));
        }
        if (!replaceMode && ctx.project().getLineCount() == 0) {
            ImGui.spacing();
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.path.no_lines"));
        }
    }

    private void renderPickingState(PowerLinePathSelectionAnalysis selection, boolean replaceMode) {
        int count = ctx.pathPickSession().getAccumulatedCount();
        String statusKey;
        if (count > 0) {
            statusKey = replaceMode
                ? "plugin.powerline.path.replace_picking_count"
                : "plugin.powerline.path.create_picking_count";
            PowerLineUiWidgets.textColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr(statusKey, count));
        } else {
            statusKey = replaceMode
                ? "plugin.powerline.path.replace_picking_active"
                : "plugin.powerline.path.create_picking_active";
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

    private void renderReplaceConfirmState() {
        PowerLineUiWidgets.textColored(
            PluginUiColors.STATUS_INFO,
            PlotI18n.tr("plugin.powerline.path.replace_ready"));
        ImGui.spacing();
        PowerLineUiWidgets.textColored(
            PluginUiColors.WARNING,
            PlotI18n.tr("plugin.powerline.path.replace_clear_intro"));
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.path.replace_clear_constraints"));
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.path.replace_clear_overrides"));
        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.path.apply_replace") + "##powerline_path_apply_replace", 0, 0)) {
            ctx.confirmPathReplace();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.path.cancel_replace") + "##powerline_path_cancel_replace", 0, 0)) {
            ctx.cancelPathReplaceConfirm();
        }
    }

    private boolean renderPathActionButton(String labelKey, String idSuffix) {
        boolean blocked = ctx.isPathPickActivationBlocked();
        if (blocked) {
            ImGui.beginDisabled();
        }
        boolean clicked = ImGui.button(
            PlotI18n.tr(labelKey) + "##" + idSuffix,
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
