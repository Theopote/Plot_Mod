package com.plot.plugin.road;

import com.plot.plugin.road.crossing.CrossingMaterializationFailure;
import com.plot.plugin.road.crossing.CrossingMaterializationReport;
import com.plot.plugin.road.crossing.CrossingMaterializeStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    }
}
