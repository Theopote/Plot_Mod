package com.plot.plugin.road;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路坡度与高度计算的纯数值逻辑（不依赖 Minecraft World）
 */
public final class RoadSlopeUtils {
    private RoadSlopeUtils() {
    }

    /**
     * 根据坡度限制，计算单段路径的目标终点高度
     */
    public static int computeTargetEndHeight(
            int currentHeight,
            int groundStart,
            int groundEnd,
            double segmentDistance,
            float maxSlopePercent) {
        double maxSlopeRatio = maxSlopePercent / 100.0;
        double maxRise = segmentDistance * maxSlopeRatio;
        int idealRise = groundEnd - groundStart;

        if (Math.abs(idealRise) <= maxRise) {
            return groundEnd;
        }
        if (idealRise > 0) {
            return currentHeight + (int) maxRise;
        }
        return currentHeight - (int) maxRise;
    }

    public static double computeActualSlopePercent(int targetStart, int targetEnd, double segmentDistance) {
        if (segmentDistance <= 0) {
            return 0.0;
        }
        return Math.abs(targetEnd - targetStart) / segmentDistance * 100.0;
    }

    /**
     * 沿多段路径链式计算目标高度（支持里程相关的坡度覆盖）
     */
    public static List<Integer> computeChainedTargetHeights(
            List<Double> segmentDistances,
            List<Integer> groundStarts,
            List<Integer> groundEnds,
            List<Float> maxSlopePercents,
            Integer manualStartHeight) {
        if (segmentDistances == null || segmentDistances.isEmpty()) {
            return List.of();
        }

        int size = segmentDistances.size();
        if (groundStarts.size() != size || groundEnds.size() != size || maxSlopePercents.size() != size) {
            throw new IllegalArgumentException("segment height inputs must have equal length");
        }

        List<Integer> targetEnds = new ArrayList<>(size);
        int currentHeight = manualStartHeight != null
            ? manualStartHeight
            : groundStarts.getFirst();

        for (int i = 0; i < size; i++) {
            int targetEnd = computeTargetEndHeight(
                currentHeight,
                groundStarts.get(i),
                groundEnds.get(i),
                segmentDistances.get(i),
                maxSlopePercents.get(i)
            );
            targetEnds.add(targetEnd);
            currentHeight = targetEnd;
        }
        return targetEnds;
    }

    /**
     * 路口汇聚高度：取各相连边在节点处高度的平均值
     */
    public static int averageJunctionHeight(List<Integer> connectedEdgeHeights) {
        if (connectedEdgeHeights == null || connectedEdgeHeights.isEmpty()) {
            return 64;
        }
        return (int) Math.round(
            connectedEdgeHeights.stream().mapToInt(Integer::intValue).average().orElse(64)
        );
    }
}
