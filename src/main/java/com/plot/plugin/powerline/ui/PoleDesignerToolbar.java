package com.plot.plugin.powerline.ui;

import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 杆塔设计器工具栏：草稿撤销/重做。 */
final class PoleDesignerToolbar {

    void render(boolean canUndo, boolean canRedo, Runnable onUndo, Runnable onRedo) {
        renderDraftHistoryControls(canUndo, canRedo, onUndo, onRedo);
        ImGui.separator();
    }

    private void renderDraftHistoryControls(
            boolean canUndo,
            boolean canRedo,
            Runnable onUndo,
            Runnable onRedo) {
        if (!canUndo) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.undo"), 0, 0)) {
            onUndo.run();
        }
        if (!canUndo) {
            ImGui.endDisabled();
        }
        ImGui.sameLine();
        if (!canRedo) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.redo"), 0, 0)) {
            onRedo.run();
        }
        if (!canRedo) {
            ImGui.endDisabled();
        }
    }
}
