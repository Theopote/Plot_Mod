package com.plot.plugin.earthwork.ui;

import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.model.*;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

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
        // 建造模式预览/施工按整场合成，不按选中分区裁剪；分区选择只在区域/编辑 Tab 有意义。
        GradingRegion region = ensureFocusRegion();
        if (region == null) {
            EarthworkUiWidgets.textWrappedSafe(PlotI18n.tr("plugin.earthwork.select_region_hint"));
            return;
        }
        ImGui.spacing();
        renderPreviewActions(region);
        EarthworkUiWidgets.renderPreviewDisplaySettings(ctx, region);

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
            EarthworkInsightCharts.render(ctx, region, preview);
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
        if (ImGui.button(PlotI18n.tr("plugin.earthwork.clear_preview"), ImGui.getContentRegionAvailX(), 0)) {
            ctx.clearPreview();
        }
        UIUtils.renderEngineeringTooltip("hint.plot.earthwork.clear_preview");
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
            rejection = ctx.buildManager().validateBuildEligibilityWithoutTerrain(
                ctx.project(), previewBuildRegionId(ctx.previewManager().getLastGenerationResult()));
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
        // 同场换区只切换剖面焦点，不提示「请重新计算」
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
        if (ctx.config().getWorkMode().showsEngineeringMetrics()) {
            ImGui.text(PlotI18n.tr("plugin.earthwork.ui.change_details",
                preview.volumeReport.cutChangedBlocks(),
                preview.volumeReport.fillChangedBlocks(),
                preview.structureChangedBlocks));
        }
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
            String buildRegionId = previewBuildRegionId(preview);
            if (!confirmChecksReady) {
                confirmEligibility = ctx.buildManager().validateBuildEligibilityFresh(
                    ctx.project(), buildRegionId);
                confirmTerrainComparison = ctx.previewManager().comparePreviewTerrainWithWorld(
                    EarthworkUiWidgets.getClientWorld(), false);
                confirmChecksReady = true;
            }
            String eligibility = confirmEligibility;
            boolean canBuild = eligibility == null && readiness.ready();
            if (eligibility != null) {
                ImGui.textColored(PluginUiColors.WARNING, eligibility);
                if (ImGui.button(PlotI18n.tr("plugin.earthwork.recalculate_preview"), 180, 0)) {
                    GradingRegion region = ctx.project().getRegion(buildRegionId);
                    if (region == null) {
                        region = ctx.project().getRegion(ctx.selectedRegionId());
                    }
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
                ctx.buildManager().buildInWorld(ctx.project(), buildRegionId);
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

    /** 落地校验/建造使用预览戳分区，避免画布换区导致 mismatch。 */
    private String previewBuildRegionId(EarthworkGenerationResult preview) {
        if (preview != null && preview.sourceRegionId != null && !preview.sourceRegionId.isBlank()) {
            return preview.sourceRegionId;
        }
        return ctx.selectedRegionId();
    }

    /**
     * 生成 Tab 焦点分区：优先画布当前选中区（可自由切换看各区剖面）；
     * 无选中时回退到预览戳或首个分区。不强制改写选中，避免锁死画布点选。
     */
    private GradingRegion ensureFocusRegion() {
        GradingRegion selected = ctx.project().getRegion(ctx.selectedRegionId());
        if (selected != null) {
            return selected;
        }
        EarthworkGenerationResult preview = ctx.previewManager().getLastGenerationResult();
        if (preview != null && preview.sourceRegionId != null && !preview.sourceRegionId.isBlank()) {
            GradingRegion fromPreview = ctx.project().getRegion(preview.sourceRegionId);
            if (fromPreview != null) {
                ctx.setSelectedRegionId(preview.sourceRegionId);
                return fromPreview;
            }
        }
        if (ctx.project().getRegionCount() == 0) {
            return null;
        }
        String firstId = ctx.project().getRegions().keySet().iterator().next();
        ctx.setSelectedRegionId(firstId);
        return ctx.project().getRegion(firstId);
    }
}
