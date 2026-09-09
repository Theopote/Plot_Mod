package com.plot.plugin.powerline.ui;

import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 电力线路插件顶部工具栏。 */
public final class PowerLineToolbarPanel {
    private final PowerLineUiContext ctx;

    public PowerLineToolbarPanel(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
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
        if (redoDisabled) {
            ImGui.endDisabled();
        }

        if (!ctx.projectStatus().isEmpty()) {
            ImGui.textColored(ctx.projectStatusSeverity().color(), ctx.projectStatus());
        }
        ImGui.separator();
    }
}
