package com.plot.plugin.road.golden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RoadGoldenAssertions {
    private RoadGoldenAssertions() {
    }

    static void assertMetrics(
            String caseId,
            RoadGoldenMetrics expected,
            RoadGoldenMetrics actual,
            RoadGoldenTolerance tolerance) {
        int tBlocks = tolerance.blockCounts();
        int tVol = tolerance.volumes();
        int tCat = tolerance.categoryBlocks();

        assertEquals(expected.surfaceBlocks(), actual.surfaceBlocks(), tBlocks, caseId + " surfaceBlocks");
        assertEquals(expected.fillBlocks(), actual.fillBlocks(), tVol, caseId + " fillBlocks");
        assertEquals(expected.cutBlocks(), actual.cutBlocks(), tVol, caseId + " cutBlocks");
        assertEquals(expected.markingBlocks(), actual.markingBlocks(), tCat, caseId + " markingBlocks");
        assertEquals(expected.bridgeBlocks(), actual.bridgeBlocks(), tCat, caseId + " bridgeBlocks");
        assertEquals(expected.tunnelBlocks(), actual.tunnelBlocks(), tCat, caseId + " tunnelBlocks");
        assertEquals(expected.junctionBlocks(), actual.junctionBlocks(), tCat, caseId + " junctionBlocks");
        assertEquals(expected.placementRecords(), actual.placementRecords(), tBlocks, caseId + " placementRecords");
        assertEquals(expected.bridgeCount(), actual.bridgeCount(), tCat, caseId + " bridgeCount");
        assertEquals(expected.tunnelCount(), actual.tunnelCount(), tCat, caseId + " tunnelCount");

        if (expected.warnings().isEmpty()) {
            assertTrue(actual.warnings().isEmpty(),
                caseId + " expected no warnings but got " + actual.warnings());
        } else {
            for (String warning : expected.warnings()) {
                assertTrue(actual.warnings().contains(warning),
                    caseId + " missing warning " + warning);
            }
        }
    }
}
