package com.plot.plugin.building.ui;

import com.plot.plugin.building.generation.DistrictBuildReport;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.generation.DistrictOverlapAnalyzer;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;
import java.util.List;

/** 建筑生成 Tab：预览、片区统计与落地确认。 */
public final class BuildingGeneratePanel {
    private final BuildingUiContext ctx;

    public BuildingGeneratePanel(BuildingUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        ctx.selection().retainExisting(ctx.project());
        BuildingFootprint building = ctx.selection().primary(ctx.project());
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        boolean hasBuilding = building != null;

        if (!hasBuilding) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.select_building_hint"));
            BuildingUiWidgets.renderBuildingSelector(ctx);
            if (ctx.lastDistrictBuildReport() != null) {
                renderDistrictBuildReport();
            }
            return;
        }

        BuildingUiWidgets.renderSelectionSummary(ctx);
        BuildingUiWidgets.renderBuildingSelector(ctx);
        ImGui.spacing();
        BuildingEditPanel.renderEarthworkPadElevationHint(building);

        int selectedCount = ctx.selection().size();
        com.plot.api.world.PlacementReadiness buildReadiness =
            ctx.host().projection().checkWorldModificationReadiness();

        if (selectedCount > 1) {
            renderDistrictGenerateActions(selectedCount, half, buildReadiness);
        } else {
            if (ImGui.button(PlotI18n.tr("plugin.building.calc_preview"), half, 0)) {
                ctx.calculatePreview(building);
            }
            ImGui.sameLine();
            boolean hasPreview = ctx.lastGenerationResult() != null;
            if (!hasPreview) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.building.clear_preview"), half, 0)) {
                ctx.clearPreview();
            }
            if (!hasPreview) {
                ImGui.endDisabled();
            }

