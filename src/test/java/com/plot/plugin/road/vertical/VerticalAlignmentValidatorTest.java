package com.plot.plugin.road.vertical;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalAlignmentValidatorTest {

    @Test
    void detectsOverlappingVerticalCurves() {
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.withCurve(100.0, 110.0, 120.0),
            PointOfVerticalIntersection.withCurve(150.0, 105.0, 120.0),
            PointOfVerticalIntersection.of(200.0, 90.0)));

        List<VerticalAlignmentViolation> violations =
            VerticalAlignmentValidator.validate(alignment, 220.0);

        assertEquals(1, violations.size());
        assertEquals(VerticalAlignmentViolationKind.VERTICAL_CURVE_OVERLAP, violations.getFirst().kind());
        assertEquals(1, violations.getFirst().pviIndex());
        assertEquals(2, violations.getFirst().relatedPviIndex());
        assertFalse(VerticalAlignmentValidator.isEvaluable(alignment));
    }

    @Test
    void detectsCurveOutOfRange() {
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.withCurve(20.0, 100.0, 60.0),
            PointOfVerticalIntersection.of(100.0, 90.0)));

        List<VerticalAlignmentViolation> violations =
            VerticalAlignmentValidator.validate(alignment, 100.0);

        assertEquals(1, violations.size());
        assertEquals(VerticalAlignmentViolationKind.VERTICAL_CURVE_OUT_OF_RANGE, violations.getFirst().kind());
        assertEquals(1, violations.getFirst().pviIndex());
    }

    @Test
    void detectsDuplicatePviStation() {
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.of(50.0, 100.0),
            PointOfVerticalIntersection.of(50.0, 95.0),
            PointOfVerticalIntersection.of(100.0, 90.0)));

        List<VerticalAlignmentViolation> violations = VerticalAlignmentValidator.validate(alignment);

        assertEquals(1, violations.size());
        assertEquals(VerticalAlignmentViolationKind.PVI_STATION_DUPLICATE, violations.getFirst().kind());
        assertEquals(1, violations.getFirst().pviIndex());
        assertEquals(2, violations.getFirst().relatedPviIndex());
    }

    @Test
    void detectsStorageOrderNotIncreasing() {
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.of(100.0, 100.0),
            PointOfVerticalIntersection.of(60.0, 95.0),
            PointOfVerticalIntersection.of(120.0, 90.0)));

        List<VerticalAlignmentViolation> violations = VerticalAlignmentValidator.validate(alignment);

        assertEquals(1, violations.size());
        assertEquals(VerticalAlignmentViolationKind.PVI_STATION_NOT_INCREASING, violations.getFirst().kind());
        assertEquals(1, violations.getFirst().pviIndex());
        assertEquals(2, violations.getFirst().relatedPviIndex());
        assertFalse(alignment.hasStrictlyIncreasingStorageOrder());
        assertTrue(alignment.sortedPvis().isEmpty());
    }

    @Test
    void acceptsValidAlignment() {
        RoadVerticalAlignment alignment = new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.withCurve(100.0, 110.0, 40.0),
            PointOfVerticalIntersection.of(200.0, 90.0)));

        assertTrue(VerticalAlignmentValidator.validate(alignment, 200.0).isEmpty());
        assertTrue(VerticalAlignmentValidator.isEvaluable(alignment));
    }
}
