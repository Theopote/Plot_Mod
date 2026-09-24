package com.plot.plugin.building.ui;

import com.plot.plugin.ui.PluginJobProgressUi;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 建筑插件顶部工具栏与落地进度控制。 */
public final class BuildingToolbarPanel {
    private final BuildingUiContext ctx;

    public BuildingToolbarPanel(BuildingUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        renderToolbar();
        renderActivePlacementControls();
    }

    private void renderToolbar() {
        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;

        boolean undoDisabled = !ctx.projectHistory().canUndo();
        if (undoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.undo"), buttonWidth, 0)) {
            ctx.undoProject();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.building.undo"));
        }
        if (undoDisabled) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean redoDisabled = !ctx.projectHistory().canRedo();
        if (redoDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.redo"), buttonWidth, 0)) {
            ctx.redoProject();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.building.redo"));
        }
        if (redoDisabled) {
            ImGui.endDisabled();
        }

        if (!ctx.projectStatus().isEmpty() && !ctx.isDistrictPreviewBusy()) {
            ImGui.textColored(
                ctx.isGhostProjectionBusy() ? PluginUiColors.STATUS_INFO : PluginUiColors.STATUS_OK,
                ctx.projectStatus());
        }
        ImGui.separator();
    }

    private void renderActivePlacementControls() {
        PluginJobProgressUi.renderPlacementProgress(
            ctx.host().placement(),
            "plugin.building.placement_progress",
            "plugin.building.build_in_progress_hint",
            "plugin.building.cancel_placement");
        if (ctx.host().placement().isBusy()) {
            ImGui.separator();
        }
    }
}
