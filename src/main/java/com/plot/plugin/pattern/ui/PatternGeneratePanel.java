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
            boolean hasPreview = ctx.lastGenerationResult() != null;
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
            boolean hasPreview = ctx.lastGenerationResult() != null;
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
                    ctx.projectPreview();
                    ctx.setBuildConfirmPending(true);
                }
            }
        }

        if (!buildReadiness.ready()) {
            ImGui.textColored(PluginUiColors.ERROR_SOFT, buildReadiness.message());
        }

        if (ctx.lastGenerationResult() != null) {
            ImGui.text(PlotI18n.tr(
                "plugin.pattern.preview_stats",
                ctx.lastGenerationResult().getBlockCount()));
            if (ImGui.button(PlotI18n.tr("plugin.pattern.build_confirm"), ImGui.getContentRegionAvailX(), 0)) {
                if (buildReadiness.ready()) {
                    ctx.setBuildConfirmPending(true);
                }
            }
        }
    }

    public void renderBuildConfirmPopup() {
        if (!ctx.buildConfirmPending()) {
            return;
        }
        ImGui.openPopup("##pattern_build_confirm");
        ctx.setBuildConfirmPending(false);
        if (ImGui.beginPopupModal("##pattern_build_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            int blockCount = ctx.lastGenerationResult() != null
                ? ctx.lastGenerationResult().getBlockCount()
                : 0;
            ImGui.text(PlotI18n.tr("plugin.pattern.build_confirm_message", blockCount));
            ImGui.spacing();
            if (ImGui.button(PlotI18n.tr("plugin.pattern.build_confirm_yes"), 120, 0)) {
                ctx.buildInWorld();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.pattern.build_confirm_no"), 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }
}
