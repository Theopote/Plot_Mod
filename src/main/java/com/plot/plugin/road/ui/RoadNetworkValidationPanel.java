package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadNetworkEngineeringValidator;
import com.plot.plugin.road.RoadNetworkValidationReport;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.validation.RoadValidationMessage;
import com.plot.plugin.road.validation.RoadValidationMessageCatalog;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;
import java.util.Map;

/**
 * Generate Tab：预览与落地之间的路网工程检查摘要。
 */
public final class RoadNetworkValidationPanel {

    private RoadNetworkValidationPanel() {
    }

    public static RoadNetworkValidationReport analyze(RoadUiContext ctx) {
        RoadNetwork network = ctx.networkManager().getNetwork();
        Map<String, RoadGenerationResult> edgeResults = ctx.previewManager().getLastEdgeResults();
        return RoadNetworkEngineeringValidator.analyze(
            network,
            edgeResults,
            ctx.networkManager().getConfig());
    }

    public static void render(RoadNetworkValidationReport report, RoadUiContext ctx) {
        if (report.items().isEmpty()) {
            return;
        }

        ImGui.text(PlotI18n.tr("plugin.road.validation_section"));
        for (RoadNetworkValidationReport.Item item : report.items()) {
            RoadValidationMessage message = RoadValidationMessageCatalog.fromReportItem(item);
            if (message != null) {
                RoadValidationMessageUi.render(message, ctx, ctx.networkManager().getNetwork(), null);
            }
        }
        if (report.blocksBuild()) {
            ImGui.textColored(
                PluginUiColors.ERROR_SOFT,
                PlotI18n.tr("plugin.road.build_blocked_validation"));
        }
        ImGui.spacing();
    }

    public static void renderConfirmWarnings(RoadNetworkValidationReport report) {
        if (report.blocksBuild()) {
            return;
        }
        List<RoadNetworkValidationReport.Item> warnings = report.nonOkItems();
        if (warnings.isEmpty()) {
            return;
        }
        ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.road.build_confirm_validation_header"));
        for (RoadNetworkValidationReport.Item item : warnings) {
            RoadValidationMessage message = RoadValidationMessageCatalog.fromReportItem(item);
            if (message != null) {
                RoadValidationMessageUi.render(message);
            }
        }
        ImGui.spacing();
    }
}
