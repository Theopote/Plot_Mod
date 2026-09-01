package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;

import java.util.Optional;

/**
 * 比较设计平面线形与 {@link com.plot.plugin.road.model.RoadEdge} 折线中心线的一致性。
 * <p>
 * 沿道路桩号同步采样，度量横向偏差；用于发现「双几何真相」而不静默选边。
 */
public final class HorizontalAlignmentCenterlineConsistency {

    public static final double DEFAULT_LENGTH_TOLERANCE_METERS = 1.0;
    public static final double DEFAULT_POINT_TOLERANCE_METERS = 1.0;
    public static final double DEFAULT_SAMPLE_SPACING_METERS = 5.0;

    private HorizontalAlignmentCenterlineConsistency() {
    }

    public record Report(
            boolean evaluable,
            double roadLengthMeters,
            double alignmentLengthMeters,
            boolean lengthMatches,
            double originDeviationMeters,
            double maxDeviationMeters,
            double meanDeviationMeters,
            int sampleCount) {

        public boolean isConsistent() {
            return isConsistent(DEFAULT_LENGTH_TOLERANCE_METERS, DEFAULT_POINT_TOLERANCE_METERS);
        }

        public boolean isConsistent(double lengthToleranceMeters, double pointToleranceMeters) {
            return evaluable
                && lengthMatches
                && originDeviationMeters <= lengthToleranceMeters
                && maxDeviationMeters <= pointToleranceMeters;
        }
    }

    public static boolean isEvaluable(RoadNetwork network, Road road) {
        if (network == null || road == null) {
            return false;
        }
        RoadHorizontalAlignment alignment = road.getHorizontalAlignment();
        return alignment != null
            && !alignment.isEmpty()
            && RoadStationing.isStationable(network, road);
    }

    public static Report evaluate(RoadNetwork network, Road road) {
        return evaluate(network, road, DEFAULT_SAMPLE_SPACING_METERS, DEFAULT_LENGTH_TOLERANCE_METERS);
    }

    public static Report evaluate(
            RoadNetwork network,
            Road road,
            double sampleSpacingMeters,
            double lengthToleranceMeters) {
        if (!isEvaluable(network, road)) {
            return new Report(false, 0.0, 0.0, false, 0.0, 0.0, 0.0, 0);
        }

        RoadHorizontalAlignment alignment = road.getHorizontalAlignment();
        double roadLength = RoadPlanGeometry.instanceLength(network, road);
        double alignmentLength = RoadPlanGeometry.designLength(network, road);
        boolean lengthMatches = Math.abs(alignmentLength - roadLength) <= lengthToleranceMeters;

        double originDeviation = RoadStationing.chainOrigin(network, road)
            .map(origin -> origin.distance(alignment.getOrigin()))
            .orElse(Double.POSITIVE_INFINITY);

        double spacing = sampleSpacingMeters > 1e-6 ? sampleSpacingMeters : DEFAULT_SAMPLE_SPACING_METERS;
        double maxDeviation = 0.0;
        double deviationSum = 0.0;
        int sampleCount = 0;

        for (double chainage = 0.0; chainage <= roadLength + 1e-6; chainage += spacing) {
            double clamped = Math.min(chainage, roadLength);
            Optional<Vec2d> centerlinePoint = RoadPlanGeometry.instancePointAtStation(network, road, clamped);
            Optional<AlignmentPose> designPose = HorizontalAlignmentGeometry.poseAt(alignment, clamped);
            if (centerlinePoint.isEmpty() || designPose.isEmpty()) {
                continue;
            }
            double deviation = centerlinePoint.get().distance(new Vec2d(designPose.get().x(), designPose.get().y()));
            maxDeviation = Math.max(maxDeviation, deviation);
            deviationSum += deviation;
            sampleCount++;
        }

        double meanDeviation = sampleCount > 0 ? deviationSum / sampleCount : 0.0;
        return new Report(
            true,
            roadLength,
            alignmentLength,
            lengthMatches,
            originDeviation,
            maxDeviation,
            meanDeviation,
            sampleCount);
    }
}
