package com.plot.plugin.road.vertical;

import com.plot.plugin.road.RoadConstructionEvaluator;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.pipeline.construction.ConstructionDetection;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;

import java.util.List;

/** Aggregates cut/fill/bridge/tunnel metrics from profile-based construction detection. */
final class FlatElevationConstructionMetrics {
    private static final double EPSILON = 1e-6;

    record Metrics(
            int cutVolume,
            int fillVolume,
            double bridgeLength,
            double tunnelLength,
            int earthworkBlocks) {

        Metrics add(Metrics other) {
            return new Metrics(
                cutVolume + other.cutVolume,
                fillVolume + other.fillVolume,
                bridgeLength + other.bridgeLength,
                tunnelLength + other.tunnelLength,
                earthworkBlocks + other.earthworkBlocks);
        }

        static Metrics empty() {
            return new Metrics(0, 0, 0.0, 0.0, 0);
        }
    }

    private FlatElevationConstructionMetrics() {
    }

    static Metrics aggregate(ConstructionDetection detection, List<SegmentHeightInfo> heightInfos) {
        if (detection == null || heightInfos == null || detection.constructionTypes().isEmpty()) {
            return Metrics.empty();
        }

        int cutVolume = 0;
        int fillVolume = 0;
        double bridgeLength = 0.0;
        double tunnelLength = 0.0;
        int segmentCount = Math.min(detection.constructionTypes().size(), heightInfos.size());
        for (int i = 0; i < segmentCount; i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            double distance = detection.segmentDistances().get(i);
            int ground = averageHeight(info.groundStart, info.groundEnd);
            int target = averageHeight(info.targetStart, info.targetEnd);
            int diff = target - ground;
            EarthworkTotals totals = accumulateSegment(detection.constructionTypes().get(i), diff, distance);
            cutVolume += totals.cutVolume;
            fillVolume += totals.fillVolume;
            bridgeLength += totals.bridgeLength;
            tunnelLength += totals.tunnelLength;
        }

        return new Metrics(cutVolume, fillVolume, bridgeLength, tunnelLength, cutVolume + fillVolume);
    }

    static EarthworkTotals accumulateSegment(RoadConstructionType type, int diff, double distance) {
        int roundedDistance = (int) Math.round(distance);
        int cutVolume = 0;
        int fillVolume = 0;
        double bridgeLength = 0.0;
        double tunnelLength = 0.0;
        switch (type) {
            case CUT -> cutVolume += Math.abs(diff) * roundedDistance;
            case FILL -> fillVolume += diff * roundedDistance;
            case BRIDGE -> bridgeLength += distance;
            case TUNNEL -> tunnelLength += distance;
            case ROAD -> {
                if (diff > 1) {
                    fillVolume += diff * roundedDistance;
                } else if (diff < -1) {
                    cutVolume += Math.abs(diff) * roundedDistance;
                }
            }
        }
        return new EarthworkTotals(cutVolume, fillVolume, bridgeLength, tunnelLength);
    }

    static double score(
            Metrics metrics,
            RoadConstructionEvaluator.RoadConstructionCostConfig costConfig,
            double junctionPenalty) {
        return costConfig.cutCostPerVolume() * metrics.cutVolume()
            + costConfig.fillCostPerVolume() * metrics.fillVolume()
            + (metrics.bridgeLength() > EPSILON ? costConfig.bridgeBaseCost() : 0.0)
            + costConfig.bridgeCostPerLength() * metrics.bridgeLength()
            + (metrics.tunnelLength() > EPSILON ? costConfig.tunnelBaseCost() : 0.0)
            + costConfig.tunnelCostPerLength() * metrics.tunnelLength()
            + junctionPenalty;
    }

    private static int averageHeight(int a, int b) {
        return (int) Math.round((a + b) / 2.0);
    }

    record EarthworkTotals(int cutVolume, int fillVolume, double bridgeLength, double tunnelLength) {
    }
}
