package com.plot.plugin.road.pipeline.corridor;

/**
 * Developer-facing junction construction diagnostics (not shown in player UI).
 */
public record JunctionConstructionReport(
        String nodeId,
        int expectedSurfaceColumns,
        int missingSurfaceColumns,
        int blockedTunnelAirBlocks,
        int protectedStructureConflicts,
        int unresolvedClearanceBlocks,
        boolean tunnelConnectivityApplied) {

    public static JunctionConstructionReport empty(String nodeId) {
        return new JunctionConstructionReport(nodeId, 0, 0, 0, 0, 0, false);
    }

    public boolean hasSurfaceGaps() {
        return missingSurfaceColumns > 0;
    }

    public boolean hasConflicts() {
        return blockedTunnelAirBlocks > 0
            || protectedStructureConflicts > 0
            || unresolvedClearanceBlocks > 0;
    }
}
