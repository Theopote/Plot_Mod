package com.plot.plugin.road.vertical;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * 纵断面工程验证：桩号单调性、竖曲线重叠与越界。
 */
public final class VerticalAlignmentValidator {

    private static final double EPSILON = 1e-6;

    private VerticalAlignmentValidator() {
    }

    public record VerticalCurveInterval(int pviIndex, double bvcStation, double evcStation) {
    }

    public static List<VerticalAlignmentViolation> validate(RoadVerticalAlignment alignment) {
        return validate(alignment, OptionalDouble.empty());
    }

    public static List<VerticalAlignmentViolation> validate(
            RoadVerticalAlignment alignment,
            double roadLength) {
        return validate(alignment, OptionalDouble.of(roadLength));
    }

    public static List<VerticalAlignmentViolation> validate(
            RoadVerticalAlignment alignment,
            OptionalDouble roadLength) {
        if (alignment == null || alignment.isEmpty()) {
            return List.of();
        }

        List<VerticalAlignmentViolation> violations = new ArrayList<>();
        List<PointOfVerticalIntersection> pvis = alignment.getPvis();

        validateStationOrder(pvis, violations);
        if (!violations.isEmpty()) {
            return violations;
        }

        double rangeStart = pvis.getFirst().getStation();
        double rangeEnd = roadLength.isPresent()
            ? roadLength.getAsDouble()
            : pvis.getLast().getStation();

        List<VerticalCurveInterval> curves = curveIntervals(pvis);
        validateCurveRanges(curves, rangeStart, rangeEnd, violations);
        validateCurveOverlaps(curves, violations);
        return violations;
    }

    public static boolean isEvaluable(RoadVerticalAlignment alignment) {
        return alignment != null
            && alignment.hasStrictlyIncreasingStorageOrder()
            && alignment.pviCount() >= 2
            && validate(alignment).isEmpty();
    }

    public static List<VerticalCurveInterval> curveIntervals(List<PointOfVerticalIntersection> pvis) {
        if (pvis == null || pvis.size() < 3) {
            return List.of();
        }
        List<VerticalCurveInterval> intervals = new ArrayList<>();
        for (int i = 1; i < pvis.size() - 1; i++) {
            PointOfVerticalIntersection pvi = pvis.get(i);
            if (!pvi.hasCurve()) {
                continue;
            }
            double half = pvi.getCurveLength() * 0.5;
            intervals.add(new VerticalCurveInterval(
                i,
                pvi.getStation() - half,
                pvi.getStation() + half));
        }
        return intervals;
    }

    private static void validateStationOrder(
            List<PointOfVerticalIntersection> pvis,
            List<VerticalAlignmentViolation> violations) {
        for (int i = 1; i < pvis.size(); i++) {
            double previous = pvis.get(i - 1).getStation();
            double current = pvis.get(i).getStation();
            if (Math.abs(current - previous) <= EPSILON) {
                violations.add(VerticalAlignmentViolation.between(
                    VerticalAlignmentViolationKind.PVI_STATION_DUPLICATE,
                    i - 1,
                    i));
                return;
            }
            if (current < previous) {
                violations.add(VerticalAlignmentViolation.between(
                    VerticalAlignmentViolationKind.PVI_STATION_NOT_INCREASING,
                    i - 1,
                    i));
                return;
            }
        }
    }

    private static void validateCurveRanges(
            List<VerticalCurveInterval> curves,
            double rangeStart,
            double rangeEnd,
            List<VerticalAlignmentViolation> violations) {
        for (VerticalCurveInterval curve : curves) {
            if (curve.bvcStation() < rangeStart - EPSILON
                    || curve.evcStation() > rangeEnd + EPSILON) {
                violations.add(VerticalAlignmentViolation.of(
                    VerticalAlignmentViolationKind.VERTICAL_CURVE_OUT_OF_RANGE,
                    curve.pviIndex()));
            }
        }
    }

    private static void validateCurveOverlaps(
            List<VerticalCurveInterval> curves,
            List<VerticalAlignmentViolation> violations) {
        for (int i = 1; i < curves.size(); i++) {
            VerticalCurveInterval previous = curves.get(i - 1);
            VerticalCurveInterval current = curves.get(i);
            if (previous.evcStation() > current.bvcStation() + EPSILON) {
                violations.add(VerticalAlignmentViolation.between(
                    VerticalAlignmentViolationKind.VERTICAL_CURVE_OVERLAP,
                    previous.pviIndex(),
                    current.pviIndex()));
            }
        }
    }
}
