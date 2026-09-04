package com.plot.plugin.building.golden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GoldenBuildingAssertions {
    private GoldenBuildingAssertions() {
    }

    static void assertMetrics(
            String caseId,
            GoldenBuildingMetrics expected,
            GoldenBuildingMetrics actual,
            GoldenBuildingTolerance tolerance) {
        int tTotal = tolerance.totalBlocks();
        int tCat = tolerance.categoryBlocks();
        int tVol = tolerance.volume();
        int tBounds = tolerance.bounds();

        assertEquals(expected.totalBlocks(), actual.totalBlocks(), tTotal,
            caseId + " totalBlocks");
        assertEquals(expected.wallBlocks(), actual.wallBlocks(), tCat,
            caseId + " wallBlocks");
        assertEquals(expected.floorBlocks(), actual.floorBlocks(), tCat,
            caseId + " floorBlocks");
        assertEquals(expected.roofBlocks(), actual.roofBlocks(), tCat,
            caseId + " roofBlocks");
        assertEquals(expected.foundationBlocks(), actual.foundationBlocks(), tCat,
            caseId + " foundationBlocks");
        assertEquals(expected.openingBlocks(), actual.openingBlocks(), tCat,
            caseId + " openingBlocks");
        assertEquals(expected.otherBlocks(), actual.otherBlocks(), tCat,
            caseId + " otherBlocks");
        assertEquals(expected.cutVolume(), actual.cutVolume(), tVol,
            caseId + " cutVolume");
        assertEquals(expected.fillVolume(), actual.fillVolume(), tVol,
            caseId + " fillVolume");

        assertEquals(expected.minX(), actual.minX(), tBounds, caseId + " minX");
        assertEquals(expected.maxX(), actual.maxX(), tBounds, caseId + " maxX");
        assertEquals(expected.minY(), actual.minY(), tBounds, caseId + " minY");
        assertEquals(expected.maxY(), actual.maxY(), tBounds, caseId + " maxY");
        assertEquals(expected.minZ(), actual.minZ(), tBounds, caseId + " minZ");
        assertEquals(expected.maxZ(), actual.maxZ(), tBounds, caseId + " maxZ");

        assertEquals(expected.effectiveRoofType(), actual.effectiveRoofType(),
            caseId + " effectiveRoofType");

        if (expected.warnings().isEmpty()) {
            assertTrue(actual.warnings().isEmpty(),
                caseId + " expected no warnings but got " + actual.warnings());
        } else {
            for (String warning : expected.warnings()) {
                assertTrue(actual.warnings().contains(warning),
                    caseId + " missing warning " + warning + " in " + actual.warnings());
            }
        }
    }
}
