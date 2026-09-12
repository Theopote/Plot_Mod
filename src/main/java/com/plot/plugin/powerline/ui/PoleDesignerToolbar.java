package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignCatalog;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import java.util.List;
import java.util.function.Consumer;

/** 杆塔设计器工具栏：撤销/重做与预设加载。 */
final class PoleDesignerToolbar {
    private int selectedPresetIndex;
    private String pendingPresetId = "";
    private boolean presetConfirmPending;

    void render(boolean canUndo, boolean canRedo, Runnable onUndo, Runnable onRedo) {
        renderDraftHistoryControls(canUndo, canRedo, onUndo, onRedo);
        ImGui.separator();
        renderPresetSelector();
        ImGui.separator();
    }

    void renderPresetConfirmPopup(Consumer<PoleDesign> onPresetApplied) {
        if (!PowerLineUiWidgets.beginDeferredPopupModal(
                "##pole_preset_confirm",
                presetConfirmPending,
                () -> presetConfirmPending = false)) {
            return;
        }
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.design.preset_confirm"));
        if (ImGui.button(PlotI18n.tr("button.plot.confirm"), 120, 0)) {
            PoleDesign preset = PoleDesignCatalog.findBuiltin(pendingPresetId);
            if (preset != null) {
                onPresetApplied.accept(preset.copy());
            }
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
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

    private void renderPresetSelector() {
        List<PoleDesign> presets = PoleDesignCatalog.defaultDesigns();
        String[] labels = presets.stream()
            .map(d -> PlotI18n.tr("plugin.powerline.design.preset_label", d.getName()))
            .toArray(String[]::new);
        String[] ids = presets.stream().map(PoleDesign::getId).toArray(String[]::new);
        selectedPresetIndex = Math.min(Math.max(0, selectedPresetIndex), labels.length - 1);

        if (ImGui.beginCombo(
                PowerLineUiWidgets.stableLabel("plugin.powerline.design.load_preset", "load_preset"),
                labels[selectedPresetIndex])) {
            for (int i = 0; i < labels.length; i++) {
                if (ImGui.selectable(
                        PowerLineUiWidgets.stableSelectableLabel(labels[i], ids[i]),
                        selectedPresetIndex == i)) {
                    selectedPresetIndex = i;
                    pendingPresetId = ids[i];
                    presetConfirmPending = true;
                }
            }
            ImGui.endCombo();
        }
    }
}
