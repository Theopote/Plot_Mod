package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.station.StationDomainMap;

import java.util.Optional;

/**
 * 比较设计平面线形与 {@link com.plot.plugin.road.model.RoadEdge} 折线中心线的一致性。
 * <p>
 * 沿道路桩号同步采样，度量横向偏差；用于发现「双几何真相」而不静默选边。
 * Compares {@code P_instance(s_i)} with {@code P_HA(M(s_i))} for the same physical pass.
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

    /**
     * 设计线形总长与实例折线链长是否一致到可安全物化（{@link HorizontalAlignmentCenterlineMaterializer}）。
     */
    public static boolean isMaterializable(RoadNetwork network, Road road) {
        return isMaterializable(network, road, DEFAULT_LENGTH_TOLERANCE_METERS);
    }

    public static boolean isMaterializable(
            RoadNetwork network,
            Road road,
            double lengthToleranceMeters) {
        // Materialization eligibility is length-domain only; StationDomainMap reliability
        // is required for consistency reporting, not for writing derived centerlines.
        if (!isEvaluable(network, road)) {
            return false;
        }
        double roadLength = RoadPlanGeometry.instanceLength(network, road);
        double alignmentLength = RoadPlanGeometry.designLength(network, road);
        return Math.abs(alignmentLength - roadLength) <= lengthToleranceMeters;
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

        StationDomainMap map = RoadStationing.domainMap(network, road);
        if (map == null || !map.isReliable()) {
            return new Report(
                false, roadLength, alignmentLength, lengthMatches,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0.0, 0);
        }

        double originDeviation = RoadStationing.chainOrigin(network, road)
            .map(origin -> origin.distance(alignment.getOrigin()))
            .orElse(Double.POSITIVE_INFINITY);

        double spacing = sampleSpacingMeters > 1e-6 ? sampleSpacingMeters : DEFAULT_SAMPLE_SPACING_METERS;
        double maxDeviation = 0.0;
        double deviationSum = 0.0;
        int sampleCount = 0;
        int failedSamples = 0;
        int attempted = 0;

        for (double instanceChainage = 0.0; instanceChainage <= roadLength + 1e-6; instanceChainage += spacing) {
            double clampedInstance = Math.min(instanceChainage, roadLength);
            attempted++;
            SampleOutcome outcome = samplePair(network, road, alignment, map, clampedInstance);
            if (outcome.ok()) {
                maxDeviation = Math.max(maxDeviation, outcome.deviation());
                deviationSum += outcome.deviation();
                sampleCount++;
            } else {
                failedSamples++;
            }
        }

        attempted++;
        SampleOutcome endOutcome = samplePair(network, road, alignment, map, roadLength);
        if (endOutcome.ok()) {
            maxDeviation = Math.max(maxDeviation, endOutcome.deviation());
            deviationSum += endOutcome.deviation();
            sampleCount++;
        } else {
            failedSamples++;
        }

        // Query failures must surface — do not treat missing samples as zero deviation.
        if (sampleCount == 0 || failedSamples * 2 >= attempted) {
            return new Report(
                false, roadLength, alignmentLength, lengthMatches,
                originDeviation, Double.POSITIVE_INFINITY, 0.0, sampleCount);
        }

        return new Report(
            true,
            roadLength,
            alignmentLength,
            lengthMatches,
            originDeviation,
            maxDeviation,
            deviationSum / sampleCount,
            sampleCount);
    }

    private static SampleOutcome samplePair(
            RoadNetwork network,
            Road road,
            RoadHorizontalAlignment alignment,
            StationDomainMap map,
            double instanceChainage) {
        Optional<Vec2d> centerlinePoint =
            RoadPlanGeometry.instancePointAtInstanceChainage(network, road, instanceChainage);
        double canonical = map.toCanonical(instanceChainage);
        if (!Double.isFinite(canonical) || centerlinePoint.isEmpty()) {
            return SampleOutcome.fail();
        }
        double nativeStation = RoadPlanGeometry.haNativeStation(network, road, canonical);
        if (!Double.isFinite(nativeStation)) {
            return SampleOutcome.fail();
        }
        Optional<AlignmentPose> designPose = HorizontalAlignmentGeometry.poseAt(alignment, nativeStation);
        if (designPose.isEmpty()) {
            return SampleOutcome.fail();
        }
        double deviation = centerlinePoint.get().distance(
            new Vec2d(designPose.get().x(), designPose.get().y()));
        return SampleOutcome.ok(deviation);
    }

    private record SampleOutcome(boolean ok, double deviation) {
        static SampleOutcome ok(double deviation) {
            return new SampleOutcome(true, deviation);
        }

        static SampleOutcome fail() {
            return new SampleOutcome(false, Double.POSITIVE_INFINITY);
        }
    }
}
