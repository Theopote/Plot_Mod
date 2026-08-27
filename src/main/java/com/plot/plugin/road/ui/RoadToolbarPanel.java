package com.plot.plugin.road.ui;
import com.plot.plugin.ui.PluginUiColors;

import com.plot.core.command.Command;
import com.plot.core.command.commands.GenerateRoadCommand;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 道路插件顶部工具栏与落地进度控制。
 */
public final class RoadToolbarPanel {
    private final RoadUiContext ctx;

    public RoadToolbarPanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        renderToolbar();
        renderActivePlacementControls();
    }

    private void renderToolbar() {
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.undo_edit_section"));
        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;

        boolean undoDisabled = !ctx.networkManager().canUndo();
        if (undoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.undo_network"), buttonWidth, 0)) {
            ctx.networkManager().undo();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.undo_network"));
        }
        if (undoDisabled) {
            ImGui.endDisabled();
        }
        ImGui.sameLine();
        boolean redoDisabled = !ctx.networkManager().canRedo();
        if (redoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.redo_network"), buttonWidth, 0)) {
            ctx.networkManager().redo();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.redo_network"));
        }
        if (redoDisabled) {
            ImGui.endDisabled();
        }

        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.undo_scope_hint"));

        if (canUndoWorldPlacement()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.undo_world_section"));
            if (ImGui.button(PlotI18n.tr("plugin.road.undo_world"), ImGui.getContentRegionAvailX(), 0)) {
                ctx.host().commands().undo();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PlotI18n.tr("hint.plot.road.undo_world"));
            }
        }

        if (!ctx.status().isEmpty()) {
            RoadStatusUi.render(ctx.status().getStatus());
        }
        ImGui.separator();
    }

    private boolean canUndoWorldPlacement() {
        Command command = ctx.host().commands().peekUndoCommand();
        return command instanceof GenerateRoadCommand;
    }

    private void renderActivePlacementControls() {
        com.plot.api.world.IBlockPlacementService scheduler = ctx.host().placement();
        if (!scheduler.isBusy()) {
            return;
        }

        com.plot.api.world.IBlockPlacementService.ProgressSnapshot progress = scheduler.getProgressSnapshot();
        if (progress != null) {
            ImGui.textColored(PluginUiColors.STATUS_INFO,
                PlotI18n.tr("plugin.road.placement_progress", progress.processed(), progress.total()));
        } else {
            ImGui.textColored(PluginUiColors.STATUS_INFO, PlotI18n.tr("plugin.road.build_in_progress_hint"));
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.cancel_placement"), 0, 0)) {
            scheduler.cancelAll();
        }
        ImGui.separator();
    }
}
