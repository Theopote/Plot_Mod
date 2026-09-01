package com.plot.plugin.road.ui;

import com.plot.plugin.road.IntersectionResult;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadTopologyRoadSplitter;
import com.plot.utils.PlotI18n;

/**
 * Topology reconcile workflows shared across road UI panels.
 */
public final class RoadTopologyWorkflow {
    private RoadTopologyWorkflow() {
    }

    /**
     * Re-runs intersection splitting and optionally refreshes preview when the user
     * had an active preview before topology changed.
     */
    public static IntersectionResult reconcileIntersections(RoadUiContext ctx, boolean refreshPreviewAfter) {
        boolean hadPreview = refreshPreviewAfter && ctx.previewManager().hasValidPreview();
        IntersectionResult result = ctx.networkManager().reconcileIntersections();
        if (hadPreview && result == IntersectionResult.COMPLETE) {
            refreshPreview(ctx);
        }
        return result;
    }

    /**
     * 自动修复道路拓扑（断开拆分 / 分叉拆分 / 闭合环 / 分段顺序），与认领后 repair 相同逻辑。
     */
    public static boolean repairTopology(RoadUiContext ctx, Road road) {
        RoadTopologyRoadSplitter.RepairResult result = ctx.networkManager().repairTopology(road);
        ctx.onGenerationConfigChanged();
        if (result.sourceRoadsRepaired() > 0 || result.newRoadsCreated() > 0) {
            ctx.status().success(PlotI18n.tr(
                "plugin.road.repair_topology_success",
                result.sourceRoadsRepaired(),
                result.newRoadsCreated()));
            return true;
        }
        ctx.status().info(PlotI18n.tr("plugin.road.repair_topology_noop"));
        return false;
    }

    private static void refreshPreview(RoadUiContext ctx) {
        if (ctx.previewManager().calculateNetworkPreview(ctx.networkManager().getNetwork())) {
            ctx.previewManager().projectRoadPreview();
            ctx.status().info(PlotI18n.tr("plugin.road.preview_refreshed_after_reconcile"));
        }
    }
}
