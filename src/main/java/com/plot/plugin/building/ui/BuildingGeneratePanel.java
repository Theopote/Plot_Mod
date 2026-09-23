package com.plot.plugin.building.ui;

import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;

/** 建筑生成 Tab：目标 → 体量预览 → 摘要 → 建造。 */
public final class BuildingGeneratePanel {
    private final BuildingUiContext ctx;

    public BuildingGeneratePanel(BuildingUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        ctx.selection().retainExisting(ctx.project());
        if (ctx.project().getBuildingCount() == 0) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.no_buildings"));
            return;
        }

        List<BuildingFootprint> targets = ctx.resolveGenerateTargets();
        if (targets.isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.generate_select_hint"));
            renderEmptyScopeHint();
            return;
        }

        BuildingGenerateScopeBar.render(ctx, targets);
        ImGui.spacing();
        BuildingMassingPreview.render(ctx, targets);
        ImGui.spacing();
        BuildingPreviewSummary.render(ctx, targets, () -> ImGui.openPopup("##building_preview_issues"));
        renderIssuesPopup(targets);
        BuildingBuildAction.render(ctx, targets);
    }

    private void renderEmptyScopeHint() {
        int selectedCount = ctx.selection().size();
        int allCount = ctx.project().getBuildingCount();
        boolean useAll = ctx.generateScopeAll();
        if (ImGui.radioButton(
                PlotI18n.tr("plugin.building.generate.scope_selected", selectedCount),
                !useAll)) {
            ctx.setGenerateScopeAll(false);
        }
        if (ImGui.radioButton(
                PlotI18n.tr("plugin.building.generate.scope_all", allCount),
                useAll)) {
            ctx.setGenerateScopeAll(true);
        }
    }

    private void renderIssuesPopup(List<BuildingFootprint> targets) {
        if (!ImGui.beginPopup("##building_preview_issues")) {
            return;
        }
        List<BuildingGenerationIssues.Issue> issues = ctx.collectPreviewIssues(targets);
        if (issues.isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.generate.no_issues"));
            ImGui.endPopup();
            return;
        }
        for (int i = 0; i < issues.size(); i++) {
            renderIssueRow(issues.get(i));
            if (i < issues.size() - 1) {
                ImGui.separator();
            }
        }
        ImGui.endPopup();
    }

    private void renderIssueRow(BuildingGenerationIssues.Issue issue) {
        ImGui.pushID(issue.primaryBuildingId() + issue.secondaryBuildingId() + issue.messageKey());
        int color = issueColor(issue.severity());
        ImGui.textColored(color, formatIssueSummary(issue));
        if (issue.kind() == BuildingGenerationIssues.Kind.OVERLAP) {
            if (ImGui.button(PlotI18n.tr("plugin.building.issue.select_pair"), 0, 0)) {
                ctx.selectBuildingPair(issue.primaryBuildingId(), issue.secondaryBuildingId());
                ImGui.closeCurrentPopup();
            }
        } else {
            if (ImGui.button(PlotI18n.tr("plugin.building.locate"), 0, 0)) {
                ctx.locateBuildingById(issue.primaryBuildingId());
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.building.issue.select"), 0, 0)) {
                ctx.selectBuildingById(issue.primaryBuildingId(), false);
                ImGui.closeCurrentPopup();
            }
        }
        ImGui.popID();
    }

    private static int issueColor(BuildingGenerationIssues.Severity severity) {
        return switch (severity) {
            case ERROR -> PluginUiColors.ERROR_SOFT;
            case WARNING -> PluginUiColors.WARNING;
            case INFO -> PluginUiColors.STATUS_INFO;
        };
    }

    private static String formatIssueSummary(BuildingGenerationIssues.Issue issue) {
        return switch (issue.kind()) {
            case SKIPPED -> PlotI18n.tr(
                "plugin.building.issue.skipped",
                issue.primaryBuildingName(),
                PlotI18n.tr(issue.messageKey()));
            case OVERLAP -> PlotI18n.tr(
                "plugin.building.issue.overlap_info",
                issue.primaryBuildingName(),
                issue.secondaryBuildingName());
            case TERRAIN_FIT -> PlotI18n.tr(
                "plugin.building.issue.terrain_fit_item",
                issue.primaryBuildingName());
            case BUILDING_WARNING -> PlotI18n.tr(
                "plugin.building.issue.warning",
                issue.primaryBuildingName(),
                PlotI18n.tr(issue.messageKey()));
        };
    }

    public void renderBuildConfirmPopup() {
        if (ctx.buildConfirmPending()) {
            ImGui.openPopup("##building_build_confirm");
            ctx.setBuildConfirmPending(false);
        }

        if (ImGui.beginPopupModal("##building_build_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            int blockCount = ctx.lastGenerationResult() != null ? ctx.lastGenerationResult().blockCount : 0;
            int buildingCount = ctx.lastDistrictResult() != null && ctx.lastDistrictResult().buildingsAttempted() > 1
                ? ctx.lastDistrictResult().buildingsGenerated()
                : 1;
            ImGui.text(PlotI18n.tr("plugin.building.build_confirm_buildings", buildingCount));
            ImGui.text(PlotI18n.tr("plugin.building.build_confirm_blocks", blockCount));

            com.plot.api.world.PlacementReadiness readiness =
                ctx.host().projection().checkWorldModificationReadiness();
            if (!readiness.ready()) {
                ImGui.textColored(PluginUiColors.ERROR, readiness.message());
            }

            ImGui.separator();
            boolean canBuild = readiness.ready() && !ctx.host().placement().isBusy();
            if (!canBuild) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.building.build"), 120, 0)) {
                ctx.buildInWorld();
                ImGui.closeCurrentPopup();
            }
            if (!canBuild) {
                ImGui.endDisabled();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }
}
