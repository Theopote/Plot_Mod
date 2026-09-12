package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.PowerLineGenerationI18n;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import com.plot.plugin.powerline.style.PowerLineQuickTunePolicy;
import com.plot.plugin.powerline.style.PowerLineStyleEditor;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.engineering.validation.PowerLineValidationReport;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTreeNodeFlags;

/** 建造 Tab：预览、友好状态、智能修正、生成。 */
public final class PowerLineBuildPanel {
    enum AdvancedReportFocus {
        NONE,
        TERRAIN,
        ENGINEERING
    }

    private final PowerLineUiContext ctx;
    private final PowerLineBuildActions buildActions;
    private final PowerLineValidationPanel validationPanel;
    private boolean advancedChecksOpen = false;
    private AdvancedReportFocus advancedReportFocus = AdvancedReportFocus.NONE;

    public PowerLineBuildPanel(PowerLineUiContext ctx, PowerLineValidationPanel validationPanel) {
        this.ctx = ctx;
        this.buildActions = new PowerLineBuildActions(ctx);
        this.validationPanel = validationPanel;
    }

    public void render() {
        ctx.selection().retainExisting(ctx.project());
        PowerLineFootprint line = ctx.selection().primary(ctx.project());
        if (line == null) {
            PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.select_line_hint"));
            PowerLineUiWidgets.renderLineSelector(ctx);
            return;
        }

        renderCurrentLineHeader();
        ImGui.spacing();
        buildActions.renderPreviewActions(line);
        renderCompactPreviewSummary(line);
        renderFriendlyStatus(line);
        ImGui.separator();
        buildActions.renderBuildAction(line);
        renderAdvancedChecks(line);
    }

    private void renderCurrentLineHeader() {
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.route.current_line"));
        ImGui.sameLine();
        float width = ImGui.getContentRegionAvail().x;
        if (width > 0f) {
            ImGui.setNextItemWidth(width);
        }
        PowerLineUiWidgets.renderLineSelector(ctx);
    }

