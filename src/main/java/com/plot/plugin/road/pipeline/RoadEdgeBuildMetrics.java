package com.plot.plugin.road.pipeline;

/**
 * Mutable metrics accumulated while building a single road edge.
 */
public final class RoadEdgeBuildMetrics {
    public int cutVolume;
    public int fillVolume;
    public int bridgeCount;
    public int tunnelCount;
}
