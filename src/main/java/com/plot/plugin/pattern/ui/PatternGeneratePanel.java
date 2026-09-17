package com.plot.plugin.pattern.ui;

import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;
import java.util.List;

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

        if (selectedCount > 1) {
            if (ImGui.button(
                    PlotI18n.tr("plugin.pattern.preview_selected", selectedCount),
                    half,
                    0)) {
                ctx.calculatePreview(ctx.selection().resolve(ctx.project()), true);
            }
            ImGui.sameLine();
            boolean hasPreview = ctx.hasValidPreview();
            if (!hasPreview) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.pattern.clear_preview"), half, 0)) {
                ctx.clearPreview();
            }
            if (!hasPreview) {
                ImGui.endDisabled();
            }
            if (ImGui.button(
                    PlotI18n.tr("plugin.pattern.build_selected", selectedCount),
                    ImGui.getContentRegionAvailX(),
                    0)) {
                if (ctx.calculatePreview(ctx.selection().resolve(ctx.project()), true)) {
                    ctx.setBuildConfirmPending(true);
                }
            }
        } else {
            if (ImGui.button(PlotI18n.tr("plugin.pattern.calc_preview"), half, 0)) {
                ctx.calculatePreview(footprint);
            }
            ImGui.sameLine();
            boolean hasPreview = ctx.hasValidPreview();
            if (!hasPreview) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.pattern.clear_preview"), half, 0)) {
                ctx.clearPreview();
            }
            if (!hasPreview) {
                ImGui.endDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.pattern.project_preview"), ImGui.getContentRegionAvailX(), 0)) {
                ctx.projectPreview();
            }
            if (ImGui.button(PlotI18n.tr("plugin.pattern.build_direct"), ImGui.getContentRegionAvailX(), 0)) {
                if (ctx.calculatePreview(footprint)) {
                    ctx.setBuildConfirmPending(true);
                }
            }
        }

        if (!buildReadiness.ready()) {
            ImGui.textColored(PluginUiColors.ERROR_SOFT, buildReadiness.message());
        }

        PatternPreviewRenderer.render(ctx.lastGenerationResult());

        if (ctx.hasValidPreview()) {
            com.plot.plugin.pattern.PatternGenerationResult preview = ctx.lastGenerationResult();
            ImGui.text(PlotI18n.tr(
                "plugin.pattern.preview_stats",
                preview.getBlockCount()));
            if (preview.getFallbackElevationCount() > 0) {
                ImGui.textColored(
                    preview.exceedsFallbackBuildThreshold()
                        ? PluginUiColors.ERROR_SOFT
                        : PluginUiColors.HINT_GRAY,
                    PlotI18n.tr(
                        "plugin.pattern.preview_fallback_elevation",
                        preview.getFallbackElevationCount(),
                        preview.getBlockCount()));
            }
            if (preview.getSkippedOverlapCount() > 0) {
                ImGui.textColored(
                    PluginUiColors.HINT_GRAY,
                    PlotI18n.tr(
                        "plugin.pattern.preview_overlap_skipped",
                        preview.getSkippedOverlapCount()));
            }
            if (ImGui.button(PlotI18n.tr("plugin.pattern.build_confirm"), ImGui.getContentRegionAvailX(), 0)) {
                if (buildReadiness.ready()) {
                    ctx.setBuildConfirmPending(true);
                }
            }
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