    private void renderCompactPreviewSummary(PowerLineFootprint line) {
        ctx.syncPreviewValidity(line);
        PowerLineGenerationResult result = ctx.hasValidPreview(line) ? ctx.lastGenerationResult() : null;
        ImGui.spacing();
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.build.preview_section"));

        if (result == null) {
            double worldLength = line.computeWorldPathLength(ctx.coordinates());
            int poleEstimate = line.estimatePoleCount(ctx.coordinates());
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.powerline.build.compact_summary_estimate",
                    poleEstimate,
                    formatBlocks(worldLength)));
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.build.no_preview"));
            return;
        }

        double worldLength = line.computeWorldPathLength(ctx.coordinates());
        PowerLineUiWidgets.text(PlotI18n.tr(
            "plugin.powerline.build.compact_summary",
            result.poleCount,
            formatBlocks(worldLength),
            formatBlockCount(result.blockCount())));
        renderStyleLine(line);
    }

    private void renderStyleLine(PowerLineFootprint line) {
        PowerLineStylePreset basePreset = PowerLineStyleEditor.basePreset(line);
        if (basePreset != null) {
            String styleLabel = PlotI18n.tr(basePreset.getLabelKey());
            if (PowerLineStyleEditor.isModified(line)) {
                styleLabel += PowerLineUiTextGlyphSafety.INLINE_SEPARATOR
                    + PlotI18n.tr("plugin.powerline.style.modified_badge");
            }
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.build.style_preset", styleLabel));
            return;
        }
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.build.style_custom"));
    }

    private void renderFriendlyStatus(PowerLineFootprint line) {
        ImGui.spacing();
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.build.status_section_title"));
        renderSpacingStatus(line);
        renderCornerStatus(line);

        if (!line.isVisualChecksEnabled()) {
            PowerLineStatusIcon.renderOkLine(PlotI18n.tr("plugin.powerline.build.status.decorative"));
            return;
        }

        if (!ctx.hasValidPreview(line)) {
            return;
        }

        if (line.isTerrainAvoidanceEnabled()) {
            renderTerrainStatus(line);
        }
        if (line.isLineChecksEnabled()) {
            renderLineCheckStatus(line, line.isTerrainAvoidanceEnabled());
        }
    }

    private void renderSpacingStatus(PowerLineFootprint line) {
        PowerLineFriendlyStatus.SpacingEvaluation spacing =
            PowerLineFriendlyStatus.evaluateSpacing(line, ctx.coordinates());
        switch (spacing.kind()) {
            case OK -> PowerLineStatusIcon.renderOkLine(
                PlotI18n.tr("plugin.powerline.build.status.spacing_ok"));
            case SETTINGS_ONLY -> PowerLineStatusIcon.renderOkLine(
                PlotI18n.tr("plugin.powerline.build.status.spacing_settings_ok"));
            case INVALID_SETTINGS -> PowerLineStatusIcon.renderWarningLine(
                PlotI18n.tr("plugin.powerline.build.status.spacing_invalid"));
            case TOO_CLOSE -> PowerLineStatusIcon.renderWarningLine(
                PlotI18n.tr(
                    "plugin.powerline.build.status.spacing_too_close",
                    String.format("%.1f", spacing.worstSpan()),
                    String.format("%.1f", spacing.limit())));
            case TOO_FAR -> PowerLineStatusIcon.renderWarningLine(
                PlotI18n.tr(
                    "plugin.powerline.build.status.spacing_too_far",
                    String.format("%.1f", spacing.worstSpan()),
                    String.format("%.1f", spacing.limit())));
            default -> { }
        }
    }

    private void renderCornerStatus(PowerLineFootprint line) {
        PowerLineFriendlyStatus.CornerEvaluation corners =
            PowerLineFriendlyStatus.evaluateCornerPoles(line, ctx.coordinates());
        switch (corners.kind()) {
            case OK -> PowerLineStatusIcon.renderOkLine(
                PlotI18n.tr("plugin.powerline.build.status.corners_ok"));
            case MISSING -> PowerLineStatusIcon.renderWarningLine(
                PlotI18n.tr("plugin.powerline.build.status.corners_missing", corners.missingCount()));
            case NO_PATH -> { }
            default -> { }
        }
    }

    private void renderTerrainStatus(PowerLineFootprint line) {
        PowerLineValidationReport report = ctx.actions().cachedTerrainReport(line);
        if (report == null || !PowerLineFriendlyStatus.hasTerrainIssues(report)) {
            PowerLineStatusIcon.renderOkLine(PlotI18n.tr("plugin.powerline.build.status.terrain_ok"));
            return;
        }
        PowerLineStatusIcon.renderWarningLine(PlotI18n.tr(
            "plugin.powerline.build.status.terrain_issues_summary",
            PowerLineFriendlyStatus.terrainIssueCount(report)));
        renderStatusActions(
            PlotI18n.tr("plugin.powerline.build.terrain_apply_fix"),
            () -> ctx.autoAdjustTerrain(line),
            AdvancedReportFocus.TERRAIN,
            "##build_status_terrain");
    }

    private void renderLineCheckStatus(PowerLineFootprint line, boolean terrainChecksActive) {
        PowerLineValidationReport report = ctx.actions().cachedEngineeringReport(line);
        if (report == null) {
            return;
        }
        var issues = terrainChecksActive
            ? report.issuesExcluding(com.plot.plugin.powerline.engineering.EngineeringRuleIds.CLEARANCE_GROUND_MINIMUM)
            : report.getIssues();
        if (issues.isEmpty()) {
            if (!terrainChecksActive || !PowerLineFriendlyStatus.hasTerrainIssues(
                    ctx.actions().cachedTerrainReport(line))) {
                PowerLineStatusIcon.renderOkLine(PlotI18n.tr("plugin.powerline.build.status.all_good"));
            }
            return;
        }
        PowerLineStatusIcon.renderWarningLine(PlotI18n.tr(
            "plugin.powerline.build.status.line_issues_summary",
            issues.size()));
        renderStatusActions(
            PlotI18n.tr("plugin.powerline.build.auto_fix_issues"),
            () -> validationPanel.requestSmartFix(line),
            AdvancedReportFocus.ENGINEERING,
            "##build_status_line_checks");
    }

    private void renderStatusActions(
            String autoFixLabel,
            Runnable autoFixAction,
            AdvancedReportFocus focus,
            String detailId) {
        if (autoFixLabel != null && autoFixAction != null) {
            if (ImGui.button(autoFixLabel + detailId + "_auto_fix", 0, 0)) {
                autoFixAction.run();
            }
            ImGui.sameLine();
        }
        if (ImGui.smallButton(PlotI18n.tr("plugin.powerline.build.view_details") + detailId)) {
            openAdvancedChecks(focus);
        }
    }

    private void openAdvancedChecks(AdvancedReportFocus focus) {
        advancedChecksOpen = true;
        advancedReportFocus = focus;
    }

    private void renderAdvancedChecks(PowerLineFootprint line) {
        ImGui.spacing();
        if (advancedChecksOpen) {
            ImGui.setNextItemOpen(true, ImGuiCond.Always);
        }
        boolean open = ImGui.collapsingHeader(
            PlotI18n.tr("plugin.powerline.build.advanced_checks"),
            advancedChecksOpen ? ImGuiTreeNodeFlags.DefaultOpen : ImGuiTreeNodeFlags.None);
        advancedChecksOpen = open;
        if (open) {
            renderAdvancedPreviewMetrics(line);
            renderAdvancedPreviewDetails(line);
            validationPanel.renderAdvancedChecksSection(line, advancedReportFocus);
            advancedReportFocus = AdvancedReportFocus.NONE;
        }
    }

    private void renderAdvancedPreviewMetrics(PowerLineFootprint line) {
        PowerLineGenerationResult result = ctx.hasValidPreview(line) ? ctx.lastGenerationResult() : null;
        if (result == null) {
            PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.build.no_preview"));
            return;
        }
        PowerLineStylePreset basePreset = PowerLineStyleEditor.basePreset(line);
        double worldLength = line.computeWorldPathLength(ctx.coordinates());
        double typicalSpan = PowerLineBuildMetrics.typicalSpanBlocks(
            line, worldLength, result.poleCount, line.getMaxPoleSpacing());
        PowerLineUiWidgets.text(PlotI18n.tr(
            "plugin.powerline.wire_length_result",
            String.format("%.1f", result.wireLength)));
        PowerLineUiWidgets.text(PlotI18n.tr(
            "plugin.powerline.build.conductor_count",
            PowerLineQuickTunePolicy.conductorCount(line, basePreset)));
        PowerLineUiWidgets.text(PlotI18n.tr(
            "plugin.powerline.build.detail_typical_span",
            formatBlocks(typicalSpan)));
    }

    private void renderAdvancedPreviewDetails(PowerLineFootprint line) {
        PowerLineGenerationResult result = ctx.hasValidPreview(line) ? ctx.lastGenerationResult() : null;
        if (result == null || result.warnings.isEmpty()) {
            return;
        }
        ImGui.separator();
        PowerLineStatusIcon.renderWarningLine(PlotI18n.tr(
            "plugin.powerline.clearance_warnings",
            result.warnings.size()));
        ImGui.beginChild("powerline_build_advanced_warnings", 0, 80, true);
        for (String warning : result.warnings) {
            PowerLineUiWidgets.text(PowerLineGenerationI18n.localize(warning));
        }
        ImGui.endChild();
    }

    private static String formatBlocks(double blocks) {
        return String.format("%.0f", blocks);
    }

    private static String formatBlockCount(int blocks) {
        return String.format("%d", blocks);
    }

    public void renderBuildConfirmPopup() {
        buildActions.renderBuildConfirmPopup();
    }

    public void renderOptimizationConfirmPopup() {
        validationPanel.renderSmartFixStrategyPopup();
        validationPanel.renderOptimizationConfirmPopup();
    }
}
