package com.plot.plugin.building.realistic;

import com.plot.plugin.building.generation.DistrictGenerationResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D-B19 mixed-district 回归断言（CI 门禁与场景测试共用）。 */
final class MixedDistrictRegressionAssertions {
    static final int EXPECTED_ATTEMPTED = 100;
    static final int EXPECTED_GENERATED = 97;
    static final int EXPECTED_SKIPPED = 3;
    static final int EXPECTED_INVALID_SKIPPED = 3;

    private MixedDistrictRegressionAssertions() {
    }

    static void assertBaseline(
            DistrictGenerationResult district,
            RealisticDistrictGapReport.Summary report) {
        assertEquals(EXPECTED_ATTEMPTED, report.attempted(), report::format);
        assertEquals(EXPECTED_GENERATED, report.generated(), report::format);
        assertEquals(EXPECTED_SKIPPED, report.skipped(), report::format);
        assertEquals(
            EXPECTED_INVALID_SKIPPED,
            report.skipByReason().get(DistrictGenerationResult.SkipReason.INVALID));
        assertTrue(district.totalBlocks() > 0);
        assertTrue(district.hasPlacements());
        assertTrue(district.hasBuildingOverlap(), "overlap pairs should be detected");

        RealisticDistrictGapReport.CategoryRow ellipseRow =
            report.byKind().get(RealisticFootprintKind.ELLIPSE);
        assertNotNull(ellipseRow);
        assertEquals(ellipseRow.attempted(), ellipseRow.generated());
        assertTrue(ellipseRow.generated() >= 8);

        RealisticDistrictGapReport.CategoryRow narrowRow =
            report.byKind().get(RealisticFootprintKind.NARROW_INNER_OFFSET);
        assertNotNull(narrowRow);
        assertEquals(4, narrowRow.attempted());
        assertEquals(4, narrowRow.generated());
        assertTrue(narrowRow.innerOffsetWarnings() >= 4);
        assertTrue(narrowRow.roofDowngrades() >= 4);

        RealisticDistrictGapReport.CategoryRow invalidRow =
            report.byKind().get(RealisticFootprintKind.INVALID);
        assertNotNull(invalidRow);
        assertEquals(3, invalidRow.attempted());
        assertEquals(0, invalidRow.generated());
        assertEquals(3, invalidRow.skipped());
    }
}
