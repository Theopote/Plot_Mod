package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.engineering.validation.PowerLineBuildPolicy;
import com.plot.plugin.powerline.engineering.validation.PowerLineValidationReport;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiHoveredFlags;

/** 建造 Tab 操作按钮（预览 / 落地）。 */
final class PowerLineBuildActions {
    private final PowerLineUiContext ctx;

    PowerLineBuildActions(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    void renderPreviewActions(PowerLineFootprint line) {
        ctx.syncPreviewValidity(line);
        boolean hasPreview = ctx.hasValidPreview(line);
        boolean showClear = hasPreview && !ctx.isPreviewAutoRefreshEnabled();
        String previewLabel = hasPreview
            ? PlotI18n.tr("plugin.powerline.build.refresh_preview")
            : PlotI18n.tr("plugin.powerline.build.generate_preview");

        float spacing = ImGui.getStyle().getItemSpacingX();
        float refreshWidth = ImGui.getContentRegionAvailX();
        if (showClear) {
            float clearWidth = ImGui.calcTextSize(PlotI18n.tr("plugin.powerline.clear_preview")).x
                + ImGui.getStyle().getFramePaddingX() * 2f
                + 8f;
            refreshWidth = Math.max(120f, refreshWidth - clearWidth - spacing);
        }

        if (ImGui.button(previewLabel + "##build_preview", refreshWidth, 0)) {
            ctx.calculatePreview(line, true);
        }
        if (showClear) {
            ImGui.sameLine(0f, spacing);
            if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.clear_preview") + "##build_clear_preview")) {
                ctx.clearPreview();
            }
        }

        if (ctx.lastGenerationResult() != null && !hasPreview) {
            PowerLineStatusIcon.renderWarningLine(PlotI18n.tr("plugin.powerline.preview_stale"));
        }
    }

    void renderBuildAction(PowerLineFootprint line) {
        ctx.syncPreviewValidity(line);
        com.plot.api.world.PlacementReadiness readiness =
            ctx.host().projection().checkWorldModificationReadiness();
        boolean hasPreview = ctx.hasValidPreview(line);

        if (!readiness.ready()) {
            PowerLineUiWidgets.textColored(PluginUiColors.ERROR_SOFT, readiness.message());
        }

        PowerLineValidationReport engineering = ctx.actions().cachedEngineeringReport(line);
        PowerLineValidationReport terrain = ctx.actions().cachedTerrainReport(line);
        boolean blockingValidation = PowerLineBuildPolicy.hasBlockingIssues(line, engineering, terrain);
        boolean parametricBlocked = ctx.actions().hasParametricBuildBlocking(line);
        boolean buildDisabled = !readiness.ready()
            || ctx.host().placement().isBusy()
            || !hasPreview
            || blockingValidation
            || parametricBlocked;
        if (blockingValidation) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.ERROR_SOFT,
                PlotI18n.tr("plugin.powerline.build_blocked_validation"));
        }
        if (parametricBlocked) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.ERROR_SOFT,
                PlotI18n.tr("plugin.powerline.build_blocked_parametric"));
        }
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
            if (parametricBlocked && ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
                ImGui.setTooltip(PlotI18n.tr("plugin.powerline.build_blocked_parametric_tooltip"));
            }
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
            PowerLineValidationReport terrain = line != null ? ctx.actions().cachedTerrainReport(line) : null;
            boolean blockingValidation = PowerLineBuildPolicy.hasBlockingIssues(line, report, terrain);
            boolean parametricBlocked = ctx.actions().hasParametricBuildBlocking(line);
            int errorCount = PowerLineBuildPolicy.blockingErrorCount(line, report, terrain);
            int warningCount = 0;
            if (line != null && line.isLineChecksEnabled() && report != null) {
                warningCount += report.warningCount();
            }
            if (line != null && line.isTerrainAvoidanceEnabled() && terrain != null) {
                warningCount += terrain.warningCount();
            }
            if (blockingValidation) {
                PowerLineUiWidgets.textColored(
                    PluginUiColors.ERROR_SOFT,
                    PlotI18n.tr("plugin.powerline.build_blocked_validation"));
                PowerLineStatusIcon.renderWarningLine(
                    PlotI18n.tr("plugin.powerline.build_confirm_errors", errorCount));
            } else if (parametricBlocked) {
                PowerLineUiWidgets.textColored(
                    PluginUiColors.ERROR_SOFT,
                    PlotI18n.tr("plugin.powerline.build_blocked_parametric"));
            } else if (warningCount > 0) {
                PowerLineStatusIcon.renderWarningLine(
                    PlotI18n.tr("plugin.powerline.build_confirm_warnings", warningCount));
            }
            boolean canBuild = result != null
                && ctx.requestBuildConfirm(line)
                && !blockingValidation
                && !parametricBlocked;
            if (!canBuild) {
                ImGui.beginDisabled();
            }
            String confirmLabel = warningCount > 0 && !blockingValidation
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
                    warningCount > 0 || blockingValidation
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
