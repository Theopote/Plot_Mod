package com.plot.plugin.road;

import com.plot.plugin.config.RoadSystemConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 纯函数道路施工决策层：按连续同符号区间做成本比较，起步成本只摊销一次。
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

    /**
     * 单段评估：硬阈值与小高度差走即时判定；显著高度差按单段区间做成本比较。
     */
    public static RoadConstructionType evaluateSegment(
            double segmentDistance,
            int groundHeight,
            int targetHeight,
            RoadConstructionCostConfig costConfig) {
        int heightDifference = targetHeight - groundHeight;
        RoadConstructionType immediate = classifyImmediate(heightDifference, costConfig);
        if (immediate != null) {
            return immediate;
        }

        double earthworkCost = computeEarthworkCost(
            heightDifference, segmentDistance, costConfig);
        return decideInterval(
            Integer.signum(heightDifference),
            segmentDistance,
            earthworkCost,
            costConfig,
            0.0);
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

        RoadConstructionType[] result = new RoadConstructionType[size];
        for (int i = 0; i < size; i++) {
            int heightDifference = targetHeights.get(i) - groundHeights.get(i);
            result[i] = classifyImmediate(heightDifference, costConfig);
        }

        int index = 0;
        while (index < size) {
            if (result[index] != null) {
                index++;
                continue;
            }

            int runStart = index;
            int sign = Integer.signum(targetHeights.get(index) - groundHeights.get(index));
            double totalLength = 0.0;
            double totalEarthworkCost = 0.0;

            while (index < size && result[index] == null
                    && Integer.signum(targetHeights.get(index) - groundHeights.get(index)) == sign) {
                int heightDifference = targetHeights.get(index) - groundHeights.get(index);
                double distance = segmentDistances.get(index);
                totalLength += distance;
                totalEarthworkCost += computeEarthworkCost(heightDifference, distance, costConfig);
                index++;
            }

            RoadConstructionType decision = decideInterval(
                sign, totalLength, totalEarthworkCost, costConfig, minimumRunLength);
            for (int j = runStart; j < index; j++) {
                result[j] = decision;
            }
        }

        return List.of(result);
    }

    private static RoadConstructionType classifyImmediate(
            int heightDifference,
            RoadConstructionCostConfig costConfig) {
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
            return null;
        }
        if (heightDifference < 0) {
            if (Math.abs(heightDifference) <= costConfig.minimumConsiderationHeight()) {
                return heightDifference < -1 ? RoadConstructionType.CUT : RoadConstructionType.ROAD;
            }
            return null;
        }
        return RoadConstructionType.ROAD;
    }

    private static double computeEarthworkCost(
            int heightDifference,
            double segmentDistance,
            RoadConstructionCostConfig costConfig) {
        if (heightDifference > 0) {
            return heightDifference * segmentDistance * costConfig.fillCostPerVolume();
        }
        return Math.abs(heightDifference) * segmentDistance * costConfig.cutCostPerVolume();
    }

    private static RoadConstructionType decideInterval(
            int sign,
            double totalLength,
            double totalEarthworkCost,
            RoadConstructionCostConfig costConfig,
            double minimumRunLength) {
        if (sign > 0) {
            double bridgeCost = costConfig.bridgeBaseCost()
                + totalLength * costConfig.bridgeCostPerLength();
            RoadConstructionType chosen = totalEarthworkCost <= bridgeCost
                ? RoadConstructionType.FILL
                : RoadConstructionType.BRIDGE;
            if (chosen == RoadConstructionType.BRIDGE
                    && minimumRunLength > 0.0
                    && totalLength < minimumRunLength) {
                return RoadConstructionType.FILL;
            }
            return chosen;
        }

        double tunnelCost = costConfig.tunnelBaseCost()
            + totalLength * costConfig.tunnelCostPerLength();
        RoadConstructionType chosen = totalEarthworkCost <= tunnelCost
            ? RoadConstructionType.CUT
            : RoadConstructionType.TUNNEL;
        if (chosen == RoadConstructionType.TUNNEL
                && minimumRunLength > 0.0
                && totalLength < minimumRunLength) {
            return RoadConstructionType.CUT;
        }
        return chosen;
    }
}
