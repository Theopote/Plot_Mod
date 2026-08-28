package com.plot.plugin.road.ui;

import com.plot.plugin.road.RoadNetworkEngineeringValidator;
import com.plot.plugin.road.RoadNetworkValidationReport;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.Map;

/**
 * Generate Tab：预览与落地之间的路网工程检查摘要。
 */
public final class RoadNetworkValidationPanel {

    private RoadNetworkValidationPanel() {
    }

    public static void render(RoadUiContext ctx) {
        RoadNetwork network = ctx.networkManager().getNetwork();
        Map<String, RoadGenerationResult> edgeResults = ctx.previewManager().getLastEdgeResults();
        RoadNetworkValidationReport report = RoadNetworkEngineeringValidator.analyze(
            network,
            edgeResults,
            ctx.networkManager().getConfig());
        if (report.items().isEmpty()) {
            return;
        }

        ImGui.text(PlotI18n.tr("plugin.road.validation_section"));
        for (RoadNetworkValidationReport.Item item : report.items()) {
            renderItem(item);
        }
        if (report.hasIntersectionWork()) {
            if (ImGui.button(PlotI18n.tr("plugin.road.validation.reconcile_intersections"))) {
                ctx.networkManager().reconcileIntersections();
            }
            ImGui.spacing();
        }
        ImGui.spacing();
    }

    private static void renderItem(RoadNetworkValidationReport.Item item) {
        int color = switch (item.level()) {
            case OK -> PluginUiColors.STATUS_OK;
            case WARNING -> PluginUiColors.WARNING;
            case ERROR -> PluginUiColors.ERROR;
        };
        String prefix = switch (item.level()) {
            case OK -> "\u2713 ";
            case WARNING -> "\u26a0 ";
            case ERROR -> "\u2717 ";
        };
        ImGui.textColored(color, prefix + PlotI18n.tr(item.messageKey(), item.args()));
    }
}
