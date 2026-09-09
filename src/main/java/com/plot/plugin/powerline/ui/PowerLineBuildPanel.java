package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.PowerLineGenerationI18n;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.style.PowerLineQuickTunePolicy;
import com.plot.plugin.powerline.style.PowerLineStyleEditor;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
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
    private final PowerLineBuildActions buildActions;
    private final PowerLineEngineeringPanel engineeringPanel;

    public PowerLineBuildPanel(PowerLineUiContext ctx, PowerLineEngineeringPanel engineeringPanel) {
        this.ctx = ctx;
        this.buildActions = new PowerLineBuildActions(ctx);
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
        buildActions.render(line);
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

        PowerLineStylePreset basePreset = PowerLineStyleEditor.basePreset(line);
        if (basePreset != null) {
            PowerLineStyleCardRenderer.renderCompactStylePreview(basePreset);
        } else {
            PowerLineStyleCardRenderer.renderCompactCustomStylePreview();
        }
        ImGui.sameLine();
        ImGui.beginGroup();
        if (basePreset != null) {
            String styleLabel = PlotI18n.tr(basePreset.getLabelKey());
            if (PowerLineStyleEditor.isModified(line)) {
                styleLabel += " · " + PlotI18n.tr("plugin.powerline.style.modified_badge");
            }
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.build.style_preset", styleLabel));
        } else {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.build.style_custom"));
        }
        ImGui.text(PlotI18n.tr("plugin.powerline.pole_count_result", result.poleCount));
        ImGui.text(PlotI18n.tr("plugin.powerline.block_count_result", result.blockCount()));
        ImGui.text(PlotI18n.tr(
            "plugin.powerline.build.conductor_count",
            PowerLineQuickTunePolicy.conductorCount(line, basePreset)));
        ImGui.endGroup();
    }

    private void renderFriendlyStatus(PowerLineFootprint line) {
        ImGui.text(PlotI18n.tr("plugin.powerline.build.status_section"));
        renderSpacingStatus(line);
        renderCornerStatus(line);

        if (line.isTerrainAvoidanceEnabled()) {
            renderTerrainStatus(line);
            return;
        }

        if (!line.isEngineeringAnalysisEnabled()) {
            ImGui.textColored(PluginUiColors.STATUS_OK, PlotI18n.tr("plugin.powerline.build.status.decorative"));
            return;
        }

        if (!ctx.hasValidPreview(line)) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.build.no_preview"));
            return;
        }

        LineEngineeringReport report = ctx.actions().cachedEngineeringReport(line);

        if (report == null || report.getIssues().isEmpty()) {
            ImGui.textColored(PluginUiColors.STATUS_OK, PlotI18n.tr("plugin.powerline.build.status.all_good"));
            return;
        }

        renderIssueList(report);
    }

    private void renderSpacingStatus(PowerLineFootprint line) {
        PowerLineFriendlyStatus.SpacingEvaluation spacing = PowerLineFriendlyStatus.evaluateSpacing(line);
        switch (spacing.kind()) {
            case OK -> ImGui.textColored(
                PluginUiColors.STATUS_OK,
                PlotI18n.tr("plugin.powerline.build.status.spacing_ok"));
            case SETTINGS_ONLY -> ImGui.textColored(
                PluginUiColors.STATUS_OK,
                PlotI18n.tr("plugin.powerline.build.status.spacing_settings_ok"));
            case INVALID_SETTINGS -> ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.powerline.build.status.spacing_invalid"));
            case TOO_CLOSE -> ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr(
                    "plugin.powerline.build.status.spacing_too_close",
                    String.format("%.1f", spacing.worstSpan()),
                    String.format("%.1f", spacing.limit())));
            case TOO_FAR -> ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr(
                    "plugin.powerline.build.status.spacing_too_far",
                    String.format("%.1f", spacing.worstSpan()),
                    String.format("%.1f", spacing.limit())));
            default -> { }
        }
    }

    private void renderCornerStatus(PowerLineFootprint line) {
        PowerLineFriendlyStatus.CornerEvaluation corners = PowerLineFriendlyStatus.evaluateCornerPoles(line);
        switch (corners.kind()) {
            case OK -> ImGui.textColored(
                PluginUiColors.STATUS_OK,
                PlotI18n.tr("plugin.powerline.build.status.corners_ok"));
            case MISSING -> ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.powerline.build.status.corners_missing", corners.missingCount()));
            case NO_PATH -> { }
            default -> { }
        }
    }

    private void renderTerrainStatus(PowerLineFootprint line) {
        if (!ctx.hasValidPreview(line)) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.build.no_preview"));
            return;
        }
        LineEngineeringReport report = ctx.actions().cachedTerrainReport(line);
        if (report == null || !PowerLineFriendlyStatus.hasTerrainIssues(report)) {
            ImGui.textColored(PluginUiColors.STATUS_OK, PlotI18n.tr("plugin.powerline.build.status.terrain_ok"));
            return;
        }
        ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.powerline.build.status.terrain_warning"));
        renderIssueList(report);
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.build.terrain_apply_fix_hint"));
        if (ImGui.button(PlotI18n.tr("plugin.powerline.build.terrain_apply_fix"), 0, 0)) {
            ctx.autoAdjustTerrain(line);
        }
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
            renderAdvancedPreviewDetails(line);
            engineeringPanel.renderAdvancedChecksSection(line);
        }
    }

    private void renderAdvancedPreviewDetails(PowerLineFootprint line) {
        PowerLineGenerationResult result = ctx.hasValidPreview(line) ? ctx.lastGenerationResult() : null;
        if (result == null) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.build.no_preview"));
            return;
        }
        if (result.warnings.isEmpty()) {
            return;
        }
        ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr(
            "plugin.powerline.clearance_warnings",
            result.warnings.size()));
        ImGui.beginChild("powerline_build_advanced_warnings", 0, 80, true);
        for (String warning : result.warnings) {
            ImGui.textWrapped(PowerLineGenerationI18n.localize(warning));
        }
        ImGui.endChild();
        ImGui.separator();
    }

    public void renderBuildConfirmPopup() {
        buildActions.renderBuildConfirmPopup();
    }

    public void renderOptimizationConfirmPopup() {
        engineeringPanel.renderOptimizationConfirmPopup();
    }
}
