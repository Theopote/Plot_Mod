package com.plot.plugin.road.vertical;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VerticalProfileAutoFixerTest {
    @Test void extendsBothRunsAroundFixedHighPoint() {
        RoadVerticalAlignment source = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(100, 70),
            PointOfVerticalIntersection.withCurve(120, 78, 8),
            PointOfVerticalIntersection.of(140, 70)));
        VerticalProfileAutoFixer.Result result =
            VerticalProfileAutoFixer.extendAdjacentRuns(source, 1, 240, 8);
        assertTrue(result.changed());
        assertTrue(result.fullyResolved());
        assertEquals(20, result.alignment().getPvis().get(0).getStation(), 1e-6);
        assertEquals(220, result.alignment().getPvis().get(2).getStation(), 1e-6);
        assertEquals(8, result.alignment().getPvis().get(1).getCurveLength(), 1e-6);
    }

    @Test void reportsWhenRoadBoundaryCannotProvideRequiredRun() {
        RoadVerticalAlignment source = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(20, 80)));
        VerticalProfileAutoFixer.Result result =
            VerticalProfileAutoFixer.extendAdjacentRuns(source, 1, 20, 8);
        assertFalse(result.fullyResolved());
        assertFalse(result.leftWithinLimit());
    }

    @Test void endpointAnchorMovesOnlyItsAdjacentFreePoint() {
        RoadVerticalAlignment source = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(80, 72),
            PointOfVerticalIntersection.of(100, 80)));
        VerticalProfileAutoFixer.Result result =
            VerticalProfileAutoFixer.extendAdjacentRuns(source, 2, 100, 8);
        assertEquals(0, result.alignment().getPvis().get(0).getStation(), 1e-6);
        assertEquals(12, result.alignment().getPvis().get(1).getStation(), 1e-6);
        assertFalse(result.leftWithinLimit());
    }

    @Test void doesNotMovePersistentlyConstrainedNeighbor() {
        RoadVerticalAlignment source = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(20, 70)
                .withConstraint(VerticalControlPointConstraint.USER_LOCKED),
            PointOfVerticalIntersection.of(40, 80)));

        VerticalProfileAutoFixer.Result result =
            VerticalProfileAutoFixer.extendAdjacentRuns(source, 1, 200, 8);

        assertFalse(result.changed());
        assertEquals(20, result.alignment().getPvis().getFirst().getStation(), 1e-6);
        assertFalse(result.leftWithinLimit());
    }
}
