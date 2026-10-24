package com.plot.plugin.earthwork.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
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
    /** 确认弹窗内缓存，避免每帧全量地形对比卡死渲染线程。 */
    private boolean confirmChecksReady;
    private String confirmEligibility;
    private TerrainSnapshot.ComparisonResult confirmTerrainComparison;

    public EarthworkGeneratePanel(EarthworkUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        GradingRegion region = ctx.project().getRegion(ctx.selectedRegionId());
        EarthworkUiWidgets.renderRegionSelector(ctx);
        if (region == null) {
            EarthworkUiWidgets.textWrappedSafe(PlotI18n.tr("plugin.earthwork.select_region_hint"));
            return;
        }
        ImGui.spacing();
        renderPreviewActions(region);

        EarthworkGenerationResult preview = ctx.previewManager().getLastGenerationResult();
        if (preview == null) {
            return;
        }
        renderPreviewStatus(preview);
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.earthwork.calc_results"));
        renderResultElevation(region, preview);
        EarthworkUiWidgets.renderPlayerCutFill(
            preview.volumeReport, preview.resolvedElevation, preview.slopedSurface,
            preview.resolvedElevationMin, preview.resolvedElevationMax, false, false);
        renderPreviewBuildButtons(preview);

        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.earthwork.ui.analysis"))) {
            ImGui.text(PlotI18n.tr("plugin.earthwork.calculation_cell_count", preview.calculationCellCount));
            renderTerrainSnapshotInfo(preview.existingTerrainSnapshot);
            if (ctx.config().isShowGrid()) {
                renderGridPreview(region, preview);
            }
            EarthworkInsightCharts.render(ctx, region, preview);
        }
        if (ctx.config().getWorkMode().showsEngineeringMetrics()
            && ImGui.collapsingHeader(PlotI18n.tr("plugin.earthwork.ui.report"))) {
            if (preview.projectReport != null && preview.projectReport.hasZoneBreakdown()) {
                renderProjectBalanceReport(preview.projectReport);
            }
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.export_report"), ImGui.getContentRegionAvailX(), 0)) {
                ctx.previewManager().exportLastReport(ctx.project(), region);
            }
        }
    }

    public void renderResultElevation(GradingRegion region, EarthworkGenerationResult preview) {
        if (preview.slopedSurface) {
            EarthworkUiWidgets.textWrappedSafe(PlotI18n.tr("plugin.earthwork.resolved_elevation_slope_result",
                preview.resolvedElevationMin, preview.resolvedElevationMax));
        } else {
            EarthworkUiWidgets.textWrappedSafe(PlotI18n.tr(EarthworkUiPresentation.elevationLabel(
                region.isAutoBalance(), ctx.previewManager().hasValidPreview()), preview.resolvedElevation));
        }
    }

    public void renderPreviewActions(GradingRegion region) {
        if (!ctx.previewManager().hasCachedPreview()) {
            renderPrimaryAction(region);
        }
    }

    public void renderPreviewBuildButtons(EarthworkGenerationResult preview) {
        renderChangeCounts(preview);
        renderPrimaryAction(ctx.project().getRegion(ctx.selectedRegionId()));
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.earthwork.ui.preview_tools"))) {
            return;
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.ui.reproject"), ImGui.getContentRegionAvailX(), 0)) {
            ctx.previewManager().projectPreview();
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.recalculate_preview"), ImGui.getContentRegionAvailX(), 0)) {
            ctx.recalculatePreview();
        }
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.clear_preview"), ImGui.getContentRegionAvailX(), 0)) {
            ctx.clearPreview();
        }
    }

    private void renderPrimaryAction(GradingRegion region) {
        boolean valid = ctx.previewManager().hasValidPreview();
        boolean busy = ctx.host().placement().isBusy()
            || ctx.previewManager().getPreviewState() == com.plot.plugin.earthwork.manager.EarthworkPreviewState.CALCULATING;
        String rejection = busy ? PlotI18n.tr("plugin.earthwork.build_in_progress_wait") : null;
        if (rejection == null && EarthworkUiPresentation.missingManualHeight(region)) {
            rejection = PlotI18n.tr("plugin.earthwork.ui.enter_height");
        }
        if (rejection == null && valid) {
            rejection = ctx.buildManager().validateBuildEligibilityWithoutTerrain(ctx.project(), ctx.selectedRegionId());
        }
        String key = valid ? "plugin.earthwork.ui.confirm_build"
            : ctx.previewManager().hasCachedPreview() ? "plugin.earthwork.recalculate_preview"
            : "plugin.earthwork.calc_preview";
        ImGui.beginDisabled(rejection != null || region == null);
        if (ImGui.button(PlotI18n.tr(key), ImGui.getContentRegionAvailX(), 0)) {
            if (valid) {
                ctx.setBuildConfirmPending(true);
            } else {
                ctx.recalculatePreview();
            }
        }
        ImGui.endDisabled();
        if (rejection != null) {
            EarthworkUiWidgets.textWrappedColored(PluginUiColors.WARNING, rejection);
        }
    }

    public void renderPreviewStatus(EarthworkGenerationResult preview) {
        boolean valid = ctx.previewManager().hasValidPreview();
        EarthworkUiWidgets.textWrappedColored(valid ? PluginUiColors.STATUS_INFO : PluginUiColors.WARNING,
            PlotI18n.tr(EarthworkUiPresentation.previewStateKey(ctx.previewManager().getPreviewState())));
        if (!valid) {
            EarthworkUiWidgets.textWrappedColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.earthwork.preview_stale_hint"));
        }
        if (preview.balanceStatus != null) {
            String balanceText = PlotI18n.tr("plugin.earthwork.ui.balance."
                + preview.balanceStatus.name().toLowerCase(java.util.Locale.ROOT));
            EarthworkUiWidgets.textWrappedColored(
                !valid ? PluginUiColors.WARNING
                    : preview.balanceStatus == com.plot.plugin.earthwork.solver.EarthworkBalanceStatus.BALANCED
                        ? PluginUiColors.STATUS_OK
                        : preview.balanceStatus == com.plot.plugin.earthwork.solver.EarthworkBalanceStatus.NOT_REQUESTED
                            ? PluginUiColors.STATUS_INFO : PluginUiColors.WARNING,
                valid ? balanceText : PlotI18n.tr("plugin.earthwork.ui.last_result", balanceText));
        }
        if (!ctx.selectedRegionId().equals(preview.sourceRegionId)) {
            GradingRegion source = ctx.project().getRegion(preview.sourceRegionId);
            EarthworkUiWidgets.textWrappedColored(PluginUiColors.WARNING,
                PlotI18n.tr("plugin.earthwork.ui.other_region",
                    source != null ? source.getName() : preview.sourceRegionId));
        }
        if (preview.unloadedColumnCount > 0) {
            EarthworkUiWidgets.textWrappedColored(PluginUiColors.WARNING,
                PlotI18n.tr("plugin.earthwork.unloaded_columns_hint", preview.unloadedColumnCount));
        }
        for (String warning : preview.warnings) {
            EarthworkUiWidgets.textWrappedColored(PluginUiColors.WARNING, PlotI18n.tr(warning));
        }
    }

    private void renderChangeCounts(EarthworkGenerationResult preview) {
        ImGui.text(PlotI18n.tr("plugin.earthwork.ui.change_total", EarthworkUiPresentation.plannedChanges(preview)));
        ImGui.text(PlotI18n.tr("plugin.earthwork.ui.change_details",
            preview.volumeReport.cutChangedBlocks(), preview.volumeReport.fillChangedBlocks(), preview.structureChangedBlocks));
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
            confirmChecksReady = false;
            confirmEligibility = null;
            confirmTerrainComparison = null;
        }

        if (ImGui.beginPopupModal("##earthwork_build_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            EarthworkGenerationResult preview = ctx.previewManager().getLastGenerationResult();
            long blockCount = EarthworkUiPresentation.plannedChanges(preview);
            ImGui.text(String.format(PlotI18n.tr("plugin.earthwork.build_confirm"), blockCount));
            if (preview != null) {
                renderChangeCounts(preview);
            }

            com.plot.api.world.PlacementReadiness readiness =
                ctx.host().projection().checkWorldModificationReadiness();
            if (!readiness.ready()) {
                ImGui.textColored(PluginUiColors.ERROR, readiness.message());
            }

            ImGui.separator();
            // 仅在弹窗打开时做一次强制地形校验并缓存，禁止每帧全量扫格
            if (!confirmChecksReady) {
                confirmEligibility = ctx.buildManager().validateBuildEligibilityFresh(
                    ctx.project(), ctx.selectedRegionId());
                confirmTerrainComparison = ctx.previewManager().comparePreviewTerrainWithWorld(
                    EarthworkUiWidgets.getClientWorld(), false);
                confirmChecksReady = true;
            }
            String eligibility = confirmEligibility;
            boolean canBuild = eligibility == null && readiness.ready();
            if (eligibility != null) {
                ImGui.textColored(PluginUiColors.WARNING, eligibility);
                if (ImGui.button(PlotI18n.tr("plugin.earthwork.recalculate_preview"), 180, 0)) {
                    GradingRegion region = ctx.project().getRegion(ctx.selectedRegionId());
                    if (region != null) {
                        ctx.recalculatePreview();
                    }
                    confirmChecksReady = false;
                    ImGui.closeCurrentPopup();
                }
                ImGui.sameLine();
            } else if (confirmTerrainComparison != null && confirmTerrainComparison.hasCosmeticSurfaceDrift()) {
                ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                    "plugin.earthwork.terrain_surface_drift_soft",
                    confirmTerrainComparison.surfaceOnlyChangedColumns(),
                    confirmTerrainComparison.totalColumns()));
            }
            if (!canBuild) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.earthwork.build"), 120, 0)) {
                ctx.buildManager().buildInWorld(ctx.project(), ctx.selectedRegionId());
                confirmChecksReady = false;
                ImGui.closeCurrentPopup();
            }
            if (!canBuild) {
                ImGui.endDisabled();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
                confirmChecksReady = false;
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        } else {
            confirmChecksReady = false;
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
        int unloaded = snapshot.unloadedColumnCount();
        if (unloaded > 0) {
            ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.earthwork.unloaded_columns_hint", unloaded));
        }
        TerrainSnapshot.ComparisonResult comparison =
            ctx.previewManager().comparePreviewTerrainWithWorld(EarthworkUiWidgets.getClientWorld());
        if (comparison != null && comparison.terrainChanged()) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                "plugin.earthwork.terrain_changed_since_preview",
                comparison.groundElevationChangedColumns(),
                comparison.totalColumns()));
        } else if (comparison != null && comparison.hasCosmeticSurfaceDrift()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                "plugin.earthwork.terrain_surface_drift_soft",
                comparison.surfaceOnlyChangedColumns(),
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
        if (report.siteWideVerticalOffset() != 0 || report.hasZoneVerticalOffsets()) {
            if (ImGui.treeNode(PlotI18n.tr("plugin.earthwork.analysis.engineering_numbers"))) {
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
    }
}
