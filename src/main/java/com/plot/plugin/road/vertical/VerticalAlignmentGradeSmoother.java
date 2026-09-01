package com.plot.plugin.road.vertical;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.List;

/**
 * 纵断面坡度自动平缓：在保留起点桩号/标高的前提下，调整 PVI 标高使采样坡度不超过限值。
 * <p>
 * 与 {@link com.plot.plugin.road.RoadNetworkEngineeringValidator} 使用相同的采样步长与容差。
 */
public final class VerticalAlignmentGradeSmoother {

    static final double SAMPLE_SPACING = 5.0;
    private static final float GRADE_TOLERANCE_PERCENT = 0.05f;
    private static final double STATION_EPSILON = 1e-9;
    private static final int MAX_ITERATIONS = 64;

    private VerticalAlignmentGradeSmoother() {
    }

    public record Result(RoadVerticalAlignment alignment, boolean changed, boolean withinLimit) {
    }

    public static boolean exceedsGradeLimit(RoadVerticalAlignment alignment, float maxGradePercent) {
        return maxSampledAbsGrade(alignment) > maxGradePercent + GRADE_TOLERANCE_PERCENT;
    }

    /**
     * 平缓纵断面。固定首点桩号与标高；可调整其余 PVI 标高。
     */
    public static Result smooth(RoadVerticalAlignment source, float maxGradePercent) {
        if (!VerticalAlignmentGeometry.isEvaluable(source)) {
            return new Result(source != null ? source.copy() : new RoadVerticalAlignment(), false, false);
        }
        if (!exceedsGradeLimit(source, maxGradePercent)) {
            return new Result(source.copy(), false, true);
        }

        List<PointOfVerticalIntersection> pvis = new ArrayList<>(source.pviCount());
        for (PointOfVerticalIntersection pvi : source.getPvis()) {
            pvis.add(pvi.copy());
        }

        boolean changed = false;
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            boolean iterationChanged = false;
            for (int i = 0; i < pvis.size() - 1; i++) {
                iterationChanged |= clampTangentForward(pvis, i, maxGradePercent);
            }
            for (int i = pvis.size() - 2; i >= 1; i--) {
                iterationChanged |= clampTangentBackward(pvis, i, maxGradePercent);
            }
            changed |= iterationChanged;

            RoadVerticalAlignment trial = new RoadVerticalAlignment(pvis);
            if (!exceedsGradeLimit(trial, maxGradePercent)) {
                return new Result(trial, changed, true);
            }
            if (!iterationChanged) {
                break;
            }
        }

        RoadVerticalAlignment result = new RoadVerticalAlignment(pvis);
        return new Result(result, changed, !exceedsGradeLimit(result, maxGradePercent));
    }

    public static boolean smoothRoad(RoadNetwork network, Road road, RoadSystemConfig config) {
        if (network == null || road == null || config == null) {
            return false;
        }
        RoadVerticalAlignment alignment = road.getVerticalAlignment();
        if (!VerticalAlignmentGeometry.isEvaluable(alignment)
            || !RoadStationing.isStationable(network, road)) {
            return false;
        }
        float limit = road.getEffectiveMaxSlope(config);
        if (!exceedsGradeLimit(alignment, limit)) {
            return false;
        }
        Result result = smooth(alignment, limit);
        if (!result.changed()) {
            return false;
        }
        road.setVerticalAlignment(result.alignment());
        return result.changed();
    }

    public static int smoothAllExceeding(RoadNetwork network, RoadSystemConfig config) {
        if (network == null || config == null) {
            return 0;
        }
        int count = 0;
        for (Road road : network.getRoads().values()) {
            if (smoothRoad(network, road, config)) {
                count++;
            }
        }
        return count;
    }

    private static boolean clampTangentForward(
            List<PointOfVerticalIntersection> pvis,
            int fromIndex,
            float maxGradePercent) {
        return clampTangent(pvis, fromIndex, fromIndex + 1, maxGradePercent, true);
    }

    private static boolean clampTangentBackward(
            List<PointOfVerticalIntersection> pvis,
            int fromIndex,
            float maxGradePercent) {
        return clampTangent(pvis, fromIndex, fromIndex + 1, maxGradePercent, false);
    }

    private static boolean clampTangent(
            List<PointOfVerticalIntersection> pvis,
            int fromIndex,
            int toIndex,
            float maxGradePercent,
            boolean forward) {
        PointOfVerticalIntersection from = pvis.get(fromIndex);
        PointOfVerticalIntersection to = pvis.get(toIndex);
        double deltaStation = to.getStation() - from.getStation();
        if (deltaStation <= STATION_EPSILON) {
            return false;
        }
        double maxDeltaElevation = deltaStation * maxGradePercent / 100.0;
        double deltaElevation = to.getElevation() - from.getElevation();
        if (Math.abs(deltaElevation) <= maxDeltaElevation + GRADE_TOLERANCE_PERCENT * deltaStation / 100.0) {
            return false;
        }
        double clampedDelta = Math.copySign(maxDeltaElevation, deltaElevation);
        if (forward) {
            pvis.set(toIndex, withElevation(to, from.getElevation() + clampedDelta));
        } else {
            pvis.set(fromIndex, withElevation(from, to.getElevation() - clampedDelta));
        }
        return true;
    }

    private static PointOfVerticalIntersection withElevation(
            PointOfVerticalIntersection pvi,
            double elevation) {
        if (pvi.hasCurve()) {
            return PointOfVerticalIntersection.withCurve(
                pvi.getStation(), elevation, pvi.getCurveLength());
        }
        return PointOfVerticalIntersection.of(pvi.getStation(), elevation);
    }

    private static double maxSampledAbsGrade(RoadVerticalAlignment alignment) {
        if (!VerticalAlignmentGeometry.isEvaluable(alignment)) {
            return 0.0;
        }
        double maxAbsGrade = 0.0;
        double end = alignment.endStation();
        for (double station = alignment.startStation();
             station <= end + STATION_EPSILON;
             station += SAMPLE_SPACING) {
            maxAbsGrade = Math.max(
                maxAbsGrade,
                Math.abs(VerticalAlignmentGeometry.gradeAt(alignment, station).orElse(0.0)));
        }
        maxAbsGrade = Math.max(
            maxAbsGrade,
            Math.abs(VerticalAlignmentGeometry.gradeAt(alignment, end).orElse(0.0)));
        return maxAbsGrade;
    }
}
