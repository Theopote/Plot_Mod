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
            ctx.setProject(ctx.projectHistory().undo(ctx.project()));
            ctx.syncSelectedBuildingAfterHistory();
            ctx.setBuildingNameEditingId("");
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
        if (ImGui.button(PlotI18n.tr("plugin.building.redo"), buttonWidth, 0)) {
            ctx.setProject(ctx.projectHistory().redo(ctx.project()));
            ctx.syncSelectedBuildingAfterHistory();
            ctx.setBuildingNameEditingId("");
            ctx.invalidatePreview();
        }
        if (redoDisabled) {
            ImGui.endDisabled();
        }

        if (!ctx.projectStatus().isEmpty()) {
            ImGui.textColored(
                ctx.isDistrictPreviewBusy() || ctx.isGhostProjectionBusy()
                    ? PluginUiColors.STATUS_INFO
                    : PluginUiColors.STATUS_OK,
                ctx.projectStatus());
        }
        renderDistrictPreviewControls();
        ImGui.separator();
    }

    private void renderDistrictPreviewControls() {
        if (!ctx.isDistrictPreviewBusy()) {
            return;
        }
        DistrictPreviewJob job = ctx.state().getDistrictPreviewJob();
        String status = job != null
            ? PlotI18n.tr(
                "plugin.building.district_preview_progress",
                job.processedCount(),
                job.totalCount())
            : PlotI18n.tr("plugin.building.generate.preview_running");
        int processed = job != null ? job.processedCount() : 0;
        int total = job != null ? job.totalCount() : 0;
        PluginJobProgressUi.renderJobProgress(
            status,
            processed,
            total,
            ImGui.getContentRegionAvailX(),
            "plugin.building.cancel_district_preview",
            () -> {
                ctx.cancelDistrictPreviewJob();
                ctx.setProjectStatus(PlotI18n.tr("plugin.building.district_preview_cancelled"));
            });
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
