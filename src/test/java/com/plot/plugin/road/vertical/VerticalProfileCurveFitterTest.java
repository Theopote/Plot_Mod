package com.plot.plugin.road.vertical;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VerticalProfileCurveFitterTest {
    @Test void fitsCurveWithinAdjacentRuns() {
        RoadVerticalAlignment source = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(40, 76),
            PointOfVerticalIntersection.of(100, 72)));
        VerticalProfileCurveFitter.Result result = VerticalProfileCurveFitter.fitAt(source, 1);
        assertTrue(result.hasSpace());
        assertEquals(40, result.alignment().getPvis().get(1).getCurveLength(), 1e-6);
        assertTrue(VerticalAlignmentValidator.validate(result.alignment(), 100).isEmpty());
    }

    @Test void refusesTransitionWhenMinimumCannotFit() {
        RoadVerticalAlignment source = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 70),
            PointOfVerticalIntersection.of(3, 71),
            PointOfVerticalIntersection.of(6, 70)));
        VerticalProfileCurveFitter.Result result = VerticalProfileCurveFitter.fitAt(source, 1);
        assertFalse(result.hasSpace());
        assertFalse(result.changed());
    }
}
