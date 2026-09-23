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
        int processed = job != null ? job.processedCount() : 0;
        int total = job != null ? job.totalCount() : 0;
        String status = job != null
            ? PlotI18n.tr("plugin.building.generate.check_progress", processed, total)
            : PlotI18n.tr("plugin.building.generate.preview_running");
        ImGui.pushID("building_district_preview_progress");
        try {
            PluginJobProgressUi.renderJobProgress(
                status,
                processed,
                total,
                ImGui.getContentRegionAvailX(),
                "plugin.building.cancel_check",
                () -> {
                    ctx.cancelDistrictPreviewJob();
                    ctx.setProjectStatus(PlotI18n.tr("plugin.building.generate.check_cancelled"));
                });
        } finally {
            ImGui.popID();
        }
    }
}
