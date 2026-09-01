package com.plot.plugin.road.vertical;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VerticalProfileDesignRulesTest {
    @Test void computesRequiredSlopeRun() {
        assertEquals(100.0, VerticalProfileDesignRules.requiredRunLength(8.0, 8.0), 1e-6);
        assertTrue(Double.isInfinite(VerticalProfileDesignRules.requiredRunLength(8.0, 0.0)));
    }

    @Test void createsHorizontalProfile() {
        RoadVerticalAlignment alignment = VerticalProfileDesignRules.flatAlignment(18.0, 72.0);
        assertEquals(2, alignment.pviCount());
        assertEquals(72.0, alignment.getPvis().getFirst().getElevation(), 1e-6);
        assertEquals(72.0, alignment.getPvis().getLast().getElevation(), 1e-6);
    }

    @Test void shortRoadRejectsSlopeButAcceptsFlat() {
        RoadVerticalAlignment sloped = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 70.0),
            PointOfVerticalIntersection.of(18.0, 71.0)));
        assertTrue(VerticalProfileDesignRules.assess(sloped, 18.0, 8.0).stream()
            .anyMatch(i -> i.kind() == VerticalProfileDesignRules.IssueKind.SHORT_ROAD_MUST_BE_FLAT));
        assertTrue(VerticalProfileDesignRules.assess(
            VerticalProfileDesignRules.flatAlignment(18.0, 70.0), 18.0, 8.0).isEmpty());
    }

    @Test void reportsShortSteepAndVeryLongRuns() {
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 70.0),
            PointOfVerticalIntersection.of(10.0, 72.0),
            PointOfVerticalIntersection.of(320.0, 100.0)));
        List<VerticalProfileDesignRules.Issue> issues =
            VerticalProfileDesignRules.assess(alignment, 320.0, 8.0);
        assertTrue(issues.stream().anyMatch(i -> i.kind() == VerticalProfileDesignRules.IssueKind.GRADE_RUN_TOO_SHORT));
        assertTrue(issues.stream().anyMatch(i -> i.kind() == VerticalProfileDesignRules.IssueKind.GRADE_EXCEEDS_LIMIT));
        assertTrue(issues.stream().anyMatch(i -> i.kind() == VerticalProfileDesignRules.IssueKind.CONTINUOUS_GRADE_TOO_LONG));
    }
}
