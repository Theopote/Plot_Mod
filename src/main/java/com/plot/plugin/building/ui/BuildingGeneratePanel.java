package com.plot.plugin.building.ui;

import com.plot.plugin.building.generation.DistrictBuildReport;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.generation.DistrictOverlapAnalyzer;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;

/** 建筑生成 Tab：范围 → 预览 → 结果 → 建造。 */
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
            renderScopeSection();
            return;
        }

        renderScopeSection();
        ImGui.separator();
        renderPreviewSection(targets);
        ImGui.separator();
        renderResultSection(targets);
        ImGui.separator();
        renderBuildSection(targets);

        if (ctx.lastDistrictBuildReport() != null) {
            renderDistrictBuildReport();
        }
    }

    private void renderScopeSection() {
        ImGui.text(PlotI18n.tr("plugin.building.generate.scope_section"));
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

    private void renderPreviewSection(List<BuildingFootprint> targets) {
        ImGui.text(PlotI18n.tr("plugin.building.generate.preview_section"));
        BuildingPreviewIdentity.Validity validity = ctx.previewValidity(targets);
        boolean previewBusy = ctx.isDistrictPreviewBusy();
        if (previewBusy) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.generate_preview"), ImGui.getContentRegionAvailX(), 0)) {
            if (targets.size() == 1) {
                ctx.calculatePreview(targets.getFirst());
            } else {
                ctx.calculateDistrictPreview(targets, true);
            }
        }
        if (previewBusy) {
            ImGui.endDisabled();
        }

        if (previewBusy) {
            ImGui.textColored(PluginUiColors.STATUS_INFO, PlotI18n.tr("plugin.building.generate.preview_running"));
            return;
        }

        switch (validity) {
            case NONE -> ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.building.generate.preview_idle"));
            case STALE -> ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.building.generate.preview_stale"));
            case VALID -> ImGui.textColored(
                PluginUiColors.STATUS_OK,
                PlotI18n.tr("plugin.building.generate.preview_ready"));
        }

        if (!ctx.hasPreviewResult()) {
            return;
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.clear_preview"), 0, 0)) {
            ctx.clearPreview();
        }
    }

    private void renderResultSection(List<BuildingFootprint> targets) {
        ImGui.text(PlotI18n.tr("plugin.building.generate.result_section"));
        if (!ctx.hasPreviewResult()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.generate.result_idle"));
            return;
        }
        if (ctx.previewValidity(targets) == BuildingPreviewIdentity.Validity.STALE) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.building.generate.result_stale"));
        }

        if (isDistrictPreview()) {
            renderDistrictResultSummary(ctx.lastDistrictResult());
        } else if (ctx.lastGenerationResult() != null) {
            renderSingleResultSummary();
        }

        int issueCount = countPreviewIssues();
        if (issueCount > 0) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.building.generate.issue_count", issueCount));
            if (ImGui.button(PlotI18n.tr("plugin.building.generate.show_issues"), 0, 0)) {
                ImGui.openPopup("##building_preview_issues");
            }
            renderIssuesPopup();
        }

        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.building.generate.detailed_stats"))) {
            if (isDistrictPreview()) {
                renderDistrictDetailedStats(ctx.lastDistrictResult());
            } else if (ctx.lastGenerationResult() != null) {
                renderSingleDetailedStats();
            }
        }
    }

    private void renderBuildSection(List<BuildingFootprint> targets) {
        ImGui.text(PlotI18n.tr("plugin.building.generate.minecraft_section"));
        BuildingPreviewIdentity.Validity validity = ctx.previewValidity(targets);
        com.plot.api.world.PlacementReadiness readiness =
            ctx.host().projection().checkWorldModificationReadiness();
        if (!readiness.ready()) {
            ImGui.textColored(PluginUiColors.ERROR_SOFT, readiness.message());
        } else if (validity == BuildingPreviewIdentity.Validity.VALID) {
            ImGui.textColored(PluginUiColors.STATUS_OK, PlotI18n.tr("plugin.building.generate.build_ready"));
        } else if (validity == BuildingPreviewIdentity.Validity.STALE) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.building.generate.build_stale"));
        } else {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.generate.build_needs_preview"));
        }

        boolean buildDisabled = validity != BuildingPreviewIdentity.Validity.VALID
            || !readiness.ready()
            || ctx.host().placement().isBusy()
            || ctx.isDistrictPreviewBusy();
        if (buildDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(
                PlotI18n.tr("plugin.building.generate.build_this_preview", targets.size()),
                ImGui.getContentRegionAvailX(),
                0)) {
            ctx.requestBuildFromCurrentPreview(targets);
        }
        if (buildDisabled) {
            ImGui.endDisabled();
        }
    }

    private boolean isDistrictPreview() {
        return ctx.lastDistrictResult() != null && ctx.lastDistrictResult().buildingsAttempted() > 1;
    }

    private int countPreviewIssues() {
        int issues = 0;
        if (isDistrictPreview()) {
            DistrictGenerationResult district = ctx.lastDistrictResult();
            issues += district.buildingsSkipped();
            if (district.hasBuildingOverlap()) {
                issues++;
            }
            if (district.waterSiteCount() > 0) {
                issues++;
            }
            if (district.steepSiteCount() > 0) {
                issues++;
            }
            if (district.structureConflictBuildingCount() > 0) {
                issues++;
            }
            issues += district.skippedOutcomes().size();
        } else if (ctx.lastGenerationResult() != null) {
            issues += ctx.lastGenerationResult().warnings.size();
        }
        return issues;
    }

    private void renderDistrictResultSummary(DistrictGenerationResult district) {
        ImGui.text(PlotI18n.tr(
            "plugin.building.generate.result_buildings",
            district.buildingsGenerated(),
            district.buildingsAttempted()));
        ImGui.text(PlotI18n.tr("plugin.building.estimated_build_blocks", district.totalBlocks()));
        ImGui.text(PlotI18n.tr("plugin.building.cut_volume_result", district.totalCutVolume()));
        ImGui.text(PlotI18n.tr("plugin.building.fill_volume_result", district.totalFillVolume()));
    }

    private void renderSingleResultSummary() {
        ImGui.text(PlotI18n.tr(
            "plugin.building.estimated_build_blocks",
            ctx.lastGenerationResult().blockCount));
        ImGui.text(PlotI18n.tr("plugin.building.cut_volume_result", ctx.lastGenerationResult().cutVolume));
        ImGui.text(PlotI18n.tr("plugin.building.fill_volume_result", ctx.lastGenerationResult().fillVolume));
    }

    private void renderDistrictDetailedStats(DistrictGenerationResult district) {
        ImGui.text(PlotI18n.tr(
            "plugin.building.district_area_result",
            String.format("%.1f", district.totalArea())));
        ImGui.text(PlotI18n.tr(
            "plugin.building.district_volume_result",
            String.format("%.0f", district.totalVolume())));
        if (district.hasSiteConditionSummary()) {
            ImGui.text(PlotI18n.tr("plugin.building.district_site_conditions"));
            if (district.waterSiteCount() > 0) {
                ImGui.text(PlotI18n.tr("plugin.building.district_site_water", district.waterSiteCount()));
            }
            if (district.partialWaterSiteCount() > 0) {
                ImGui.text(PlotI18n.tr("plugin.building.district_site_partial_water", district.partialWaterSiteCount()));
            }
            if (district.steepSiteCount() > 0) {
                ImGui.text(PlotI18n.tr("plugin.building.district_site_steep", district.steepSiteCount()));
            }
            if (district.structureConflictBuildingCount() > 0) {
                ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                    "plugin.building.district_site_structure_conflict",
                    district.structureConflictBuildingCount()));
            }
            if (district.heavyEarthworkSiteCount() > 0) {
                ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                    "plugin.building.district_site_heavy_earthwork",
                    district.heavyEarthworkSiteCount()));
            }
        }
        for (DistrictGenerationResult.BuildingOutcome skipped : district.skippedOutcomes()) {
            String reason = skipped.skipReason() != null
                ? PlotI18n.tr(skipped.skipReason().i18nKey())
                : "";
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                "plugin.building.district_skip_item",
                skipped.buildingName(),
                reason));
        }
    }

    private void renderSingleDetailedStats() {
        if (ctx.lastGenerationResult().sitePreview != null) {
            var site = ctx.lastGenerationResult().sitePreview;
            ImGui.text(PlotI18n.tr(
                "plugin.building.site_foundation_elevation",
                site.foundationElevation()));
            ImGui.text(PlotI18n.tr(
                "plugin.building.site_terrain_range",
                site.minGroundElevation(),
                site.maxGroundElevation()));
        }
        ImGui.text(PlotI18n.tr("plugin.building.roof_type_result",
            PlotI18n.tr("plugin.building.roof_" + ctx.lastGenerationResult().effectiveRoofType.name().toLowerCase())));
        for (String warningKey : ctx.lastGenerationResult().warnings) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(warningKey));
        }
    }

    private void renderIssuesPopup() {
        if (!ImGui.beginPopup("##building_preview_issues")) {
            return;
        }
        if (isDistrictPreview()) {
            DistrictGenerationResult district = ctx.lastDistrictResult();
            if (district.buildingsSkipped() > 0) {
                ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                    "plugin.building.district_skipped_result",
                    district.buildingsSkipped()));
            }
            if (district.hasBuildingOverlap()) {
                renderDistrictOverlapNotice(
                    district.overlappingBuildingCount(),
                    district.conflictingBlockCount(),
                    district.overlappingBuildingPairs());
            }
            for (DistrictGenerationResult.BuildingOutcome skipped : district.skippedOutcomes()) {
                String reason = skipped.skipReason() != null
                    ? PlotI18n.tr(skipped.skipReason().i18nKey())
                    : "";
                ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                    "plugin.building.district_skip_item",
                    skipped.buildingName(),
                    reason));
            }
        } else if (ctx.lastGenerationResult() != null) {
            for (String warningKey : ctx.lastGenerationResult().warnings) {
                ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(warningKey));
            }
        }
        ImGui.endPopup();
    }

    public void renderDistrictBuildReport() {
        DistrictBuildReport report = ctx.lastDistrictBuildReport();
        if (report == null || !report.isDistrict()) {
            return;
        }
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.building.district_build_report"));
        ImGui.text(PlotI18n.tr(
            "plugin.building.district_buildings_result",
            report.buildingsGenerated(),
            report.buildingsAttempted()));
        if (ImGui.button(PlotI18n.tr("plugin.building.clear_build_report"), 0, 0)) {
            ctx.state().setLastDistrictBuildReport(null);
        }
    }

    public void renderBuildConfirmPopup() {
        if (ctx.buildConfirmPending()) {
            ImGui.openPopup("##building_build_confirm");
            ctx.setBuildConfirmPending(false);
        }

        if (ImGui.beginPopupModal("##building_build_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            int blockCount = ctx.lastGenerationResult() != null ? ctx.lastGenerationResult().placementRecords.size() : 0;
            if (ctx.lastDistrictResult() != null && ctx.lastDistrictResult().buildingsAttempted() > 1) {
                ImGui.text(PlotI18n.tr(
                    "plugin.building.build_confirm_district",
                    ctx.lastDistrictResult().buildingsGenerated(),
                    blockCount));
            } else {
                ImGui.text(String.format(PlotI18n.tr("plugin.building.build_confirm"), blockCount));
            }

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

    private static void renderDistrictOverlapNotice(
            int overlappingBuildingCount,
            int conflictingBlockCount,
            List<DistrictOverlapAnalyzer.OverlapPair> pairs) {
        int buildings = overlappingBuildingCount;
        if (buildings <= 0 && pairs != null && !pairs.isEmpty()) {
            buildings = DistrictOverlapAnalyzer.countDistinctBuildings(pairs);
        }
        if (buildings > 0) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                "plugin.building.district_overlap_buildings",
                buildings));
        }
        if (conflictingBlockCount > 0) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                "plugin.building.district_overlap_voxels",
                conflictingBlockCount));
        }
    }
}
