package com.plot.plugin.building.ui;

import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 建筑「检查可生成」分帧任务进度（仅生成 Tab）。 */
public final class BuildingDistrictPreviewProgress {
    private BuildingDistrictPreviewProgress() {
    }

    public static void render(BuildingUiContext ctx) {
        if (!ctx.isDistrictPreviewBusy()) {
            return;
        }
        DistrictPreviewJob job = ctx.state().getDistrictPreviewJob();
        if (job == null) {
            return;
        }
        String status = PlotI18n.tr(
            "plugin.building.generate.check_progress_building",
            job.currentBuildingLabel(),
            job.displayBuildingNumber(),
            job.totalBuildingCount());
        ImGui.pushID("building_district_preview_progress");
        try {
            BuildingCheckProgressUi.render(
                status,
                job.progressCompletedSteps(),
                job.progressTotalSteps(),
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
