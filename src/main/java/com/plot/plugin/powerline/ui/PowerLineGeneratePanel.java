package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/** 电力线路生成 Tab。 */
public final class PowerLineGeneratePanel {
    private final PowerLineUiContext ctx;

    public PowerLineGeneratePanel(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        ctx.selection().retainExisting(ctx.project());
        PowerLineFootprint line = ctx.selection().primary(ctx.project());
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;

        if (line == null) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.select_line_hint"));
            PowerLineUiWidgets.renderLineSelector(ctx);
            return;
        }

        renderBuildActions(line);
    }

    void renderBuildActions(PowerLineFootprint line) {
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        ctx.syncPreviewValidity(line);

        com.plot.api.world.PlacementReadiness readiness =
            ctx.host().projection().checkWorldModificationReadiness();

        if (ImGui.button(PlotI18n.tr("plugin.powerline.calc_preview"), half, 0)) {
            ctx.calculatePreview(line);
            if (line.isEngineeringAnalysisEnabled()) {
                ctx.actions().analyzeEngineering(line);
            }
        }
        ImGui.sameLine();
        boolean hasPreview = ctx.hasValidPreview(line);
        if (!hasPreview) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.clear_preview"), half, 0)) {
            ctx.clearPreview();
        }
        if (!hasPreview) {
            ImGui.endDisabled();
        }

        if (ImGui.button(PlotI18n.tr("plugin.powerline.build_direct"), ImGui.getContentRegionAvailX(), 0)) {
            if (ctx.calculatePreview(line)) {
                if (line.isEngineeringAnalysisEnabled()) {
                    ctx.actions().analyzeEngineering(line);
                }
                ctx.setBuildConfirmPending(true);
            }
        }

        if (!readiness.ready()) {
            ImGui.textColored(PluginUiColors.ERROR_SOFT, readiness.message());
        }

        if (ctx.lastGenerationResult() != null && !hasPreview) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.powerline.preview_stale"));
        }

        PowerLineGenerationResult result = ctx.hasValidPreview(line) ? ctx.lastGenerationResult() : null;
        if (result != null) {
            renderPreviewStats(line, result, readiness);
        }
    }

    private void renderPreviewStats(
            PowerLineFootprint line,
            PowerLineGenerationResult result,
            com.plot.api.world.PlacementReadiness readiness) {
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.powerline.preview_stats"));
        ImGui.text(PlotI18n.tr("plugin.powerline.pole_count_result", result.poleCount));
        if (result.roleCount(TowerRole.SUSPENSION) > 0) {
            ImGui.text(PlotI18n.tr(
                "plugin.powerline.role_stats_suspension",
                result.roleCount(TowerRole.SUSPENSION)));
        }
        if (result.roleCount(TowerRole.ANGLE) > 0) {
            ImGui.text(PlotI18n.tr(
                "plugin.powerline.role_stats_angle",
                result.roleCount(TowerRole.ANGLE)));
        }
        if (result.roleCount(TowerRole.DEAD_END) > 0) {
            ImGui.text(PlotI18n.tr(
                "plugin.powerline.role_stats_dead_end",
                result.roleCount(TowerRole.DEAD_END)));
        }
        if (result.roleCount(TowerRole.TERMINAL) > 0) {
            ImGui.text(PlotI18n.tr(
                "plugin.powerline.role_stats_terminal",
                result.roleCount(TowerRole.TERMINAL)));
        }
        if (result.roleCount(TowerRole.SPECIAL) > 0) {
            ImGui.text(PlotI18n.tr(
                "plugin.powerline.role_stats_special",
                result.roleCount(TowerRole.SPECIAL)));
        }
        ImGui.text(PlotI18n.tr(
            "plugin.powerline.wire_length_result",
            String.format("%.1f", result.wireLength)));
        ImGui.text(PlotI18n.tr("plugin.powerline.block_count_result", result.blockCount()));
        if (!result.warnings.isEmpty()) {
            ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
                "plugin.powerline.clearance_warnings",
                result.warnings.size()));
            ImGui.beginChild("powerline_warnings", 0, 80, true);
            for (String warning : result.warnings) {
                ImGui.textWrapped(warning);
            }
            ImGui.endChild();
        }

        boolean buildDisabled = !readiness.ready()
            || ctx.host().placement().isBusy()
            || !ctx.hasValidPreview(line);
        if (buildDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.build"), ImGui.getContentRegionAvailX(), 0)) {
            if (ctx.requestBuildConfirm(line)) {
                ctx.setBuildConfirmPending(true);
            }
        }
        if (buildDisabled) {
            ImGui.endDisabled();
        }
    }

    public void renderBuildConfirmPopup() {
        if (!ctx.buildConfirmPending()) {
            return;
        }
        ImGui.openPopup("##powerline_build_confirm");
        ctx.setBuildConfirmPending(false);
        if (ImGui.beginPopupModal(
                "##powerline_build_confirm",
                ImGuiWindowFlags.AlwaysAutoResize)) {
            PowerLineFootprint line = ctx.selection().primary(ctx.project());
            PowerLineGenerationResult result = ctx.hasValidPreview(line) ? ctx.lastGenerationResult() : null;
            int blocks = result != null ? result.blockCount() : 0;
            ImGui.text(PlotI18n.tr("plugin.powerline.build_confirm", blocks));
            boolean canBuild = result != null && ctx.requestBuildConfirm(line);
            if (!canBuild) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("button.plot.confirm"), 120, 0)) {
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
