package com.plot.plugin.powerline.ui;

import com.plot.core.command.Command;
import com.plot.core.command.CommandService;
import com.plot.plugin.powerline.placement.PowerLineWorldCommandSync;
import com.plot.plugin.powerline.placement.PowerLineWorldCommands;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 电力线路插件顶部工具栏。 */
public final class PowerLineToolbarPanel {
    private final PowerLineUiContext ctx;

    public PowerLineToolbarPanel(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        renderProjectHistoryToolbar();
        renderWorldPlacementToolbar();
        renderActivePlacementControls();

        if (!ctx.projectStatus().isEmpty()) {
            PowerLineUiWidgets.textColored(ctx.projectStatusSeverity().color(), ctx.projectStatus());
        }
        ImGui.separator();
    }

    private void renderProjectHistoryToolbar() {
        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;

        boolean undoDisabled = !ctx.projectHistory().canUndo();
        if (undoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.undo"), buttonWidth, 0)) {
            ctx.setProject(ctx.projectHistory().undo(ctx.project()));
            ctx.selection().retainExisting(ctx.project());
            ctx.setLineNameEditingId("");
            ctx.invalidatePreview();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.powerline.undo_project"));
        }
        if (undoDisabled) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean redoDisabled = !ctx.projectHistory().canRedo();
        if (redoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.redo"), buttonWidth, 0)) {
            ctx.setProject(ctx.projectHistory().redo(ctx.project()));
            ctx.selection().retainExisting(ctx.project());
            ctx.setLineNameEditingId("");
            ctx.invalidatePreview();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.powerline.redo_project"));
        }
        if (redoDisabled) {
            ImGui.endDisabled();
        }

        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.undo_scope_hint"));
    }

    private void renderWorldPlacementToolbar() {
        CommandService commands = ctx.host().commands();
        Command undoCommand = commands.peekUndoCommand();
        Command redoCommand = peekRedoCommand(commands);

        if (!PowerLineWorldCommands.isPowerLineWorldCommand(undoCommand)
                && !PowerLineWorldCommands.isPowerLineWorldCommand(redoCommand)) {
            return;
        }

        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.undo_world_section"));

        float halfWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        boolean undoWorldDisabled = !PowerLineWorldCommands.isPowerLineWorldCommand(undoCommand);
        if (undoWorldDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.undo_world"), halfWidth, 0)) {
            if (commands.undo()) {
                PowerLineWorldCommandSync.afterUndo(undoCommand, ctx.state());
                ctx.invalidatePreview();
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.powerline.undo_world"));
        }
        if (undoWorldDisabled) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean redoWorldDisabled = !PowerLineWorldCommands.isPowerLineWorldCommand(redoCommand);
        if (redoWorldDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.redo_world"), halfWidth, 0)) {
            if (commands.redo()) {
                PowerLineWorldCommandSync.afterRedo(redoCommand, ctx.state());
                ctx.invalidatePreview();
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.powerline.redo_world"));
        }
        if (redoWorldDisabled) {
            ImGui.endDisabled();
        }
    }

    private void renderActivePlacementControls() {
        com.plot.api.world.IBlockPlacementService scheduler = ctx.host().placement();
        if (!scheduler.isBusy()) {
            return;
        }

        com.plot.api.world.IBlockPlacementService.ProgressSnapshot progress = scheduler.getProgressSnapshot();
        if (progress != null) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr("plugin.powerline.placement_progress", progress.processed(), progress.total()));
        } else {
            PowerLineUiWidgets.textColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr("plugin.powerline.build_in_progress_wait"));
        }

        if (ImGui.button(PlotI18n.tr("plugin.powerline.cancel_placement"), 0, 0)) {
            scheduler.cancelAll();
        }
    }

    private static Command peekRedoCommand(CommandService commands) {
        if (commands == null || !commands.canRedo()) {
            return null;
        }
        int nextIndex = commands.getCurrentIndex() + 1;
        if (nextIndex < 0 || nextIndex >= commands.size()) {
            return null;
        }
        return commands.history().get(nextIndex);
    }
}
