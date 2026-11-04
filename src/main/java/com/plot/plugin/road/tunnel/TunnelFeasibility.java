package com.plot.plugin.road.tunnel;

/**
 * Result of checking whether a tunnel cross-section can be fully enclosed by solid terrain.
 */
public record TunnelFeasibility(
        boolean valid,
        int minimumCover,
        double coveredRatio) {

    /**
     * Continuous engineering cover required above the tunnel roof shell.
     * One block is too weak: shallow open cuts / thin ledges were validated as tunnels,
     * which suppressed daylight batter and left floating lights without a roof lining.
     */
    public static final int MINIMUM_COVER_BLOCKS = 2;

    public static TunnelFeasibility invalid() {
        return new TunnelFeasibility(false, 0, 0.0);
    }
}
