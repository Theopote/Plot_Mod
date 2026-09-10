package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.engineering.validation.PowerLineValidationReport;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 建造 Tab 操作按钮（预览 / 落地）。 */
final class PowerLineBuildActions {
    private final PowerLineUiContext ctx;

    PowerLineBuildActions(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    void render(PowerLineFootprint line) {
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        ctx.syncPreviewValidity(line);

        com.plot.api.world.PlacementReadiness readiness =
            ctx.host().projection().checkWorldModificationReadiness();
        boolean hasPreview = ctx.hasValidPreview(line);

        if (ImGui.button(PlotI18n.tr("plugin.powerline.calc_preview"), half, 0)) {
            ctx.calculatePreview(line);
        }
        ImGui.sameLine();
        if (!hasPreview) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.clear_preview"), half, 0)) {
            ctx.clearPreview();
        }
        if (!hasPreview) {
            ImGui.endDisabled();
        }

        if (!readiness.ready()) {
            PowerLineUiWidgets.textColored(PluginUiColors.ERROR_SOFT, readiness.message());
        }

        if (ctx.lastGenerationResult() != null && !hasPreview) {
            PowerLineStatusIcon.renderWarningLine(PlotI18n.tr("plugin.powerline.preview_stale"));
        }

        boolean buildDisabled = !readiness.ready()
            || ctx.host().placement().isBusy()
            || !hasPreview;
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

    void renderBuildConfirmPopup() {
        if (PowerLineUiWidgets.beginDeferredPopupModal(
                "##powerline_build_confirm",
                ctx.buildConfirmPending(),
                () -> ctx.setBuildConfirmPending(false))) {
            PowerLineFootprint line = ctx.selection().primary(ctx.project());
            PowerLineGenerationResult result = ctx.hasValidPreview(line) ? ctx.lastGenerationResult() : null;
            int blocks = result != null ? result.blockCount() : 0;
            PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.build_confirm", blocks));
            PowerLineValidationReport report = line != null ? ctx.actions().cachedEngineeringReport(line) : null;
            PowerLineValidationReport terrain = line != null ? ctx.state().getValidationState().getLastTerrainReport() : null;
            int issueCount = report != null ? report.errorCount() + report.warningCount() : 0;
            int terrainHits = terrain != null ? terrain.errorCount() + terrain.warningCount() : 0;
            if (issueCount == 0) {
                issueCount = terrainHits;
            }
            if (issueCount > 0) {
                PowerLineStatusIcon.renderWarningLine(
                    PlotI18n.tr("plugin.powerline.build_confirm_issues", issueCount, terrainHits));
            }
            boolean canBuild = result != null && ctx.requestBuildConfirm(line);
            if (!canBuild) {
                ImGui.beginDisabled();
            }
            String confirmLabel = issueCount > 0
                ? PlotI18n.tr("plugin.powerline.build_anyway")
                : PlotI18n.tr("button.plot.confirm");
            if (ImGui.button(confirmLabel, 120, 0)) {
                ctx.buildInWorld();
                ImGui.closeCurrentPopup();
            }
            if (!canBuild) {
                ImGui.endDisabled();
            }
            ImGui.sameLine();
            if (ImGui.button(
                    issueCount > 0
                        ? PlotI18n.tr("plugin.powerline.build_return_adjust")
                        : PlotI18n.tr("button.plot.cancel"),
                    120,
                    0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }
}