            if (ImGui.button(PlotI18n.tr("plugin.building.build_direct"), ImGui.getContentRegionAvailX(), 0)) {
                if (ctx.calculatePreview(building)) {
                    ctx.setBuildConfirmPending(true);
                }
            }
        }

        if (!buildReadiness.ready()) {
            ImGui.textColored(PluginUiColors.ERROR_SOFT, buildReadiness.message());
        }

        if (ctx.lastDistrictResult() != null && ctx.lastDistrictResult().buildingsAttempted() > 1) {
            renderDistrictPreviewStats(half, buildReadiness);
        } else if (ctx.lastGenerationResult() != null) {
            renderSinglePreviewStats(half, buildReadiness);
        }

        if (ctx.lastDistrictBuildReport() != null) {
            renderDistrictBuildReport();
        }
    }
    private void renderDistrictGenerateActions(
            int selectedCount,
            float half,
            com.plot.api.world.PlacementReadiness buildReadiness) {
        if (ImGui.button(
                PlotI18n.tr("plugin.building.preview_selected", selectedCount),
                half,
                0)) {
            ctx.calculateDistrictPreview(ctx.selection().resolve(ctx.project()), true);
        }
        ImGui.sameLine();
        boolean hasPreview = ctx.lastGenerationResult() != null;
        if (!hasPreview) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.clear_preview"), half, 0)) {
            ctx.clearPreview();
        }
        if (!hasPreview) {
            ImGui.endDisabled();
        }

        if (ImGui.button(
                PlotI18n.tr("plugin.building.preview_all", ctx.project().getBuildingCount()),
                ImGui.getContentRegionAvailX(),
                0)) {
            ctx.calculateDistrictPreview(new ArrayList<>(ctx.project().getBuildings().values()), true);
        }

        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.building.district_generate_section"));

        boolean generateDisabled = !buildReadiness.ready() || ctx.host().placement().isBusy();
        if (generateDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(
                PlotI18n.tr("plugin.building.generate_selected", selectedCount),
                half,
                0)) {
            requestDistrictGenerate(ctx.selection().resolve(ctx.project()));
        }
        ImGui.sameLine();
        if (ImGui.button(
                PlotI18n.tr("plugin.building.generate_all", ctx.project().getBuildingCount()),
                half,
                0)) {
            requestDistrictGenerate(new ArrayList<>(ctx.project().getBuildings().values()));
        }
        if (generateDisabled) {
            ImGui.endDisabled();
        }
    }
    private void requestDistrictGenerate(List<BuildingFootprint> buildings) {
        if (ctx.calculateDistrictPreview(buildings, true)) {
            ctx.setBuildConfirmPending(true);
        }
    }
    private void renderDistrictPreviewStats(
            float half,
            com.plot.api.world.PlacementReadiness buildReadiness) {
        DistrictGenerationResult district = ctx.lastDistrictResult();
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.building.district_preview_results"));
        ImGui.text(PlotI18n.tr(
            "plugin.building.district_buildings_result",
            district.buildingsGenerated(),
            district.buildingsAttempted()));
        if (district.buildingsSkipped() > 0) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                "plugin.building.district_skipped_result",
                district.buildingsSkipped()));
        }
        ImGui.text(PlotI18n.tr(
            "plugin.building.district_area_result",
            String.format("%.1f", district.totalArea())));
        ImGui.text(PlotI18n.tr(
            "plugin.building.district_volume_result",
            String.format("%.0f", district.totalVolume())));
        ImGui.text(PlotI18n.tr("plugin.building.cut_volume_result", district.totalCutVolume()));
        ImGui.text(PlotI18n.tr("plugin.building.fill_volume_result", district.totalFillVolume()));
        ImGui.text(PlotI18n.tr("plugin.building.block_count_result", district.totalBlocks()));

        if (district.buildingsSkipped() > 0) {
            renderDistrictFailSoftSummary(
                district.buildingsGenerated(),
                district.buildingsSkipped());
        }

        if (district.hasBuildingOverlap()) {
            renderDistrictOverlapNotice(
                district.overlappingBuildingCount(),
                district.conflictingBlockCount(),
                district.overlappingBuildingPairs());
        }

        if (district.hasSiteConditionSummary()) {
            ImGui.text(PlotI18n.tr("plugin.building.district_site_conditions"));
            if (district.waterSiteCount() > 0) {
                ImGui.text(PlotI18n.tr(
                    "plugin.building.district_site_water",
                    district.waterSiteCount()));
            }
            if (district.partialWaterSiteCount() > 0) {
                ImGui.text(PlotI18n.tr(
                    "plugin.building.district_site_partial_water",
                    district.partialWaterSiteCount()));
            }
            if (district.steepSiteCount() > 0) {
                ImGui.text(PlotI18n.tr(
                    "plugin.building.district_site_steep",
                    district.steepSiteCount()));
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

        for (String warningKey : district.warnings()) {
            if ("plugin.building.warn.district_overlap".equals(warningKey)
                    || "plugin.building.warn.district_partial".equals(warningKey)) {
                continue;
            }
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(warningKey));
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

        boolean hasPlacements = district.hasPlacements();
        if (!hasPlacements) {
            ImGui.textColored(PluginUiColors.WARNING_LIGHT, PlotI18n.tr("plugin.building.generate_empty_result"));
        }

        if (!hasPlacements) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.projection_ref"), half, 0)) {
            ctx.projectPreview();
        }
        if (!hasPlacements) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean buildDisabled = !hasPlacements
            || !buildReadiness.ready()
            || ctx.host().placement().isBusy();
        if (buildDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.generate_from_preview"), half, 0)) {
            ctx.setBuildConfirmPending(true);
        }
        if (buildDisabled) {
            ImGui.endDisabled();
        }
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
        if (report.buildingsSkipped() > 0) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                "plugin.building.district_skipped_result",
                report.buildingsSkipped()));
        }
        ImGui.text(PlotI18n.tr(
            "plugin.building.district_area_result",
            String.format("%.1f", report.totalArea())));
        ImGui.text(PlotI18n.tr(
            "plugin.building.district_volume_result",
            String.format("%.0f", report.totalVolume())));
        ImGui.text(PlotI18n.tr(
            "plugin.building.district_placed_result",
            report.placedBlocks(),
            report.plannedBlocks()));
        if (report.buildingsSkipped() > 0) {
            renderDistrictFailSoftSummary(
                report.buildingsGenerated(),
                report.buildingsSkipped());
        }
        if (report.overlappingBuildingCount() > 0 || report.conflictingBlockCount() > 0) {
            renderDistrictOverlapNotice(
                report.overlappingBuildingCount(),
                report.conflictingBlockCount(),
                List.of());
        }
        if (report.hasSiteConditionSummary()) {
            ImGui.text(PlotI18n.tr("plugin.building.district_site_conditions"));
            if (report.waterSiteCount() > 0) {
                ImGui.text(PlotI18n.tr(
                    "plugin.building.district_site_water",
                    report.waterSiteCount()));
            }
            if (report.partialWaterSiteCount() > 0) {
                ImGui.text(PlotI18n.tr(
                    "plugin.building.district_site_partial_water",
                    report.partialWaterSiteCount()));
            }
            if (report.steepSiteCount() > 0) {
                ImGui.text(PlotI18n.tr(
                    "plugin.building.district_site_steep",
                    report.steepSiteCount()));
            }
            if (report.structureConflictBuildingCount() > 0) {
                ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                    "plugin.building.district_site_structure_conflict",
                    report.structureConflictBuildingCount()));
            }
            if (report.heavyEarthworkSiteCount() > 0) {
                ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                    "plugin.building.district_site_heavy_earthwork",
                    report.heavyEarthworkSiteCount()));
            }
        }
        if (report.failedBlocks() > 0) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                "plugin.building.district_failed_blocks",
                report.failedBlocks()));
        }
        if (report.cancelled()) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.building.district_build_cancelled"));
        }

        for (DistrictBuildReport.SkipItem skipped : report.skipped()) {
            String reason = skipped.reasonKey().isBlank() ? "" : PlotI18n.tr(skipped.reasonKey());
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                "plugin.building.district_skip_item",
                skipped.buildingName(),
                reason));
        }

        if (ImGui.button(PlotI18n.tr("plugin.building.clear_build_report"), 0, 0)) {
            ctx.state().setLastDistrictBuildReport(null);
        }
    }
    private void renderSinglePreviewStats(
            float half,
            com.plot.api.world.PlacementReadiness buildReadiness) {
        ImGui.separator();
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.preview_projection_hint"));
        ImGui.text(PlotI18n.tr("plugin.building.calc_results"));
        if (ctx.lastGenerationResult().sitePreview != null) {
            var site = ctx.lastGenerationResult().sitePreview;
            ImGui.text(PlotI18n.tr(
                "plugin.building.site_foundation_elevation",
                site.foundationElevation()));
            ImGui.text(PlotI18n.tr(
                "plugin.building.site_foundation_source",
                PlotI18n.tr("plugin.building.site_source_" + site.source().name().toLowerCase())));
            ImGui.text(PlotI18n.tr(
                "plugin.building.site_terrain_range",
                site.minGroundElevation(),
                site.maxGroundElevation()));
            if (site.waterCoverageRatio() > 0) {
                ImGui.text(PlotI18n.tr(
                    "plugin.building.site_water_coverage",
                    String.format("%.0f", site.waterCoverageRatio() * 100)));
            }
            ImGui.text(PlotI18n.tr(
                "plugin.building.site_cut_fill",
                site.estimatedCut(),
                site.estimatedFill()));
        }
        ImGui.text(PlotI18n.tr("plugin.building.cut_volume_result", ctx.lastGenerationResult().cutVolume));
        ImGui.text(PlotI18n.tr("plugin.building.fill_volume_result", ctx.lastGenerationResult().fillVolume));
        ImGui.text(PlotI18n.tr("plugin.building.block_count_result", ctx.lastGenerationResult().blockCount));
        ImGui.text(PlotI18n.tr("plugin.building.roof_type_result",
            PlotI18n.tr("plugin.building.roof_" + ctx.lastGenerationResult().effectiveRoofType.name().toLowerCase())));

        for (String warningKey : ctx.lastGenerationResult().warnings) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(warningKey));
        }

        boolean hasPlacements = !ctx.lastGenerationResult().placementRecords.isEmpty();
        if (!hasPlacements) {
            ImGui.textColored(PluginUiColors.WARNING_LIGHT, PlotI18n.tr("plugin.building.generate_empty_result"));
        }

        if (!hasPlacements) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.projection_ref"), half, 0)) {
            ctx.projectPreview();
        }
        if (!hasPlacements) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        boolean buildDisabled = !hasPlacements
            || !buildReadiness.ready()
            || ctx.host().placement().isBusy();
        if (buildDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.build"), half, 0)) {
            ctx.setBuildConfirmPending(true);
        }
        if (buildDisabled) {
            ImGui.endDisabled();
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

    private static void renderDistrictFailSoftSummary(int generated, int skipped) {
        ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
            "plugin.building.district_fail_soft_summary",
            generated,
            skipped));
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
            "plugin.building.district_fail_soft_policy"));
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
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
            "plugin.building.district_overlap_later_wins"));

        if (pairs == null || pairs.isEmpty()) {
            return;
        }
        int shown = 0;
        for (DistrictOverlapAnalyzer.OverlapPair pair : pairs) {
            if (shown >= 5) {
                ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                    "plugin.building.district_overlap_more",
                    pairs.size() - shown));
                break;
            }
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                "plugin.building.district_overlap_pair",
                pair.buildingNameA(),
                pair.buildingNameB()));
            shown++;
        }
    }
}
