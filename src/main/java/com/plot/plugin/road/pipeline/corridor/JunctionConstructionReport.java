package com.plot.plugin.road.pipeline.corridor;

/**
 * Developer-facing junction construction diagnostics (not shown in player UI).
 * Merges local surface mask, tunnel connectivity, and post-merge aggregate checks.
 */
public record JunctionConstructionReport(
        String nodeId,
        int expectedSurfaceColumns,
        int missingSurfaceColumns,
        int blockedTunnelAirBlocks,
        int protectedStructureConflicts,
        int unresolvedClearanceBlocks,
        boolean tunnelConnectivityApplied,
        int postMergeMissingSurfaceColumns,
        int postMergeSurfaceSemanticMismatches,
        int postMergeProtectedLosses,
        int disconnectedTunnelComponents,
        int crossLayerLeaks) {

    public static JunctionConstructionReport empty(String nodeId) {
        return new JunctionConstructionReport(nodeId, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, 0);
    }

    public boolean hasSurfaceGaps() {
        return missingSurfaceColumns > 0
            || postMergeMissingSurfaceColumns > 0
            || postMergeSurfaceSemanticMismatches > 0;
    }

    public boolean hasConflicts() {
        return blockedTunnelAirBlocks > 0
            || protectedStructureConflicts > 0
            || unresolvedClearanceBlocks > 0
            || postMergeProtectedLosses > 0
            || postMergeSurfaceSemanticMismatches > 0
            || disconnectedTunnelComponents > 0
            || crossLayerLeaks > 0;
    }

    public JunctionConstructionReport withLocalSurface(
            int expectedSurfaceColumns,
            int missingSurfaceColumns,
            int unresolvedClearanceBlocks) {
        return new JunctionConstructionReport(
            nodeId,
            expectedSurfaceColumns,
            missingSurfaceColumns,
            blockedTunnelAirBlocks,
            protectedStructureConflicts,
            unresolvedClearanceBlocks,
            tunnelConnectivityApplied,
            postMergeMissingSurfaceColumns,
            postMergeSurfaceSemanticMismatches,
            postMergeProtectedLosses,
            disconnectedTunnelComponents,
            crossLayerLeaks);
    }

    public JunctionConstructionReport withTunnelConnectivity(
            int blockedTunnelAirBlocks,
            int protectedStructureConflicts,
            boolean tunnelConnectivityApplied,
            int disconnectedTunnelComponents) {
        return new JunctionConstructionReport(
            nodeId,
            expectedSurfaceColumns,
            missingSurfaceColumns,
            blockedTunnelAirBlocks,
            protectedStructureConflicts,
            unresolvedClearanceBlocks,
            tunnelConnectivityApplied,
            postMergeMissingSurfaceColumns,
            postMergeSurfaceSemanticMismatches,
            postMergeProtectedLosses,
            disconnectedTunnelComponents,
            crossLayerLeaks);
    }

    public JunctionConstructionReport withPostMerge(
            int postMergeMissingSurfaceColumns,
            int postMergeSurfaceSemanticMismatches,
            int postMergeProtectedLosses,
            int disconnectedTunnelComponents,
            int crossLayerLeaks) {
        return new JunctionConstructionReport(
            nodeId,
            expectedSurfaceColumns,
            missingSurfaceColumns,
            blockedTunnelAirBlocks,
            protectedStructureConflicts,
            unresolvedClearanceBlocks,
            tunnelConnectivityApplied,
            postMergeMissingSurfaceColumns,
            postMergeSurfaceSemanticMismatches,
            postMergeProtectedLosses,
            disconnectedTunnelComponents,
            crossLayerLeaks);
    }

    public static JunctionConstructionReport merge(
            JunctionConstructionReport base,
            JunctionConstructionReport overlay) {
        if (base == null) {
            return overlay != null ? overlay : empty("");
        }
        if (overlay == null) {
            return base;
        }
        String nodeId = base.nodeId() != null && !base.nodeId().isBlank()
            ? base.nodeId()
            : overlay.nodeId();
        return new JunctionConstructionReport(
            nodeId,
            Math.max(base.expectedSurfaceColumns(), overlay.expectedSurfaceColumns()),
            Math.max(base.missingSurfaceColumns(), overlay.missingSurfaceColumns()),
            Math.max(base.blockedTunnelAirBlocks(), overlay.blockedTunnelAirBlocks()),
            Math.max(base.protectedStructureConflicts(), overlay.protectedStructureConflicts()),
            Math.max(base.unresolvedClearanceBlocks(), overlay.unresolvedClearanceBlocks()),
            base.tunnelConnectivityApplied() || overlay.tunnelConnectivityApplied(),
            Math.max(base.postMergeMissingSurfaceColumns(), overlay.postMergeMissingSurfaceColumns()),
            Math.max(base.postMergeSurfaceSemanticMismatches(), overlay.postMergeSurfaceSemanticMismatches()),
            Math.max(base.postMergeProtectedLosses(), overlay.postMergeProtectedLosses()),
            Math.max(base.disconnectedTunnelComponents(), overlay.disconnectedTunnelComponents()),
            Math.max(base.crossLayerLeaks(), overlay.crossLayerLeaks()));
    }
}
