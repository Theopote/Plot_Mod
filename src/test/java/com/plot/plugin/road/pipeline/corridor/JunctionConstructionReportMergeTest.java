package com.plot.plugin.road.pipeline.corridor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionConstructionReportMergeTest {

    @Test
    void finalAcceptanceOverwritesProcessDisconnectedCount() {
        JunctionConstructionReport process = JunctionConstructionReport.empty("n1")
            .withTunnelConnectivity(3, 0, true, 2);
        assertEquals(2, process.processDisconnectedTunnelComponents());
        assertEquals(0, process.disconnectedTunnelComponents());
        assertFalse(process.hasBlockingPostMergeIssues());

        JunctionConstructionReport finalClean = JunctionConstructionReport.empty("n1")
            .withPostMerge(0, 0, 0, 0, 0, 0, 0);
        JunctionConstructionReport merged = JunctionConstructionReport.merge(process, finalClean);

        assertEquals(2, merged.processDisconnectedTunnelComponents(),
            "process diagnostics keep historical peak");
        assertEquals(0, merged.disconnectedTunnelComponents(),
            "final acceptance replaces disconnected count");
        assertTrue(merged.finalAcceptanceApplied());
        assertFalse(merged.hasBlockingPostMergeIssues());
    }

    @Test
    void finalAcceptanceBlockingSurvivesLaterDiagnosticMerge() {
        JunctionConstructionReport finalBad = JunctionConstructionReport.empty("n1")
            .withPostMerge(2, 0, 1, 1, 0, 0, 0);
        JunctionConstructionReport laterDiag = JunctionConstructionReport.empty("n1")
            .withTunnelConnectivity(1, 0, true, 0);
        JunctionConstructionReport merged = JunctionConstructionReport.merge(finalBad, laterDiag);

        assertTrue(merged.hasBlockingPostMergeIssues());
        assertEquals(2, merged.postMergeMissingSurfaceColumns());
        assertEquals(1, merged.disconnectedTunnelComponents());
    }
}
