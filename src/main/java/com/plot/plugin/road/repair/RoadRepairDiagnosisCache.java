package com.plot.plugin.road.repair;

import com.plot.plugin.road.IntersectionProbeResult;
import com.plot.plugin.road.crossing.RoadCrossingReconciler;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.overlay.RoadOverlayWarningCache;
import com.plot.plugin.road.ui.RoadUiContext;

import java.util.List;
import java.util.Objects;

/**
 * 道路健康诊断缓存：复用 Crossing 注册完整性探测结果，
 * 供 Auto Repair 横幅与 Topology Hints 共用，避免每帧重复求交。
 * 实例归属 {@link RoadUiContext}。
 */
public final class RoadRepairDiagnosisCache {

    private final RoadOverlayWarningCache overlayWarningCache;

    private long cachedRevision = Long.MIN_VALUE;
    private String cachedRoadId = "";
    private boolean cachedAdoptPending;
    private List<RoadRepairIssue> cachedIssues = List.of();
    private RoadNetwork cachedNetwork;

    public RoadRepairDiagnosisCache() {
        this(new RoadOverlayWarningCache());
    }

    public RoadRepairDiagnosisCache(RoadOverlayWarningCache overlayWarningCache) {
        this.overlayWarningCache = Objects.requireNonNull(overlayWarningCache, "overlayWarningCache");
    }

    public RoadOverlayWarningCache overlayWarningCache() {
        return overlayWarningCache;
    }

    public void invalidate() {
        cachedRevision = Long.MIN_VALUE;
        cachedRoadId = "";
        cachedNetwork = null;
        cachedIssues = List.of();
        overlayWarningCache.invalidate();
    }

    public List<RoadRepairIssue> diagnose(RoadUiContext ctx, RoadNetwork network, Road road) {
        if (ctx == null || network == null || road == null) {
            return List.of();
        }
        long revision = ctx.networkManager().getNetworkRevision();
        boolean adoptPending = ctx.networkManager().isAdoptIntersectionRepairPending();
        String roadId = road.getId();
        boolean cacheable = network == ctx.networkManager().getNetwork();
        if (cacheable && cachedNetwork == network && revision == cachedRevision
                && roadId.equals(cachedRoadId)
                && adoptPending == cachedAdoptPending) {
            return cachedIssues;
        }

        IntersectionProbeResult probe = RoadCrossingReconciler.probeRegistryCompleteness(network);
        List<RoadRepairIssue> issues = RoadAutoRepair.diagnose(
            network,
            road,
            ctx.networkManager().getConfig(),
            probe,
            adoptPending);

        if (cacheable) {
            cachedNetwork = network;
            cachedRevision = revision;
            cachedRoadId = roadId;
            cachedAdoptPending = adoptPending;
            cachedIssues = issues;
        }
        return issues;
    }

    public boolean hasIssues(RoadUiContext ctx, RoadNetwork network, Road road) {
        return !diagnose(ctx, network, road).isEmpty();
    }
}
