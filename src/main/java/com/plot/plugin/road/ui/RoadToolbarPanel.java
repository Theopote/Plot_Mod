package com.plot.plugin.road.ui;
import com.plot.plugin.ui.PluginUiColors;

import com.plot.infrastructure.event.block.BlockPlacementScheduler;
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
        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX() * 2) / 3.0f;

        boolean undoDisabled = !ctx.networkManager().canUndo();
        if (undoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.undo"), buttonWidth, 0)) {
            ctx.networkManager().undo();
        }
        if (undoDisabled) {
            ImGui.endDisabled();
        }
        ImGui.sameLine();
        boolean redoDisabled = !ctx.networkManager().canRedo();
        if (redoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.redo"), buttonWidth, 0)) {
            ctx.networkManager().redo();
        }
        if (redoDisabled) {
            ImGui.endDisabled();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.save_network"), buttonWidth, 0)) {
            ctx.persistenceManager().saveCurrentNetwork(ctx.networkManager().getNetwork());
        }

        if (!ctx.status().isEmpty()) {
            ImGui.textColored(PluginUiColors.STATUS_OK, ctx.status().get());
        }
        ImGui.separator();
    }

    private void renderActivePlacementControls() {
        BlockPlacementScheduler scheduler = BlockPlacementScheduler.getInstance();
        if (!scheduler.isBusy()) {
            return;
        }

        BlockPlacementScheduler.ProgressSnapshot progress = scheduler.getProgressSnapshot();
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
