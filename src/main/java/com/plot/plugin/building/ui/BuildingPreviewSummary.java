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
            ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.building.generate.parameters_changed"));
        }

        List<BuildingGenerationIssues.Issue> issues = ctx.collectPreviewIssues(targets);
        BuildingGenerationIssues.Summary summary = BuildingGenerationIssues.summarize(
            ctx.lastDistrictResult(),
            issues,
            targets.size());

        ImGui.textColored(
            PluginUiColors.STATUS_OK,
            PlotI18n.tr("plugin.building.generate.summary_ready", summary.generated(), summary.attempted()));
        if (summary.skipped() > 0) {
            ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.building.generate.summary_skipped", summary.skipped()));
        }
        if (summary.warningCount() > 0) {
            ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.building.generate.summary_warnings", summary.warningCount()));
        }
        if (summary.infoCount() > 0) {
            ImGui.textColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr("plugin.building.generate.summary_info", summary.infoCount()));
        }
        ImGui.text(PlotI18n.tr("plugin.building.generate.summary_blocks", blockCount(ctx)));

        if (summary.hasIssues()) {
            ImGui.sameLine();
            if (ImGui.smallButton(PlotI18n.tr("plugin.building.generate.show_issues"))) {
                openIssuesPopup.run();
            }
        }
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
