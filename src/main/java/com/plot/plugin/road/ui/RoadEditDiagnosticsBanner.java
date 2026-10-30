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
public final class RoadEditDiagnosticsBanner {
    private RoadEditDiagnosticsBanner() {
    }

    public static int countIssues(RoadUiContext ctx, RoadNetwork network, Road road) {
        if (ctx == null || network == null || road == null) {
            return 0;
        }
        // Repair 诊断已覆盖拓扑 + 中心线形状；勿再叠加 ShapeValidator 以免双计。
        int count = RoadRepairDiagnosisCache.diagnose(ctx, network, road).size();
        if (count > 0) {
            return count;
        }
        // 无 repair issue 时仍展示纯拓扑/形状提示（与 renderDetailedIssues 回退路径一致）。
        count += RoadTopologyInvariantValidator.validateRoad(network, road).size();
        count += RoadCenterlineShapeValidator.validateRoad(network, road).size();
        return count;
    }

    public static boolean render(RoadUiContext ctx, RoadNetwork network, Road road, Runnable onView) {
        return render(ctx, network, road, onView, countIssues(ctx, network, road));
    }

    public static boolean render(
            RoadUiContext ctx,
            RoadNetwork network,
            Road road,
            Runnable onView,
            int issueCount) {
        if (issueCount <= 0) {
            return false;
        }

        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, PluginUiColors.WARNING);
        ImGui.text("\u26a0 " + PlotI18n.tr("plugin.road.edit.diagnostics_banner", issueCount));
        ImGui.popStyleColor();
        if (onView != null && ImGui.button(PlotI18n.tr("plugin.road.edit.view_diagnostics") + "##edit_diag_view")) {
            onView.run();
        }
        ImGui.spacing();
        return true;
    }

    public static void renderDetailedIssues(RoadUiContext ctx, RoadNetwork network, Road road) {
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
