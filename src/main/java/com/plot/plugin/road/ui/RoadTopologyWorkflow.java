package com.plot.plugin.road.ui;

import com.plot.plugin.road.IntersectionResult;
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

    private static void refreshPreview(RoadUiContext ctx) {
        if (ctx.previewManager().calculateNetworkPreview(ctx.networkManager().getNetwork())) {
            ctx.previewManager().projectRoadPreview();
            ctx.status().info(PlotI18n.tr("plugin.road.preview_refreshed_after_reconcile"));
        }
    }
}
