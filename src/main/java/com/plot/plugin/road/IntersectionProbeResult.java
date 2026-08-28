package com.plot.plugin.road;

/**
 * Result of probing whether intersection splitting would still change the network.
 */
public record IntersectionProbeResult(
        IntersectionResult result,
        boolean topologyWouldChange) {

    public static IntersectionProbeResult resolved() {
        return new IntersectionProbeResult(IntersectionResult.COMPLETE, false);
    }

    public boolean isFullyResolved() {
        return result == IntersectionResult.COMPLETE && !topologyWouldChange;
    }

    public boolean hasPendingWork() {
        return topologyWouldChange && result == IntersectionResult.COMPLETE;
    }

    public boolean isIncomplete() {
        return result == IntersectionResult.INCOMPLETE;
    }
}
