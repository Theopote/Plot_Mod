package com.plot.plugin.road.pipeline.construction;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.pipeline.profile.environment.WaterCrossing;
import com.plot.plugin.road.pipeline.profile.environment.WaterCrossingStrategy;
import com.plot.core.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies profile {@link WaterCrossingStrategy} onto cost-based {@link RoadConstructionType}
 * classification so construction matches water-aware vertical design.
 */
public final class WaterCrossingConstructionResolver {

    private static final double EPSILON = 1e-9;

    private WaterCrossingConstructionResolver() {
    }

    public static ConstructionDetection apply(
            ConstructionDetection base,
            List<WaterCrossing> crossings,
            List<SegmentHeightInfo> heightInfos,
            double unitsPerBlock,
            RoadSystemConfig config,
            TerrainSampler terrain,
            RoadConstructionClassifier.CanvasBlockPosResolver canvasToBlockPos) {
        if (base == null || crossings == null || crossings.isEmpty()) {
            return base;
        }
        double scale = unitsPerBlock > EPSILON ? unitsPerBlock : 1.0;
        List<RoadConstructionType> types = new ArrayList<>(base.constructionTypes());
        double station = 0.0;
        for (int i = 0; i < types.size() && i < heightInfos.size(); i++) {
            double segmentLengthWorld = heightInfos.get(i).segment.distance / scale;
            double segmentStart = station;
            double segmentEnd = station + segmentLengthWorld;
            types.set(i, resolveSegmentType(
                types.get(i),
                crossings,
                segmentStart,
                segmentEnd,
                config));
            station = segmentEnd;
        }
        return RoadConstructionClassifier.finalizeDetection(
            types,
            base.segmentDistances(),
            heightInfos,
            canvasToBlockPos,
            terrain);
    }

    public static WaterCrossingStrategy strategyAtWorldStation(
            List<WaterCrossing> crossings,
            double worldStation) {
        if (crossings == null || crossings.isEmpty()) {
            return null;
        }
        for (WaterCrossing crossing : crossings) {
            if (crossing.containsStation(worldStation)) {
                return crossing.strategy();
            }
        }
        return null;
    }

    public static boolean isBridgeStructureStation(
            List<WaterCrossing> crossings,
            double worldStation) {
        if (crossings == null || crossings.isEmpty()) {
            return true;
        }
        for (WaterCrossing crossing : crossings) {
            if (!isElevatedBridgeStrategy(crossing.strategy())) {
                continue;
            }
            if (crossing.isInCrossingZone(worldStation)
                    || crossing.isInApproachZone(worldStation)
                    || crossing.isInExitZone(worldStation)) {
                return true;
            }
        }
        return false;
    }

    public static double bridgePillarSpacingBlocks(
            List<WaterCrossing> crossings,
            double worldStation,
            double defaultSpacingBlocks) {
        WaterCrossingStrategy strategy = strategyAtWorldStation(crossings, worldStation);
        if (strategy == WaterCrossingStrategy.LONG_BRIDGE) {
            return Math.max(defaultSpacingBlocks, defaultSpacingBlocks * 2.0);
        }
        return defaultSpacingBlocks;
    }

    private static RoadConstructionType resolveSegmentType(
            RoadConstructionType baseType,
            List<WaterCrossing> crossings,
            double segmentStart,
            double segmentEnd,
            RoadSystemConfig config) {
        WaterCrossingStrategy strategy = dominantStrategy(crossings, segmentStart, segmentEnd);
        if (strategy == null) {
            return baseType;
        }
        return switch (strategy) {
            case CAUSEWAY -> overlapsCrossingZone(crossings, segmentStart, segmentEnd)
                ? RoadConstructionType.FILL
                : baseType;
            case BRIDGE, LONG_BRIDGE -> overlapsCrossingZone(crossings, segmentStart, segmentEnd)
                ? RoadConstructionType.BRIDGE
                : baseType;
            case TUNNEL_CANDIDATE -> config != null && config.isAllowUnderwaterRoad()
                && overlapsCrossingZone(crossings, segmentStart, segmentEnd)
                ? RoadConstructionType.TUNNEL
                : baseType;
        };
    }

    private static WaterCrossingStrategy dominantStrategy(
            List<WaterCrossing> crossings,
            double segmentStart,
            double segmentEnd) {
        WaterCrossingStrategy selected = null;
        double bestOverlap = 0.0;
        for (WaterCrossing crossing : crossings) {
            double overlap = overlapLength(
                segmentStart,
                segmentEnd,
                crossing.approachStartStation(),
                crossing.exitEndStation());
            if (overlap <= EPSILON) {
                continue;
            }
            if (overlap > bestOverlap + EPSILON) {
                bestOverlap = overlap;
                selected = crossing.strategy();
            }
        }
        return selected;
    }

    private static boolean overlapsCrossingZone(
            List<WaterCrossing> crossings,
            double segmentStart,
            double segmentEnd) {
        for (WaterCrossing crossing : crossings) {
            if (overlapLength(
                    segmentStart,
                    segmentEnd,
                    crossing.crossingStartStation(),
                    crossing.crossingEndStation()) > EPSILON) {
                return true;
            }
        }
        return false;
    }

    private static boolean isElevatedBridgeStrategy(WaterCrossingStrategy strategy) {
        return strategy == WaterCrossingStrategy.BRIDGE
            || strategy == WaterCrossingStrategy.LONG_BRIDGE;
    }

    private static double overlapLength(double aStart, double aEnd, double bStart, double bEnd) {
        double start = Math.max(aStart, bStart);
        double end = Math.min(aEnd, bEnd);
        return Math.max(0.0, end - start);
    }
}
