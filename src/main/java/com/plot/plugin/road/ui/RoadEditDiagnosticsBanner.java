package com.plot.plugin.road.ui;

import com.plot.plugin.road.centerline.RoadCenterlineShapeValidator;
import com.plot.plugin.road.centerline.RoadCenterlineViolation;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadTopologyInvariantValidator;
import com.plot.plugin.road.model.RoadTopologyViolation;
import com.plot.plugin.road.repair.RoadRepairDiagnosisCache;
import com.plot.plugin.road.repair.RoadRepairIssue;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/** Edit Tab 单路诊断横幅：仅当道路存在问题时显示。 */
final class RoadEditDiagnosticsBanner {
    private RoadEditDiagnosticsBanner() {
    }

    static int countIssues(RoadUiContext ctx, RoadNetwork network, Road road) {
        if (ctx == null || network == null || road == null) {
            return 0;
        }
        int count = RoadRepairDiagnosisCache.diagnose(ctx, network, road).size();
        if (count == 0 && !RoadRepairDiagnosisCache.hasIssues(ctx, network, road)) {
            count += RoadTopologyInvariantValidator.validateRoad(network, road).size();
        }
        count += RoadCenterlineShapeValidator.validateRoad(network, road).size();
        return count;
    }

    static boolean render(RoadUiContext ctx, RoadNetwork network, Road road, Runnable onView) {
        int count = countIssues(ctx, network, road);
        if (count <= 0) {
            return false;
        }

        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, PluginUiColors.WARNING);
        ImGui.text("\u26a0 " + PlotI18n.tr("plugin.road.edit.diagnostics_banner", count));
        ImGui.popStyleColor();
        if (onView != null && ImGui.button(PlotI18n.tr("plugin.road.edit.view_diagnostics") + "##edit_diag_view")) {
            onView.run();
        }
        ImGui.spacing();
        return true;
    }

    static void renderDetailedIssues(RoadUiContext ctx, RoadNetwork network, Road road) {
        List<RoadRepairIssue> repairIssues = RoadRepairDiagnosisCache.diagnose(ctx, network, road);
        if (!repairIssues.isEmpty()) {
            RoadAutoRepairUi.renderDetailed(ctx, network, road);
            return;
        }
        renderTopologyHints(ctx, network, road);
        renderCenterlineHints(ctx, network, road);
    }

    private static void renderTopologyHints(RoadUiContext ctx, RoadNetwork network, Road road) {
        if (RoadRepairDiagnosisCache.hasIssues(ctx, network, road)) {
            return;
        }
        List<RoadTopologyViolation> violations = RoadTopologyInvariantValidator.validateRoad(network, road);
        for (RoadTopologyViolation violation : violations) {
            var message = com.plot.plugin.road.validation.RoadValidationMessageCatalog
                .fromTopologyKind(violation.kind());
            if (message != null) {
                RoadValidationMessageUi.render(message, ctx, network, road);
            }
        }
    }

    private static void renderCenterlineHints(RoadUiContext ctx, RoadNetwork network, Road road) {
        List<RoadCenterlineViolation> violations = RoadCenterlineShapeValidator.validateRoad(network, road);
        for (RoadCenterlineViolation violation : violations) {
            var message = com.plot.plugin.road.validation.RoadValidationMessageCatalog
                .fromCenterlineKind(violation.kind());
            if (message != null) {
                RoadValidationMessageUi.render(message, ctx, network, road);
            }
        }
    }
}
