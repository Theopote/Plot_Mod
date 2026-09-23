package com.plot.plugin.building.ui;

import com.plot.plugin.ui.PluginJobProgressUi;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 建筑「检查可生成」分帧任务进度（工具栏与生成页共用）。 */
public final class BuildingDistrictPreviewProgress {
    private BuildingDistrictPreviewProgress() {
    }

    public static void render(BuildingUiContext ctx) {
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
        int displayProcessed = job != null && job.isRunning() && processed < total
            ? processed + 1
            : processed;
        PluginJobProgressUi.renderJobProgress(
            status,
            displayProcessed,
            total,
            ImGui.getContentRegionAvailX(),
            "plugin.building.cancel_district_preview",
            () -> {
                ctx.cancelDistrictPreviewJob();
                ctx.setProjectStatus(PlotI18n.tr("plugin.building.district_preview_cancelled"));
            });
    }
}
