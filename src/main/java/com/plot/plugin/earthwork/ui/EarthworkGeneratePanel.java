package com.plot.plugin.earthwork.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.core.material.EarthMaterialClass;
import com.plot.plugin.earthwork.solver.EarthworkAllocationMatrix;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.volume.EarthworkProjectReport;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.earthwork.model.*;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;
import java.util.Map;


/** 土方生成 Tab：预览计算、网格示意与落地确认。 */
public final class EarthworkGeneratePanel {
    private final EarthworkUiContext ctx;

    public EarthworkGeneratePanel(EarthworkUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        GradingRegion region = ctx.project().getRegion(ctx.selectedRegionId());
                boolean hasRegion = region != null;

                if (!hasRegion) {
                    ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.select_region_hint"));
                    EarthworkUiWidgets.renderRegionSelector(ctx);
                    return;
                }

                EarthworkUiWidgets.renderRegionSelector(ctx);
                ImGui.spacing();
                renderPreviewActions(region);

                com.plot.api.world.PlacementReadiness buildReadiness =
                    ctx.host().projection().checkWorldModificationReadiness();
                if (!buildReadiness.ready()) {
                    ImGui.textColored(PluginUiColors.ERROR_SOFT, buildReadiness.message());
                }

                EarthworkGenerationResult preview = ctx.previewManager().getLastGenerationResult();
                if (ctx.config().isShowGrid() && preview != null) {
                    renderGridPreview(region, preview);
                }

                if (preview != null) {
                    ImGui.separator();
                    ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.preview_projection_hint"));
                    ImGui.text(PlotI18n.tr("plugin.earthwork.calc_results"));
                    EarthworkVolumeReport volumes = preview.volumeReport;
                    ImGui.text(PlotI18n.tr("plugin.earthwork.calculation_cell_count", preview.calculationCellCount));
                    renderTerrainSnapshotInfo(preview.existingTerrainSnapshot);
                    EarthworkUiWidgets.renderPlayerCutFill(
                        volumes,
                        preview.resolvedElevation,
                        preview.slopedSurface,
                        preview.resolvedElevationMin,
                        preview.resolvedElevationMax);
                    EarthworkInsightCharts.render(ctx, region, preview);
                    if (ctx.config().getWorkMode().showsLearningMetrics()) {
                        EarthworkLearnWidgets.renderConversionLesson(ctx, volumes);
                    }

                    if (preview.projectReport != null
                        && preview.projectReport.hasZoneBreakdown()
                        && ctx.config().getWorkMode().showsLearningMetrics()) {
                        renderProjectBalanceReport(preview.projectReport);
                    }

                    if (ctx.config().getWorkMode().showsLearningMetrics()
                        && ImGui.button(PlotI18n.tr("plugin.earthwork.export_report"), ImGui.getContentRegionAvailX(), 0)) {
                        ctx.previewManager().exportLastReport(ctx.project(), region);
                    }

                    for (String warningKey : preview.warnings) {
                        ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(warningKey));
                    }

                    renderPreviewBuildButtons(preview);
                }
    }

    public void renderPreviewActions(GradingRegion region) {
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.calc_preview"), half, 0)) {
            ctx.recalculatePreview();
        }
        ImGui.sameLine();
        boolean hasPreview = ctx.previewManager().getLastGenerationResult() != null;
        if (!hasPreview) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.clear_preview"), half, 0)) {
            ctx.clearPreview();
        }
        if (!hasPreview) {
            ImGui.endDisabled();
        }

        if (ImGui.button(PlotI18n.tr("plugin.earthwork.build_direct"), ImGui.getContentRegionAvailX(), 0)) {
            if (ctx.recalculatePreview()) {
                ctx.setBuildConfirmPending(true);
            }
        }
    }

    public void renderPreviewBuildButtons(EarthworkGenerationResult preview) {
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        boolean hasPlacements = preview != null && !preview.placementRecords.isEmpty();
        if (!hasPlacements) {
            ImGui.textColored(PluginUiColors.WARNING_LIGHT, PlotI18n.tr("plugin.earthwork.generate_empty_result"));
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.projection_ref"), half, 0)) {
            ctx.previewManager().projectPreview();
        }
        if (!hasPlacements) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        com.plot.api.world.PlacementReadiness buildReadiness =
            ctx.host().projection().checkWorldModificationReadiness();
        boolean buildDisabled = !hasPlacements
            || !buildReadiness.ready()
            || ctx.host().placement().isBusy();
        if (buildDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.build"), half, 0)) {
            ctx.setBuildConfirmPending(true);
        }
        if (buildDisabled) {
            ImGui.endDisabled();
        }
    }

    private void renderGridPreview(GradingRegion region, EarthworkGenerationResult result) {
        List<Vec2d> points = region.getOuterPoints();
        if (points.size() < 3 || result.gridSamples.isEmpty()) {
            return;
        }

        PolygonRegionUtils.RectBounds bounds = EarthworkGeometryUtils.computeBounds(points);
        float availWidth = ImGui.getContentRegionAvailX();
        float previewHeight = 140.0f;
        float originX = ImGui.getCursorScreenPosX();
        float originY = ImGui.getCursorScreenPosY();

        ImGui.dummy(availWidth, previewHeight);
        ImDrawList drawList = ImGui.getWindowDrawList();

        double spanX = Math.max(bounds.width(), 1.0);
        double spanZ = Math.max(bounds.depth(), 1.0);
        float scale = (float) Math.min(
            (availWidth - 16.0f) / spanX,
            (previewHeight - 16.0f) / spanZ);

        int cutColor = 0x80FF4040;
        int fillColor = 0x804040FF;
        int borderColor = 0xFF606060;

        for (EarthworkGenerationResult.GridSample sample : result.gridSamples) {
            float cellX = originX + 8.0f + (float) ((sample.center.x - bounds.minX()) * scale);
            float cellY = originY + 8.0f + (float) ((sample.center.y - bounds.minZ()) * scale);
            float cellSize = Math.max(3.0f, scale * 0.8f);
            int color = sample.changeType == EarthworkGenerationResult.ChangeType.CUT ? cutColor : fillColor;
            drawList.addRectFilled(cellX, cellY, cellX + cellSize, cellY + cellSize, color);
        }

        float bx1 = originX + 8.0f;
        float by1 = originY + 8.0f;
        float bx2 = bx1 + (float) (spanX * scale);
        float by2 = by1 + (float) (spanZ * scale);
        drawList.addRect(bx1, by1, bx2, by2, borderColor);

        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.earthwork.grid_preview_legend"));
    }

    public void renderBuildConfirmPopup() {
        if (ctx.buildConfirmPending()) {
            ImGui.openPopup("##earthwork_build_confirm");
            ctx.setBuildConfirmPending(false);
        }

        if (ImGui.beginPopupModal("##earthwork_build_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            EarthworkGenerationResult preview = ctx.previewManager().getLastGenerationResult();
            long blockCount = preview != null
                ? preview.volumeReport.totalChangedBlocks()
                : 0L;
            ImGui.text(String.format(PlotI18n.tr("plugin.earthwork.build_confirm"), blockCount));

            TerrainSnapshot.ComparisonResult terrainComparison =
                ctx.previewManager().comparePreviewTerrainWithWorld(EarthworkUiWidgets.getClientWorld());
            boolean terrainStale = terrainComparison != null && terrainComparison.terrainChanged();
            if (terrainStale) {
                ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                    "plugin.earthwork.terrain_changed_since_preview",
                    terrainComparison.changedColumns(),
                    terrainComparison.totalColumns()));
            }

            com.plot.api.world.PlacementReadiness readiness =
                ctx.host().projection().checkWorldModificationReadiness();
            if (!readiness.ready()) {
                ImGui.textColored(PluginUiColors.ERROR, readiness.message());
            }

            ImGui.separator();
            boolean canBuild = readiness.ready() && !ctx.host().placement().isBusy() && !terrainStale;
            if (terrainStale) {
                if (ImGui.button(PlotI18n.tr("plugin.earthwork.recalculate_preview"), 180, 0)) {
                    GradingRegion region = ctx.project().getRegion(ctx.selectedRegionId());
                    if (region != null) {
                        ctx.recalculatePreview();
                    }
                    ImGui.closeCurrentPopup();
                }
                ImGui.sameLine();
            }
            if (!canBuild) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.build"), 120, 0)) {
                ctx.buildManager().buildInWorld(ctx.project(), ctx.selectedRegionId());
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

    private void renderTerrainSnapshotInfo(TerrainSnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        TerrainSnapshot.Metadata metadata = snapshot.metadata();
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
            "plugin.earthwork.terrain_snapshot_info",
            formatTerrainSnapshotTime(metadata.capturedAtEpochMs()),
            metadata.columnCount()));
        TerrainSnapshot.ComparisonResult comparison =
            ctx.previewManager().comparePreviewTerrainWithWorld(EarthworkUiWidgets.getClientWorld());
        if (comparison != null && comparison.terrainChanged()) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                "plugin.earthwork.terrain_changed_since_preview",
                comparison.changedColumns(),
                comparison.totalColumns()));
        }
    }

    private static String formatTerrainSnapshotTime(long epochMs) {
        if (epochMs <= 0L) {
            return "-";
        }
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.ofEpochMilli(epochMs));
    }

    private void renderProjectBalanceReport(EarthworkProjectReport report) {
        ImGui.separator();
        String headerKey = report.hasCrossSiteBreakdown()
            ? "plugin.earthwork.project_global_balance_header"
            : "plugin.earthwork.project_balance_header";
        ImGui.text(PlotI18n.tr(headerKey));
        if (report.hasCrossSiteBreakdown()) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.earthwork.project_global_balance_sites", report.sitesWithVolume()));
        }
        ImGui.text(PlotI18n.tr("plugin.earthwork.project_total_cut", report.totalCut()));
        ImGui.text(PlotI18n.tr("plugin.earthwork.project_total_fill", report.totalFill()));
        ImGui.text(PlotI18n.tr("plugin.earthwork.project_net_volume", report.netVolume()));
        if (ImGui.treeNode(PlotI18n.tr("plugin.earthwork.learn.engineering_numbers"))) {
            ImGui.text(PlotI18n.tr("plugin.earthwork.reusable_cut_volume", report.reusableCut()));
            EarthworkUiWidgets.renderProjectMaterialBalance(report.materialBalance());
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.earthwork.project_balance_scope",
                    PlotI18n.tr("plugin.earthwork.balance_scope." + report.balanceScope().toLowerCase())));
            if (report.siteWideVerticalOffset() != 0) {
                ImGui.text(PlotI18n.tr(
                    "plugin.earthwork.site_wide_vertical_offset",
                    report.siteWideVerticalOffset()));
            }
            if (report.hasZoneVerticalOffsets()) {
                ImGui.text(PlotI18n.tr("plugin.earthwork.zone_vertical_offsets_header"));
                for (Map.Entry<String, Integer> entry : report.zoneVerticalOffsets().entrySet()) {
                    GradingRegion zoneRegion = ctx.project().getRegion(entry.getKey());
                    String zoneName = zoneRegion != null ? zoneRegion.getName() : entry.getKey();
                    ImGui.bulletText(PlotI18n.tr(
                        "plugin.earthwork.zone_vertical_offset_item",
                        zoneName,
                        entry.getValue()));
                }
            }
            ImGui.treePop();
        }

        if (report.hasCrossSiteBreakdown()) {
            ImGui.spacing();
            ImGui.text(PlotI18n.tr("plugin.earthwork.site_volume_header"));
            for (var entry : report.bySite().entrySet()) {
                var snapshot = entry.getValue();
                ImGui.text(PlotI18n.tr(
                    "plugin.earthwork.site_volume_item",
                    snapshot.siteName(),
                    snapshot.volumes().geometricCutVolume(),
                    snapshot.volumes().geometricFillVolume()));
            }
        }

        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.earthwork.zone_volume_header"));
        for (Map.Entry<String, EarthworkVolumeReport> entry : report.byZone().entrySet()) {
            GradingRegion zoneRegion = ctx.project().getRegion(entry.getKey());
            String zoneName = zoneRegion != null ? zoneRegion.getName() : entry.getKey();
            EarthworkVolumeReport zoneVolumes = entry.getValue();
            ImGui.text(PlotI18n.tr(
                "plugin.earthwork.zone_volume_item",
                zoneName,
                zoneVolumes.geometricCutVolume(),
                zoneVolumes.geometricFillVolume()));
        }

        if (!report.allocationMatrix().isEmpty()) {
            ImGui.spacing();
            EarthworkLearnWidgets.renderDirtFlowHeader();
            for (EarthworkAllocationMatrix.Transfer transfer : report.allocationMatrix().transfers()) {
                ImGui.bulletText(formatAllocationTransfer(
                    transfer,
                    resolveAllocationEndpoint(transfer.sourceZoneId(), true),
                    resolveAllocationEndpoint(transfer.destinationZoneId(), false)));
            }
        }

        if (!report.crossSiteAllocationMatrix().isEmpty()) {
            ImGui.spacing();
            ImGui.text(PlotI18n.tr("plugin.earthwork.learn.cross_site_dirt"));
            for (EarthworkAllocationMatrix.Transfer transfer : report.crossSiteAllocationMatrix().transfers()) {
                ImGui.bulletText(formatAllocationTransfer(
                    transfer,
                    resolveSiteAllocationEndpoint(transfer.sourceZoneId(), true, report),
                    resolveSiteAllocationEndpoint(transfer.destinationZoneId(), false, report)));
            }
        }
    }

    private static String formatAllocationTransfer(
            EarthworkAllocationMatrix.Transfer transfer,
            String sourceName,
            String destinationName) {
        if (transfer.materialClass() != null
            && transfer.materialClass() != EarthMaterialClass.UNKNOWN) {
            return PlotI18n.tr(
                "plugin.earthwork.allocation_transfer_material",
                sourceName,
                destinationName,
                transfer.volume(),
                PlotI18n.tr(transfer.materialClass().i18nKey()));
        }
        return PlotI18n.tr(
            "plugin.earthwork.allocation_transfer",
            sourceName,
            destinationName,
            transfer.volume());
    }

    private String resolveSiteAllocationEndpoint(
            String siteId,
            boolean source,
            EarthworkProjectReport report) {
        if (EarthworkAllocationMatrix.EXPORT.equals(siteId)) {
            return PlotI18n.tr("plugin.earthwork.allocation_export");
        }
        if (EarthworkAllocationMatrix.IMPORT.equals(siteId)) {
            return PlotI18n.tr("plugin.earthwork.allocation_import");
        }
        var snapshot = report.bySite().get(siteId);
        if (snapshot != null && snapshot.siteName() != null && !snapshot.siteName().isBlank()) {
            return snapshot.siteName();
        }
        EarthworkSite site = ctx.project().getSite(siteId);
        if (site != null && site.getName() != null && !site.getName().isBlank()) {
            return site.getName();
        }
        return siteId != null ? siteId : "";
    }

    private String resolveAllocationEndpoint(String zoneId, boolean source) {
        if (EarthworkAllocationMatrix.EXPORT.equals(zoneId)) {
            return PlotI18n.tr("plugin.earthwork.allocation_export");
        }
        if (EarthworkAllocationMatrix.IMPORT.equals(zoneId)) {
            return PlotI18n.tr("plugin.earthwork.allocation_import");
        }
        GradingRegion region = ctx.project().getRegion(zoneId);
        if (region != null && region.getName() != null && !region.getName().isBlank()) {
            return region.getName();
        }
        return zoneId != null ? zoneId : "";
    }
}
