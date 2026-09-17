package com.plot.plugin.pattern.ui;

import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 图案插件顶部工具栏。 */
public final class PatternToolbarPanel {
    private final PatternUiContext ctx;

    public PatternToolbarPanel(PatternUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;

        boolean undoDisabled = !ctx.projectHistory().canUndo();
        if (undoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.pattern.undo"), buttonWidth, 0)) {
            ctx.setProject(ctx.projectHistory().undo(ctx.project()));
            ctx.syncSelectedFootprintAfterHistory();
            ctx.footprintRename().cancelActive();
            ctx.state().bumpProjectRevision();
            ctx.clearPreview();
        }
        if (undoDisabled) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean redoDisabled = !ctx.projectHistory().canRedo();
        if (redoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.pattern.redo"), buttonWidth, 0)) {
            ctx.setProject(ctx.projectHistory().redo(ctx.project()));
            ctx.syncSelectedFootprintAfterHistory();
            ctx.footprintRename().cancelActive();
            ctx.state().bumpProjectRevision();
            ctx.clearPreview();
        }
        if (redoDisabled) {
            ImGui.endDisabled();
        }

        if (!ctx.projectStatus().isEmpty()) {
            ImGui.textColored(
                ctx.isPreviewBusy() ? PluginUiColors.STATUS_INFO : PluginUiColors.STATUS_OK,
                ctx.projectStatus());
        }

        renderPreviewJobControls();
        renderActivePlacementControls();
        ImGui.separator();
    }

    private void renderPreviewJobControls() {
        if (!ctx.isPreviewBusy()) {
            return;
        }
        if (ImGui.button(PlotI18n.tr("plugin.pattern.cancel_preview"), 0, 0)) {
            ctx.cancelPreviewJob();
        }
    }

    private void renderActivePlacementControls() {
        com.plot.api.world.IBlockPlacementService scheduler = ctx.host().placement();
        if (!scheduler.isBusy()) {
            return;
        }
        com.plot.api.world.IBlockPlacementService.ProgressSnapshot progress = scheduler.getProgressSnapshot();
        if (progress != null) {
            ImGui.textColored(PluginUiColors.STATUS_INFO,
                PlotI18n.tr("plugin.pattern.placement_progress", progress.processed(), progress.total()));
        } else {
            ImGui.textColored(PluginUiColors.STATUS_INFO, PlotI18n.tr("plugin.pattern.build_in_progress_hint"));
        }
        if (ImGui.button(PlotI18n.tr("plugin.pattern.cancel_placement"), 0, 0)) {
            scheduler.cancelAll();
        }
        ImGui.separator();
    }
}
