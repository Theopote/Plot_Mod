package com.plot.plugin.road;

import com.plot.plugin.road.crossing.CrossingMaterializationFailure;
import com.plot.plugin.road.crossing.CrossingMaterializationReport;
import com.plot.plugin.road.crossing.CrossingMaterializeStatus;
import com.plot.plugin.road.pipeline.corridor.JunctionConstructionReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Package-local coverage for generation integrity flags used to block final build.
 */
class NetworkGenerationIntegrityResultTest {

    @Test
    void crossingMaterializationFailureMarksPartialFailure() {
        var result = new RoadNetworkGenerator.NetworkGenerationResult();
        assertFalse(result.hasPartialFailure());
        result.recordCrossingMaterialization(new CrossingMaterializationReport(
            1,
            0,
            List.of(new CrossingMaterializationFailure(
                "broken",
                "roadA",
                "ghost",
                CrossingMaterializeStatus.SUCCESS,
                CrossingMaterializeStatus.ROAD_NOT_FOUND,
                "crossing arms incomplete"))));
        assertTrue(result.hasPartialFailure());
        assertFalse(result.getCrossingMaterializationFailures().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("crossing broken")));
    }

    @Test
    void missingJunctionValidationMarksPartialFailure() {
        var result = new RoadNetworkGenerator.NetworkGenerationResult();
        assertFalse(result.hasPartialFailure());
        result.recordMissingJunctionValidation(
            "n1", "expected junction has no generation/validation result");
        assertTrue(result.hasPartialFailure());
        assertTrue(result.getMissingJunctionValidationIds().contains("n1"));
        assertTrue(result.getErrors().getFirst().contains("n1"));
        assertTrue(result.partialFailureDetails().stream()
            .anyMatch(detail -> detail.contains("missingJunctionValidation")));
    }

    @Test
    void postMergeBlockingIssuesMarkPartialFailureWhileAdvisoryDoesNot() {
        var result = new RoadNetworkGenerator.NetworkGenerationResult();
        assertFalse(result.hasPartialFailure());

        JunctionConstructionReport advisory = JunctionConstructionReport.empty("n-advisory")
            .withPostMerge(0, 2, 0, 0, 1, 0, 1);
        result.mergeJunctionReport(advisory);
        result.publishJunctionPostMergeDiagnostics(advisory);
        assertFalse(result.hasPartialFailure(), "advisory postMerge must not block build");
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("postMerge advisory")));

        JunctionConstructionReport blocking = JunctionConstructionReport.empty("n-block")
            .withPostMerge(3, 0, 1, 2, 0, 0, 0);
        result.mergeJunctionReport(blocking);
        result.publishJunctionPostMergeDiagnostics(blocking);
        assertTrue(result.hasPartialFailure());
        assertTrue(result.partialFailureDetails().stream()
            .anyMatch(detail -> detail.contains("postMergeBlocking") && detail.contains("n-block")));
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("postMerge blocking")));
    }

    @Test
    void surfaceYMismatchBlocksBuildAsCriticalElevationBreak() {
        var result = new RoadNetworkGenerator.NetworkGenerationResult();
        JunctionConstructionReport yBreak = JunctionConstructionReport.empty("n-y")
            .withPostMerge(0, 0, 0, 0, 0, 2, 0);
        result.mergeJunctionReport(yBreak);
        result.publishJunctionPostMergeDiagnostics(yBreak);
        assertTrue(result.hasPartialFailure());
        assertTrue(yBreak.hasBlockingPostMergeIssues());
        assertFalse(yBreak.hasAdvisoryPostMergeIssues());
    }

    @Test
    void finalPlacementAcceptanceFailureMarksPartialFailure() {
        var result = new RoadNetworkGenerator.NetworkGenerationResult();
        assertFalse(result.hasPartialFailure());
        result.recordFinalPlacementAcceptance(
            new com.plot.plugin.road.pipeline.corridor.FinalPlacementAcceptanceValidator.Report(
                2, 0, 1, 0));
        assertTrue(result.hasPartialFailure());
        assertTrue(result.hasFinalPlacementAcceptanceFailure());
        assertTrue(result.partialFailureDetails().stream()
            .anyMatch(detail -> detail.contains("finalPlacementAcceptanceFailed")));
    }

    @Test
    void repairedTunnelConnectivityDoesNotKeepBlockingDisconnectedCount() {
        var result = new RoadNetworkGenerator.NetworkGenerationResult();
        result.mergeJunctionReport(JunctionConstructionReport.empty("n1")
            .withTunnelConnectivity(1, 0, true, 2));
        result.mergeJunctionReport(JunctionConstructionReport.empty("n1")
            .withPostMerge(0, 0, 0, 0, 0, 0, 0));
        JunctionConstructionReport merged = result.getJunctionReports().get("n1");
        assertEquals(2, merged.processDisconnectedTunnelComponents());
        assertEquals(0, merged.disconnectedTunnelComponents());
        assertFalse(merged.hasBlockingPostMergeIssues());
        assertFalse(result.hasPartialFailure());
    }
}
