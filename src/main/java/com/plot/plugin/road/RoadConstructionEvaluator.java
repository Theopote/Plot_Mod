package com.plot.plugin.road;

import com.plot.plugin.config.RoadSystemConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 纯函数道路施工决策层：基于成本比较判定每段施工类型，并平滑短距离桥/隧道段。
 */
public final class RoadConstructionEvaluator {

    private RoadConstructionEvaluator() {
    }

    /**
     * 施工成本配置（字段存于 {@link RoadSystemConfig}，此处为评估用值对象）。
     */
    public record RoadConstructionCostConfig(
            double fillCostPerVolume,
            double bridgeBaseCost,
            double bridgeCostPerLength,
            double cutCostPerVolume,
            double tunnelBaseCost,
            double tunnelCostPerLength,
            double minimumConsiderationHeight,
            int bridgeThreshold,
            int tunnelThreshold) {

        public static RoadConstructionCostConfig from(RoadSystemConfig config) {
            return new RoadConstructionCostConfig(
                config.getFillCostPerVolume(),
                config.getBridgeBaseCost(),
                config.getBridgeCostPerLength(),
                config.getCutCostPerVolume(),
                config.getTunnelBaseCost(),
                config.getTunnelCostPerLength(),
                config.getMinimumConsiderationHeight(),
                config.getBridgeThreshold(),
                config.getTunnelThreshold()
            );
        }
    }

    public static RoadConstructionType evaluateSegment(
            double segmentDistance,
            int groundHeight,
            int targetHeight,
            RoadConstructionCostConfig costConfig) {
        int heightDifference = targetHeight - groundHeight;

        if (heightDifference > costConfig.bridgeThreshold()) {
            return RoadConstructionType.BRIDGE;
        }
        if (heightDifference < -costConfig.tunnelThreshold()) {
            return RoadConstructionType.TUNNEL;
        }

        if (heightDifference > 0) {
            if (heightDifference <= costConfig.minimumConsiderationHeight()) {
                return heightDifference > 1 ? RoadConstructionType.FILL : RoadConstructionType.ROAD;
            }
            double fillCost = heightDifference * segmentDistance * costConfig.fillCostPerVolume();
            double bridgeCost = costConfig.bridgeBaseCost()
                + segmentDistance * costConfig.bridgeCostPerLength();
            return fillCost <= bridgeCost ? RoadConstructionType.FILL : RoadConstructionType.BRIDGE;
        }

        if (heightDifference < 0) {
            int absDiff = Math.abs(heightDifference);
            if (absDiff <= costConfig.minimumConsiderationHeight()) {
                return heightDifference < -1 ? RoadConstructionType.CUT : RoadConstructionType.ROAD;
            }
            double cutCost = absDiff * segmentDistance * costConfig.cutCostPerVolume();
            double tunnelCost = costConfig.tunnelBaseCost()
                + segmentDistance * costConfig.tunnelCostPerLength();
            return cutCost <= tunnelCost ? RoadConstructionType.CUT : RoadConstructionType.TUNNEL;
        }

        return RoadConstructionType.ROAD;
    }

    public static List<RoadConstructionType> evaluatePath(
            List<Double> segmentDistances,
            List<Integer> groundHeights,
            List<Integer> targetHeights,
            RoadConstructionCostConfig costConfig,
            double minimumRunLength) {
        if (segmentDistances == null || segmentDistances.isEmpty()) {
            return List.of();
        }
        int size = segmentDistances.size();
        if (groundHeights.size() != size || targetHeights.size() != size) {
            throw new IllegalArgumentException("segment inputs must have equal length");
        }

        List<RoadConstructionType> raw = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            raw.add(evaluateSegment(
                segmentDistances.get(i),
                groundHeights.get(i),
                targetHeights.get(i),
                costConfig));
        }

        if (minimumRunLength <= 0.0) {
            return raw;
        }
        return smoothShortRuns(raw, segmentDistances, groundHeights, targetHeights, minimumRunLength);
    }

    private static List<RoadConstructionType> smoothShortRuns(
            List<RoadConstructionType> types,
            List<Double> segmentDistances,
            List<Integer> groundHeights,
            List<Integer> targetHeights,
            double minimumRunLength) {
        List<RoadConstructionType> result = new ArrayList<>(types);
        int index = 0;
        while (index < result.size()) {
            RoadConstructionType runType = result.get(index);
            if (!isSmoothable(runType)) {
                index++;
                continue;
            }

            int runStart = index;
            double runLength = 0.0;
            while (index < result.size() && result.get(index) == runType) {
                runLength += segmentDistances.get(index);
                index++;
            }

            if (runLength < minimumRunLength) {
                RoadConstructionType replacement = pickReplacement(
                    result, runStart, index, segmentDistances, groundHeights, targetHeights);
                for (int j = runStart; j < index; j++) {
                    result.set(j, replacement);
                }
            }
        }
        return result;
    }

    private static boolean isSmoothable(RoadConstructionType type) {
        return type == RoadConstructionType.BRIDGE || type == RoadConstructionType.TUNNEL;
    }

    private static RoadConstructionType pickReplacement(
            List<RoadConstructionType> types,
            int runStart,
            int runEnd,
            List<Double> segmentDistances,
            List<Integer> groundHeights,
            List<Integer> targetHeights) {
        RoadConstructionType left = runStart > 0 ? types.get(runStart - 1) : null;
        RoadConstructionType right = runEnd < types.size() ? types.get(runEnd) : null;

        if (left != null && left == right) {
            return left;
        }
        if (left != null && right == null) {
            return left;
        }
        if (left == null && right != null) {
            return right;
        }
        if (left != null) {
            double leftRunLength = contiguousRunLength(types, segmentDistances, runStart - 1, -1);
            double rightRunLength = contiguousRunLength(types, segmentDistances, runEnd, 1);
            return leftRunLength >= rightRunLength ? left : right;
        }

        return fallbackByHeight(groundHeights.get(runStart), targetHeights.get(runStart));
    }

    private static double contiguousRunLength(
            List<RoadConstructionType> types,
            List<Double> segmentDistances,
            int startIndex,
            int direction) {
        RoadConstructionType type = types.get(startIndex);
        double length = segmentDistances.get(startIndex);
        int index = startIndex + direction;
        while (index >= 0 && index < types.size() && types.get(index) == type) {
            length += segmentDistances.get(index);
            index += direction;
        }
        return length;
    }

    private static RoadConstructionType fallbackByHeight(int groundHeight, int targetHeight) {
        int heightDifference = targetHeight - groundHeight;
        if (heightDifference > 1) {
            return RoadConstructionType.FILL;
        }
        if (heightDifference < -1) {
            return RoadConstructionType.CUT;
        }
        return RoadConstructionType.ROAD;
    }
}
