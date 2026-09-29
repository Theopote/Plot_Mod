package com.plot.plugin.road.vertical;

import com.plot.plugin.road.RoadConstructionEvaluator;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.pipeline.construction.ConstructionDetection;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;

import java.util.List;

/** Aggregates cut/fill/bridge/tunnel metrics from profile-based construction detection. */
final class FlatElevationConstructionMetrics {
    private FlatElevationConstructionMetrics() {
    }

    record Metrics(
            int cutVolume,
            int fillVolume,
            double bridgeLength,
            double tunnelLength,
            int bridgeRunCount,
            int tunnelRunCount,
            int earthworkBlocks) {

        Metrics add(Metrics other) {
            return new Metrics(
                cutVolume + other.cutVolume,
                fillVolume + other.fillVolume,
                bridgeLength + other.bridgeLength,
                tunnelLength + other.tunnelLength,
                bridgeRunCount + other.bridgeRunCount,
                tunnelRunCount + other.tunnelRunCount,
                earthworkBlocks + other.earthworkBlocks);
        }

        static Metrics empty() {
            return new Metrics(0, 0, 0.0, 0.0, 0, 0, 0);
        }
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

        int bridgeRunCount = structureRunCount(detection, RoadConstructionType.BRIDGE);
        int tunnelRunCount = structureRunCount(detection, RoadConstructionType.TUNNEL);
        return new Metrics(
            cutVolume,
            fillVolume,
            bridgeLength,
            tunnelLength,
            bridgeRunCount,
            tunnelRunCount,
            cutVolume + fillVolume);
    }

    static Metrics fromStageA(
            List<RoadConstructionType> types,
            int cutVolume,
            int fillVolume,
            double bridgeLength,
            double tunnelLength) {
        return new Metrics(
            cutVolume,
            fillVolume,
            bridgeLength,
            tunnelLength,
            countStructureRuns(types, RoadConstructionType.BRIDGE),
            countStructureRuns(types, RoadConstructionType.TUNNEL),
            cutVolume + fillVolume);
    }

    static int countStructureRuns(List<RoadConstructionType> types, RoadConstructionType target) {
        if (types == null || types.isEmpty()) {
            return 0;
        }
        int count = 0;
        RoadConstructionType previous = null;
        for (RoadConstructionType type : types) {
            if (type == target && previous != target) {
                count++;
            }
            previous = type;
        }
        return count;
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
            + costConfig.bridgeBaseCost() * metrics.bridgeRunCount()
            + costConfig.bridgeCostPerLength() * metrics.bridgeLength()
            + costConfig.tunnelBaseCost() * metrics.tunnelRunCount()
            + costConfig.tunnelCostPerLength() * metrics.tunnelLength()
            + junctionPenalty;
    }

    private static int structureRunCount(ConstructionDetection detection, RoadConstructionType type) {
        if (!detection.runs().isEmpty()) {
            return (int) detection.runCount(type);
        }
        return countStructureRuns(detection.constructionTypes(), type);
    }

    private static int averageHeight(int a, int b) {
        return (int) Math.round((a + b) / 2.0);
    }

    record EarthworkTotals(int cutVolume, int fillVolume, double bridgeLength, double tunnelLength) {
    }
}
