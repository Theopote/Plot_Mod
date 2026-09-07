package com.plot.plugin.building.golden;

import com.plot.plugin.building.generation.DistrictGenerationResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Semantic Acceptance：片区 Massing 场景断言。 */
final class DistrictScenarioAssertions {
    private DistrictScenarioAssertions() {
    }

    static void assertAllGenerated(DistrictGenerationResult district, int expected) {
        assertEquals(expected, district.buildingsGenerated(), "generated count");
        assertEquals(0, district.buildingsSkipped(), "skipped count");
        assertEquals(expected, district.buildingsAttempted(), "attempted count");
        assertTrue(district.hasPlacements(), "must have placements");
    }

    static void assertFailSoftPartial(
            DistrictGenerationResult district,
            int expectedGenerated,
            int expectedSkipped,
            int expectedAttempted) {
        assertEquals(expectedGenerated, district.buildingsGenerated());
        assertEquals(expectedSkipped, district.buildingsSkipped());
        assertEquals(expectedAttempted, district.buildingsAttempted());
        assertTrue(district.warnings().contains("plugin.building.warn.district_partial"));
    }
}
