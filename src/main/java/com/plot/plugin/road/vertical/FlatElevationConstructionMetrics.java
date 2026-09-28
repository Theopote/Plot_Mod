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
            int changedBlocks) {

        Metrics add(Metrics other) {
            return new Metrics(
                cutVolume + other.cutVolume,
                fillVolume + other.fillVolume,
                bridgeLength + other.bridgeLength,
                tunnelLength + other.tunnelLength,
                changedBlocks + other.changedBlocks);
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
            switch (detection.constructionTypes().get(i)) {
                case CUT -> cutVolume += Math.abs(diff) * (int) Math.round(distance);
                case FILL -> fillVolume += diff * (int) Math.round(distance);
                case BRIDGE -> {
                    fillVolume += diff * (int) Math.round(distance);
                    bridgeLength += distance;
                }
                case TUNNEL -> {
                    cutVolume += Math.abs(diff) * (int) Math.round(distance);
                    tunnelLength += distance;
                }
                case ROAD -> {
                    if (diff > 1) {
                        fillVolume += diff * (int) Math.round(distance);
                    } else if (diff < -1) {
                        cutVolume += Math.abs(diff) * (int) Math.round(distance);
                    }
                }
            }
        }

        int changedBlocks = cutVolume + fillVolume;
        return new Metrics(cutVolume, fillVolume, bridgeLength, tunnelLength, changedBlocks);
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
}
