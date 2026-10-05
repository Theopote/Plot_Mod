package com.plot.plugin.road;

import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;

import java.util.List;

/**
 * 纯函数道路施工决策层：按连续同符号区间做复杂度比较，起步惩罚只摊销一次。
 */
public final class RoadConstructionEvaluator {

    private RoadConstructionEvaluator() {
    }

    /**
     * 施工复杂度权重（内部启发式，非工程造价模型）。
     */
    public record RoadConstructionScoreConfig(
            double fillWeight,
            double bridgeBasePenalty,
            double bridgeLengthPenalty,
            double cutWeight,
            double tunnelBasePenalty,
            double tunnelLengthPenalty,
            double minimumConsiderationHeight,
            int bridgeThreshold,
            int tunnelThreshold) {

        public static RoadConstructionScoreConfig from(com.plot.plugin.config.RoadSystemConfig config) {
            return RoadConstructionHeuristics.constructionConfig(config);
        }
    }

    /**
     * 单段评估：硬阈值与小高度差走即时判定；显著高度差按单段区间做复杂度比较。
     */
    public static RoadConstructionType evaluateSegment(
            double segmentDistance,
            int groundHeight,
            int targetHeight,
            RoadConstructionScoreConfig scoreConfig) {
        int heightDifference = targetHeight - groundHeight;
        RoadConstructionType immediate = classifyImmediate(heightDifference, scoreConfig);
        if (immediate != null) {
            return immediate;
        }

        double earthworkScore = computeEarthworkScore(
            heightDifference, segmentDistance, scoreConfig);
        return decideInterval(
            Integer.signum(heightDifference),
            segmentDistance,
            earthworkScore,
            scoreConfig,
            0.0);
    }

    public static List<RoadConstructionType> evaluatePath(
            List<Double> segmentDistances,
            List<Integer> groundHeights,
            List<Integer> targetHeights,
            RoadConstructionScoreConfig scoreConfig,
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
            result[i] = classifyImmediate(heightDifference, scoreConfig);
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
            double totalEarthworkScore = 0.0;

            while (index < size && result[index] == null
                    && Integer.signum(targetHeights.get(index) - groundHeights.get(index)) == sign) {
                int heightDifference = targetHeights.get(index) - groundHeights.get(index);
                double distance = segmentDistances.get(index);
                totalLength += distance;
                totalEarthworkScore += computeEarthworkScore(heightDifference, distance, scoreConfig);
                index++;
            }

            RoadConstructionType decision = decideInterval(
                sign, totalLength, totalEarthworkScore, scoreConfig, minimumRunLength);
            for (int j = runStart; j < index; j++) {
                result[j] = decision;
            }
        }

        normalizeShortStructureRuns(result, segmentDistances, minimumRunLength);
        return List.of(result);
    }

    /** Applies minimum length to every bridge/tunnel run, including hard-threshold decisions. */
    private static void normalizeShortStructureRuns(
            RoadConstructionType[] types,
            List<Double> distances,
            double minimumRunLength) {
        if (minimumRunLength <= 0.0) {
            return;
        }
        int index = 0;
        while (index < types.length) {
            RoadConstructionType type = types[index];
            int end = index + 1;
            double length = distances.get(index);
            while (end < types.length && types[end] == type) {
                length += distances.get(end);
                end++;
            }
            if (length + 1e-9 < minimumRunLength) {
                RoadConstructionType replacement = switch (type) {
                    case BRIDGE -> RoadConstructionType.FILL;
                    case TUNNEL -> RoadConstructionType.CUT;
                    default -> null;
                };
                if (replacement != null) {
                    for (int i = index; i < end; i++) {
                        types[i] = replacement;
                    }
                }
            }
            index = end;
        }
    }

    private static RoadConstructionType classifyImmediate(
            int heightDifference,
            RoadConstructionScoreConfig scoreConfig) {
        if (heightDifference > scoreConfig.bridgeThreshold()) {
            return RoadConstructionType.BRIDGE;
        }
        if (heightDifference < -scoreConfig.tunnelThreshold()) {
            return RoadConstructionType.TUNNEL;
        }
        if (heightDifference > 0) {
            if (heightDifference <= scoreConfig.minimumConsiderationHeight()) {
                return heightDifference > 1 ? RoadConstructionType.FILL : RoadConstructionType.ROAD;
            }
            return null;
        }
        if (heightDifference < 0) {
            if (Math.abs(heightDifference) <= scoreConfig.minimumConsiderationHeight()) {
                return heightDifference < -1 ? RoadConstructionType.CUT : RoadConstructionType.ROAD;
            }
            return null;
        }
        return RoadConstructionType.ROAD;
    }

    private static double computeEarthworkScore(
            int heightDifference,
            double segmentDistance,
            RoadConstructionScoreConfig scoreConfig) {
        if (heightDifference > 0) {
            return heightDifference * segmentDistance * scoreConfig.fillWeight();
        }
        return Math.abs(heightDifference) * segmentDistance * scoreConfig.cutWeight();
    }

    private static RoadConstructionType decideInterval(
            int sign,
            double totalLength,
            double totalEarthworkScore,
            RoadConstructionScoreConfig scoreConfig,
            double minimumRunLength) {
        if (sign > 0) {
            double bridgeScore = scoreConfig.bridgeBasePenalty()
                + totalLength * scoreConfig.bridgeLengthPenalty();
            RoadConstructionType chosen = totalEarthworkScore <= bridgeScore
                ? RoadConstructionType.FILL
                : RoadConstructionType.BRIDGE;
            if (chosen == RoadConstructionType.BRIDGE
                    && minimumRunLength > 0.0
                    && totalLength < minimumRunLength) {
                return RoadConstructionType.FILL;
            }
            return chosen;
        }

        double tunnelScore = scoreConfig.tunnelBasePenalty()
            + totalLength * scoreConfig.tunnelLengthPenalty();
        RoadConstructionType chosen = totalEarthworkScore <= tunnelScore
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
