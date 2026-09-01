package com.plot.plugin.road.vertical;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/** Extends grade runs around a fixed-elevation PVI until they meet the grade limit. */
public final class VerticalProfileAutoFixer {
    private static final double EPSILON = 1e-6;

    public record Result(
            RoadVerticalAlignment alignment,
            boolean changed,
            boolean leftWithinLimit,
            boolean rightWithinLimit) {
        public boolean fullyResolved() {
            return leftWithinLimit && rightWithinLimit;
        }
    }

    private VerticalProfileAutoFixer() { }

    public static Result extendAdjacentRuns(
            RoadVerticalAlignment source,
            int anchorIndex,
            double roadLength,
            double maxGradePercent) {
        return extendAdjacentRuns(source, anchorIndex, roadLength, maxGradePercent, index -> true);
    }

    public static Result extendAdjacentRuns(
            RoadVerticalAlignment source,
            int anchorIndex,
            double roadLength,
            double maxGradePercent,
            IntPredicate movablePvi) {
        if (source == null || !source.hasStrictlyIncreasingStorageOrder()
                || anchorIndex < 0 || anchorIndex >= source.pviCount()
                || maxGradePercent <= EPSILON || roadLength <= EPSILON) {
            return new Result(source != null ? source.copy() : new RoadVerticalAlignment(),
                false, false, false);
        }
        List<PointOfVerticalIntersection> original = source.getPvis();
        List<PointOfVerticalIntersection> edited = new ArrayList<>();
        for (PointOfVerticalIntersection pvi : original) {
            edited.add(pvi.copy());
        }
        PointOfVerticalIntersection anchor = original.get(anchorIndex);

        if (anchorIndex > 0 && movablePvi.test(anchorIndex - 1)) {
            int neighborIndex = anchorIndex - 1;
            PointOfVerticalIntersection neighbor = original.get(neighborIndex);
            double required = Math.max(
                VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH,
                VerticalProfileDesignRules.requiredRunLength(
                    anchor.getElevation() - neighbor.getElevation(), maxGradePercent));
            double desired = anchor.getStation() - required;
            double lowerBound = neighborIndex == 0
                ? 0.0
                : original.get(neighborIndex - 1).getStation()
                    + VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH;
            double upperBound = anchor.getStation()
                - VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH;
            if (lowerBound <= upperBound + EPSILON) {
                double station = Math.max(lowerBound, Math.min(upperBound, desired));
                edited.set(neighborIndex, copyAtStation(neighbor, station));
            }
        }

        if (anchorIndex + 1 < original.size() && movablePvi.test(anchorIndex + 1)) {
            int neighborIndex = anchorIndex + 1;
            PointOfVerticalIntersection neighbor = original.get(neighborIndex);
            double required = Math.max(
                VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH,
                VerticalProfileDesignRules.requiredRunLength(
                    neighbor.getElevation() - anchor.getElevation(), maxGradePercent));
            double desired = anchor.getStation() + required;
            double upperBound = neighborIndex == original.size() - 1
                ? roadLength
                : original.get(neighborIndex + 1).getStation()
                    - VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH;
            double lowerBound = anchor.getStation()
                + VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH;
            if (lowerBound <= upperBound + EPSILON) {
                double station = Math.max(lowerBound, Math.min(upperBound, desired));
                edited.set(neighborIndex, copyAtStation(neighbor, station));
            }
        }

        RoadVerticalAlignment result = new RoadVerticalAlignment(edited);
        boolean leftOk = anchorIndex == 0 || withinLimit(
            edited.get(anchorIndex - 1), edited.get(anchorIndex), maxGradePercent);
        boolean rightOk = anchorIndex == edited.size() - 1 || withinLimit(
            edited.get(anchorIndex), edited.get(anchorIndex + 1), maxGradePercent);
        return new Result(result, !sameStations(original, edited), leftOk, rightOk);
    }

    private static PointOfVerticalIntersection copyAtStation(
            PointOfVerticalIntersection source,
            double station) {
        return new PointOfVerticalIntersection(station, source.getElevation(), source.getCurveLength());
    }

    private static boolean withinLimit(
            PointOfVerticalIntersection from,
            PointOfVerticalIntersection to,
            double maxGradePercent) {
        return Math.abs(VerticalAlignmentGeometry.tangentGradePercent(from, to))
            <= maxGradePercent + EPSILON;
    }

    private static boolean sameStations(
            List<PointOfVerticalIntersection> left,
            List<PointOfVerticalIntersection> right) {
        for (int i = 0; i < left.size(); i++) {
            if (Math.abs(left.get(i).getStation() - right.get(i).getStation()) > EPSILON) {
                return false;
            }
        }
        return true;
    }
}
