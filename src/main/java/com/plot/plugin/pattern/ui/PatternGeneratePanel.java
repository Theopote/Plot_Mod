package com.plot.plugin.pattern.ui;

import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.ui.PluginJobProgressUi;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/** 图案生成 Tab。 */
public final class PatternGeneratePanel {
    private final PatternUiContext ctx;

    public PatternGeneratePanel(PatternUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        PatternUiWidgets.ensurePrimaryFootprintSelected(ctx);
        ctx.selection().retainExisting(ctx.project());
        PatternFootprint footprint = ctx.selection().primary(ctx.project());
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;

        if (footprint == null) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.pattern.select_footprint_hint"));
            PatternUiWidgets.renderFootprintSelector(ctx);
            return;
        }

        PatternUiWidgets.renderSelectionSummary(ctx);
        PatternUiWidgets.renderFootprintSelector(ctx);
        ImGui.spacing();

        int selectedCount = ctx.selection().size();
        com.plot.api.world.PlacementReadiness buildReadiness =
            ctx.host().projection().checkWorldModificationReadiness();

        renderPreviewControls(selectedCount, footprint, half);
        renderPreviewProgress();
        ImGui.spacing();

        PatternPreviewRenderer.render(resolvePreviewResult());
        renderPreviewStats();
        renderBuildButton(buildReadiness);
    }

    private void renderPreviewStats() {
        if (!ctx.hasValidPreview()) {
            return;
        }
        com.plot.plugin.pattern.PatternGenerationResult preview = ctx.lastGenerationResult();
        ImGui.text(PlotI18n.tr("plugin.pattern.preview_stat_blocks", preview.getBlockCount()));
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.pattern.preview_stat_overlap", preview.getSkippedOverlapCount()));
        int fallbackColor = preview.exceedsFallbackBuildThreshold()
            ? PluginUiColors.ERROR_SOFT
            : PluginUiColors.HINT_GRAY;
        ImGui.textColored(
            fallbackColor,
            PlotI18n.tr("plugin.pattern.preview_stat_fallback", preview.getFallbackElevationCount()));
    }

    private void renderBuildButton(com.plot.api.world.PlacementReadiness buildReadiness) {
        if (!buildReadiness.ready()) {
            ImGui.textColored(PluginUiColors.ERROR_SOFT, buildReadiness.message());
        }

        com.plot.plugin.pattern.PatternGenerationResult preview =
            ctx.hasValidPreview() ? ctx.lastGenerationResult() : null;
        boolean fallbackBlocked = preview != null && preview.exceedsFallbackBuildThreshold();
        boolean canBuild = ctx.hasValidPreview()
            && buildReadiness.ready()
            && !ctx.host().placement().isBusy()
            && !ctx.isPreviewBusy()
            && !fallbackBlocked;
        if (!canBuild) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.pattern.build"), ImGui.getContentRegionAvailX(), 0)) {
            ctx.setBuildConfirmPending(true);
        }
        if (!canBuild) {
            ImGui.endDisabled();
        }
        if (fallbackBlocked && preview != null) {
            ImGui.textColored(
                PluginUiColors.ERROR_SOFT,
                PlotI18n.tr(
                    "plugin.pattern.build_fallback_elevation_blocked",
                    preview.getFallbackElevationCount(),
                    preview.getBlockCount()));
        }
    }

    private com.plot.plugin.pattern.PatternGenerationResult resolvePreviewResult() {
        if (ctx.isPreviewBusy()) {
            PatternPreviewJob job = ctx.previewJob();
            if (job != null && job.partialResult().hasPlacements()) {
                return job.partialResult();
            }
            return null;
        }
        return ctx.lastGenerationResult();
    }

    private void renderPreviewProgress() {
        if (!ctx.isPreviewBusy()) {
            return;
        }
        PatternPreviewJob job = ctx.previewJob();
        if (job == null) {
            return;
        }

        PluginJobProgressUi.renderJobProgress(
            PlotI18n.tr(job.phaseTranslationKey()),
            job.processedCount(),
            job.totalCount(),
            ImGui.getContentRegionAvailX(),
            "plugin.pattern.cancel_preview",
            ctx::cancelPreviewJob);
    }

    private void renderPreviewControls(int selectedCount, PatternFootprint footprint, float halfWidth) {
        String updateLabel = selectedCount > 1
            ? PlotI18n.tr("plugin.pattern.update_preview_selected", selectedCount)
            : PlotI18n.tr("plugin.pattern.update_preview");
        boolean previewBusy = ctx.isPreviewBusy();
        if (previewBusy) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(updateLabel, halfWidth, 0)) {
            if (selectedCount > 1) {
                ctx.updatePreview(ctx.selection().resolve(ctx.project()));
            } else {
                ctx.updatePreview(footprint);
            }
        }
        if (previewBusy) {
            ImGui.endDisabled();
        }
        ImGui.sameLine();
        boolean hasPreview = ctx.hasValidPreview();
        boolean clearDisabled = !hasPreview || previewBusy;
        if (clearDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.pattern.clear_preview"), halfWidth, 0)) {
            ctx.clearPreview();
        }
        if (clearDisabled) {
            ImGui.endDisabled();
        }
    }

    public void renderBuildConfirmPopup() {
        if (ctx.buildConfirmPending()) {
            ImGui.openPopup("##pattern_build_confirm");
            ctx.setBuildConfirmPending(false);
        }

        if (ImGui.beginPopupModal("##pattern_build_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            int blockCount = ctx.hasValidPreview()
                ? ctx.lastGenerationResult().getBlockCount()
                : 0;
            ImGui.text(PlotI18n.tr("plugin.pattern.build_confirm_message", blockCount));

            com.plot.api.world.PlacementReadiness readiness =
                ctx.host().projection().checkWorldModificationReadiness();
            if (!readiness.ready()) {
                ImGui.textColored(PluginUiColors.ERROR_SOFT, readiness.message());
            }

            com.plot.plugin.pattern.PatternGenerationResult preview =
                ctx.hasValidPreview() ? ctx.lastGenerationResult() : null;
            boolean fallbackBlocked = preview != null && preview.exceedsFallbackBuildThreshold();
            if (preview != null && preview.getFallbackElevationCount() > 0) {
                ImGui.textColored(
                    fallbackBlocked ? PluginUiColors.ERROR_SOFT : PluginUiColors.HINT_GRAY,
                    PlotI18n.tr(
                        "plugin.pattern.preview_fallback_elevation",
                        preview.getFallbackElevationCount(),
                        preview.getBlockCount()));
            }
            if (fallbackBlocked) {
                ImGui.textColored(
                    PluginUiColors.ERROR_SOFT,
                    PlotI18n.tr(
                        "plugin.pattern.build_fallback_elevation_blocked",
                        preview.getFallbackElevationCount(),
                        preview.getBlockCount()));
            }

            ImGui.spacing();
            boolean canBuild = readiness.ready()
                && ctx.hasValidPreview()
                && !ctx.host().placement().isBusy()
                && !fallbackBlocked;
            if (!canBuild) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.pattern.build_confirm_yes"), 120, 0)) {
                ctx.buildInWorld();
                ImGui.closeCurrentPopup();
            }
            if (!canBuild) {
                ImGui.endDisabled();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.pattern.build_confirm_no"), 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }
}
