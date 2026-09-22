package com.plot.plugin.building.ui;

import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/** Generate Tab 轻量结果摘要与问题入口。 */
public final class BuildingPreviewSummary {
    private BuildingPreviewSummary() {
    }

    public static void render(
            BuildingUiContext ctx,
            List<BuildingFootprint> targets,
            Runnable openIssuesPopup) {
        if (!ctx.hasPreviewResult()) {
            return;
        }
        if (ctx.previewValidity(targets) == BuildingPreviewIdentity.Validity.STALE) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.building.generate.result_stale"));
        }

        int generated = generatedCount(ctx);
        int attempted = attemptedCount(ctx, targets.size());
        int blocks = blockCount(ctx);

        ImGui.textColored(
            PluginUiColors.STATUS_OK,
            PlotI18n.tr("plugin.building.generate.summary_ready", generated, attempted));
        ImGui.text(PlotI18n.tr("plugin.building.generate.summary_blocks", blocks));

        int issueCount = ctx.collectPreviewIssues(targets).size();
        if (issueCount > 0) {
            ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.building.generate.issue_count", issueCount));
            ImGui.sameLine();
            if (ImGui.smallButton(PlotI18n.tr("plugin.building.generate.show_issues"))) {
                openIssuesPopup.run();
            }
        }
    }

    private static int generatedCount(BuildingUiContext ctx) {
        DistrictGenerationResult district = ctx.lastDistrictResult();
        if (district != null && district.buildingsAttempted() > 0) {
            return district.buildingsGenerated();
        }
        return ctx.hasPreviewResult() ? 1 : 0;
    }

    private static int attemptedCount(BuildingUiContext ctx, int targetCount) {
        DistrictGenerationResult district = ctx.lastDistrictResult();
        if (district != null && district.buildingsAttempted() > 0) {
            return district.buildingsAttempted();
        }
        return targetCount;
    }

    private static int blockCount(BuildingUiContext ctx) {
        DistrictGenerationResult district = ctx.lastDistrictResult();
        if (district != null && district.buildingsAttempted() > 0) {
            return district.totalBlocks();
        }
        if (ctx.lastGenerationResult() != null) {
            return ctx.lastGenerationResult().blockCount;
        }
        return 0;
    }
}
