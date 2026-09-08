package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTreeNodeFlags;

/** 建造 Tab：预览、友好状态、智能修正、生成。 */
public final class PowerLineBuildPanel {
    private final PowerLineUiContext ctx;
    private final PowerLineGeneratePanel generatePanel;
    private final PowerLineEngineeringPanel engineeringPanel;

    public PowerLineBuildPanel(
            PowerLineUiContext ctx,
            PowerLineGeneratePanel generatePanel,
            PowerLineEngineeringPanel engineeringPanel) {
        this.ctx = ctx;
        this.generatePanel = generatePanel;
        this.engineeringPanel = engineeringPanel;
    }

    public void render() {
        ctx.selection().retainExisting(ctx.project());
        PowerLineFootprint line = ctx.selection().primary(ctx.project());
        if (line == null) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.select_line_hint"));
            PowerLineUiWidgets.renderLineSelector(ctx);
            return;
        }

        PowerLineUiWidgets.renderLineSelector(ctx);
        renderPreviewSummary(line);
        renderFriendlyStatus(line);
        engineeringPanel.renderSmartFixSection(line);
        ImGui.separator();
        generatePanel.renderBuildActions(line);
        renderAdvancedChecks(line);
    }

    private void renderPreviewSummary(PowerLineFootprint line) {
        ctx.syncPreviewValidity(line);
        PowerLineGenerationResult result = ctx.hasValidPreview(line) ? ctx.lastGenerationResult() : null;
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.powerline.build.summary"));
        if (result == null) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.build.no_preview"));
            return;
        }
        ImGui.text(PlotI18n.tr("plugin.powerline.pole_count_result", result.poleCount));
        ImGui.text(PlotI18n.tr(
            "plugin.powerline.wire_length_result",
            String.format("%.1f", result.wireLength)));
    }

    private void renderFriendlyStatus(PowerLineFootprint line) {
        ImGui.text(PlotI18n.tr("plugin.powerline.build.status_section"));
        if (PowerLineFriendlyStatus.spacingLooksGood(line)) {
            ImGui.textColored(PluginUiColors.STATUS_OK, PlotI18n.tr("plugin.powerline.build.status.spacing_ok"));
        }
        ImGui.textColored(PluginUiColors.STATUS_OK, PlotI18n.tr("plugin.powerline.build.status.corners_ok"));

        if (line.isTerrainAvoidanceEnabled()) {
            renderTerrainStatus(line);
            return;
        }

        if (!line.isEngineeringAnalysisEnabled()) {
            ImGui.textColored(PluginUiColors.STATUS_OK, PlotI18n.tr("plugin.powerline.build.status.decorative"));
            return;
        }

        LineEngineeringReport report = ctx.state().getEngineeringState().getLastEngineeringReport();
        if (report == null && ctx.hasValidPreview(line)) {
            report = ctx.actions().analyzeEngineering(line);
        }

        if (report == null || report.getIssues().isEmpty()) {
            ImGui.textColored(PluginUiColors.STATUS_OK, PlotI18n.tr("plugin.powerline.build.status.all_good"));
            return;
        }

        renderIssueList(report);
    }

    private void renderTerrainStatus(PowerLineFootprint line) {
        LineEngineeringReport report = ctx.state().getEngineeringState().getLastTerrainReport();
        if (report == null && ctx.hasValidPreview(line)) {
            report = ctx.actions().analyzeTerrainCollisions(line);
        }
        if (report == null || !PowerLineFriendlyStatus.hasTerrainIssues(report)) {
            ImGui.textColored(PluginUiColors.STATUS_OK, PlotI18n.tr("plugin.powerline.build.status.terrain_ok"));
            return;
        }
        renderIssueList(report);
    }

    private void renderIssueList(LineEngineeringReport report) {
        int shown = 0;
        for (var issue : report.getIssues()) {
            if (shown >= 4) {
                ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                    "plugin.powerline.build.more_issues",
                    report.getIssues().size() - shown));
                break;
            }
            ImGui.textColored(PluginUiColors.WARNING, "⚠ " + PowerLineFriendlyStatus.friendlyIssue(issue));
            ImGui.textColored(PluginUiColors.HINT_GRAY, "  " + PowerLineFriendlyStatus.friendlySuggestion(issue));
            shown++;
        }
    }

    private void renderAdvancedChecks(PowerLineFootprint line) {
        ImGui.spacing();
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.powerline.build.advanced_checks"), ImGuiTreeNodeFlags.None)) {
            engineeringPanel.renderAdvancedChecksSection(line);
        }
    }

    public void renderBuildConfirmPopup() {
        generatePanel.renderBuildConfirmPopup();
    }

    public void renderOptimizationConfirmPopup() {
        engineeringPanel.renderOptimizationConfirmPopup();
    }
}
