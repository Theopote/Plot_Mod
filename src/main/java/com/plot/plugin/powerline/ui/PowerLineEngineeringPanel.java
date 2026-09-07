package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.engineering.EngineeringRuleProfileResolver;
import com.plot.plugin.powerline.engineering.EngineeringSeverity;
import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;
import com.plot.plugin.powerline.engineering.analysis.PowerLineEngineeringAnalyzer;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/** 电力线路工程分析 Tab。 */
public final class PowerLineEngineeringPanel {
    private final PowerLineUiContext ctx;

    public PowerLineEngineeringPanel(PowerLineUiContext ctx) {
        this.ctx = ctx;
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
        renderProfileControls(line);
        renderAnalysisControls(line);

        LineEngineeringReport report = ctx.state().getEngineeringState().getLastEngineeringReport();
        if (report != null) {
            renderReportSummary(report);
            renderIssueList(report);
        }
    }

    private void renderProfileControls(PowerLineFootprint line) {
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.powerline.engineering.profile_section"));

        EngineeringRuleProfileResolver resolver = new EngineeringRuleProfileResolver();
        var profiles = resolver.listAll();
        String[] labels = new String[profiles.size()];
        String[] ids = new String[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            labels[i] = profiles.get(i).getName();
            ids[i] = profiles.get(i).getId();
        }
        String currentId = line.effectiveEngineeringProfileId();
        int current = 0;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(currentId)) {
                current = i;
                break;
            }
        }
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.beginCombo(PlotI18n.tr("plugin.powerline.engineering.profile"), labels[current])) {
            for (int i = 0; i < labels.length; i++) {
                if (ImGui.selectable(labels[i], current == i)) {
                    ctx.pushEditSnapshot();
                    line.setEngineeringProfileId(ids[i]);
                }
            }
            ImGui.endCombo();
        }

        boolean analysisEnabled = line.isEngineeringAnalysisEnabled();
        if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.engineering.enabled"), analysisEnabled)) {
            ctx.pushEditSnapshot();
            line.setEngineeringAnalysisEnabled(!analysisEnabled);
        }
        boolean autoSelect = line.isAutomaticTowerSelectionEnabled();
        if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.engineering.auto_select"), autoSelect)) {
            ctx.pushEditSnapshot();
            line.setAutomaticTowerSelectionEnabled(!autoSelect);
            ctx.invalidatePreview();
        }
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.engineering.disclaimer"));
    }

    private void renderAnalysisControls(PowerLineFootprint line) {
        ImGui.separator();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.engineering.analyze"), 0, 0)) {
            ctx.actions().analyzeEngineering(line);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.engineering.auto_select_towers"), 0, 0)) {
            ctx.actions().proposeOptimization(line);
            ctx.state().getEngineeringState().setOptimizationConfirmPending(true);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.engineering.fix_clearance"), 0, 0)) {
            ctx.actions().proposeOptimization(line);
            ctx.state().getEngineeringState().setOptimizationConfirmPending(true);
        }
    }

    private void renderReportSummary(LineEngineeringReport report) {
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.powerline.engineering.summary"));
        ImGui.text(PlotI18n.tr(
            "plugin.powerline.engineering.profile_result",
            report.getProfileName() != null ? report.getProfileName() : report.getProfileId()));
        ImGui.text(PlotI18n.tr("plugin.powerline.engineering.errors", report.errorCount()));
        ImGui.text(PlotI18n.tr("plugin.powerline.engineering.warnings", report.warningCount()));
        ImGui.textColored(
            report.passes() ? PluginUiColors.HINT_GRAY : PluginUiColors.WARNING,
            PlotI18n.tr(
                report.passes()
                    ? "plugin.powerline.engineering.passes"
                    : "plugin.powerline.engineering.fails"));
    }

    private void renderIssueList(LineEngineeringReport report) {
        ImGui.beginChild("powerline_engineering_issues", 0, 180, true);
        for (var issue : report.getIssues()) {
            var color = issue.severity() == EngineeringSeverity.ERROR
                ? PluginUiColors.ERROR_SOFT
                : issue.severity() == EngineeringSeverity.WARNING
                    ? PluginUiColors.WARNING
                    : PluginUiColors.HINT_GRAY;
            ImGui.textColored(color, "[%s] %s".formatted(issue.severity(), issue.message()));
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr(
                    "plugin.powerline.engineering.issue_detail",
                    issue.actual(),
                    issue.required(),
                    issue.location().planPoint().x,
                    issue.location().planPoint().y));
        }
        ImGui.endChild();
    }

    public void renderOptimizationConfirmPopup() {
        if (!ctx.state().getEngineeringState().isOptimizationConfirmPending()) {
            return;
        }
        ImGui.openPopup("##powerline_engineering_opt_confirm");
        ctx.state().getEngineeringState().setOptimizationConfirmPending(false);
        if (ImGui.beginPopupModal("##powerline_engineering_opt_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            var optimization = ctx.state().getEngineeringState().getPendingOptimization();
            ImGui.text(PlotI18n.tr("plugin.powerline.engineering.proposed_changes"));
            if (optimization != null) {
                for (var action : optimization.getActions()) {
                    ImGui.textWrapped(action.getMessage() != null ? action.getMessage() : action.getType().name());
                }
            }
            if (ImGui.button(PlotI18n.tr("button.plot.confirm"), 120, 0)) {
                PowerLineFootprint line = ctx.selection().primary(ctx.project());
                ctx.actions().applyPendingOptimization(line);
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
                ctx.state().getEngineeringState().clearOptimization();
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }
}
