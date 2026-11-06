package com.plot.plugin.road.pipeline;

/**
 * Mutable metrics accumulated while building a single road edge.
 */
public final class RoadEdgeBuildMetrics {
    public int cutVolume;
    public int fillVolume;
    public int bridgeCount;
    public int tunnelCount;
    /** Feasible portal stations placed by {@link com.plot.plugin.road.tunnel.TunnelPortalPlanner}. */
    public int portalFoundCount;
    /** Tunnel-run boundary attempts that found no feasible cover station. */
    public int portalNotFoundCount;
}
