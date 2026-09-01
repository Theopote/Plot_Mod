package com.plot.plugin.road.vertical;

import java.util.ArrayList;
import java.util.List;

/** Minecraft-oriented vertical profile rules. Distances are centerline blocks. */
public final class VerticalProfileDesignRules {
    public static final double MIN_ROAD_LENGTH_FOR_SLOPE = 20.0;
    public static final double MIN_GRADE_RUN_LENGTH = 12.0;
    public static final double MIN_VERTICAL_TRANSITION_LENGTH = 8.0;
    public static final double WARNING_CONTINUOUS_GRADE_LENGTH = 300.0;
    public static final double MIN_MEANINGFUL_ELEVATION_CHANGE = 1.0;
    private static final double EPSILON = 1e-6;

    public enum IssueKind {
        SHORT_ROAD_MUST_BE_FLAT,
        GRADE_EXCEEDS_LIMIT,
        GRADE_RUN_TOO_SHORT,
        CONTINUOUS_GRADE_TOO_LONG,
        ELEVATION_CHANGE_NOT_VISIBLE
    }

    public record Issue(IssueKind kind, int fromPviIndex, int toPviIndex, double actual, double limit) { }

    private VerticalProfileDesignRules() { }

    public static boolean slopeAllowed(double roadLength) {
        return Double.isFinite(roadLength) && roadLength + EPSILON >= MIN_ROAD_LENGTH_FOR_SLOPE;
    }

    /** Minimum distance needed to overcome a height difference at the grade limit. */
    public static double requiredRunLength(double elevationDifference, double maxGradePercent) {
        if (!Double.isFinite(elevationDifference) || !Double.isFinite(maxGradePercent)
                || maxGradePercent <= EPSILON) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.abs(elevationDifference) / (maxGradePercent / 100.0);
    }

    /** Conservative symmetric transition length that fits both adjacent tangents. */
    public static double suggestedTransitionLength(double incomingRun, double outgoingRun) {
        double available = 2.0 * Math.max(0.0, Math.min(incomingRun, outgoingRun));
        if (available + EPSILON < MIN_VERTICAL_TRANSITION_LENGTH) {
            return 0.0;
        }
        return Math.min(available, Math.max(MIN_VERTICAL_TRANSITION_LENGTH, available * 0.5));
    }

    public static RoadVerticalAlignment flatAlignment(double roadLength, double elevation) {
        if (!Double.isFinite(roadLength) || roadLength <= EPSILON || !Double.isFinite(elevation)) {
            throw new IllegalArgumentException("roadLength must be positive and elevation must be finite");
        }
        return new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, elevation),
            PointOfVerticalIntersection.of(roadLength, elevation)));
    }

    public static boolean isFlat(RoadVerticalAlignment alignment) {
        if (alignment == null || alignment.pviCount() < 2) {
            return false;
        }
        double elevation = alignment.getPvis().getFirst().getElevation();
        return alignment.getPvis().stream()
            .allMatch(pvi -> Math.abs(pvi.getElevation() - elevation) <= EPSILON);
    }

    public static List<Issue> assess(RoadVerticalAlignment alignment, double roadLength,
            double maxGradePercent) {
        if (alignment == null || alignment.pviCount() < 2
                || !alignment.hasStrictlyIncreasingStorageOrder()) {
            return List.of();
        }
        List<PointOfVerticalIntersection> pvis = alignment.getPvis();
        List<Issue> issues = new ArrayList<>();
        for (int i = 1; i < pvis.size(); i++) {
            PointOfVerticalIntersection from = pvis.get(i - 1);
            PointOfVerticalIntersection to = pvis.get(i);
            double run = to.getStation() - from.getStation();
            double elevationChange = Math.abs(to.getElevation() - from.getElevation());
            double grade = Math.abs(VerticalAlignmentGeometry.tangentGradePercent(from, to));
            if (elevationChange > EPSILON
                    && elevationChange + EPSILON < MIN_MEANINGFUL_ELEVATION_CHANGE) {
                issues.add(new Issue(IssueKind.ELEVATION_CHANGE_NOT_VISIBLE, i - 1, i,
                    elevationChange, MIN_MEANINGFUL_ELEVATION_CHANGE));
            }
            if (!slopeAllowed(roadLength) && grade > EPSILON) {
                issues.add(new Issue(IssueKind.SHORT_ROAD_MUST_BE_FLAT, i - 1, i,
                    roadLength, MIN_ROAD_LENGTH_FOR_SLOPE));
            }
            if (grade > EPSILON && run + EPSILON < MIN_GRADE_RUN_LENGTH) {
                issues.add(new Issue(IssueKind.GRADE_RUN_TOO_SHORT, i - 1, i,
                    run, MIN_GRADE_RUN_LENGTH));
            }
            if (maxGradePercent > EPSILON && grade > maxGradePercent + EPSILON) {
                issues.add(new Issue(IssueKind.GRADE_EXCEEDS_LIMIT, i - 1, i,
                    grade, maxGradePercent));
            }
            if (grade > EPSILON && run > WARNING_CONTINUOUS_GRADE_LENGTH + EPSILON) {
                issues.add(new Issue(IssueKind.CONTINUOUS_GRADE_TOO_LONG, i - 1, i,
                    run, WARNING_CONTINUOUS_GRADE_LENGTH));
            }
        }
        return List.copyOf(issues);
    }
}
