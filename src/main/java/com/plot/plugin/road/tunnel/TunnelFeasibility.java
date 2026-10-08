package com.plot.plugin.road.tunnel;

/**
 * Result of checking whether a tunnel cross-section can be fully enclosed by solid terrain.
 */
public record TunnelFeasibility(
        boolean valid,
        int minimumCover,
        double coveredRatio) {

    public static final int MINIMUM_COVER_BLOCKS = 1;

    public static TunnelFeasibility invalid() {
        return new TunnelFeasibility(false, 0, 0.0);
    }
}
