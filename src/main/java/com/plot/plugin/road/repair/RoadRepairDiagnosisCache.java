package com.plot.plugin.road.repair;

import com.plot.plugin.road.IntersectionProbeResult;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.overlay.RoadOverlayWarningCache;
import com.plot.plugin.road.ui.RoadUiContext;

import java.util.List;

/**
 * 道路健康诊断缓存：复用 {@link RoadNetworkBuilder#probeIntersectionCompleteness} 结果，
 * 供 Auto Repair 横幅与 Topology Hints 共用，避免每帧重复 snapshot + 求交。
 */
public final class RoadRepairDiagnosisCache {

    private static final RoadNetworkBuilder PROBE_BUILDER = new RoadNetworkBuilder();

    private static long cachedRevision = Long.MIN_VALUE;
    private static String cachedRoadId = "";
    private static boolean cachedAdoptPending;
    private static List<RoadRepairIssue> cachedIssues = List.of();

    private RoadRepairDiagnosisCache() {
    }

    public static void invalidate() {
        cachedRevision = Long.MIN_VALUE;
        cachedRoadId = "";
        RoadOverlayWarningCache.invalidate();
    }

    public static List<RoadRepairIssue> diagnose(RoadUiContext ctx, RoadNetwork network, Road road) {
        if (ctx == null || network == null || road == null) {
            return List.of();
        }
        long revision = ctx.networkManager().getNetworkRevision();
        boolean adoptPending = ctx.networkManager().isAdoptIntersectionRepairPending();
        String roadId = road.getId();
        if (revision == cachedRevision
                && roadId.equals(cachedRoadId)
                && adoptPending == cachedAdoptPending) {
            return cachedIssues;
        }

        IntersectionProbeResult probe = PROBE_BUILDER.probeIntersectionCompleteness(network);
        List<RoadRepairIssue> issues = RoadAutoRepair.diagnose(
            network,
            road,
            ctx.networkManager().getConfig(),
            probe,
            adoptPending);

        cachedRevision = revision;
        cachedRoadId = roadId;
        cachedAdoptPending = adoptPending;
        cachedIssues = issues;
        return issues;
    }

    public static boolean hasIssues(RoadUiContext ctx, RoadNetwork network, Road road) {
        return !diagnose(ctx, network, road).isEmpty();
    }
}
