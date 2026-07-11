package com.plot.plugin.road;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

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
        return computeChainedTargetHeights(
            segmentDistances,
            groundStarts,
            groundEnds,
            maxSlopePercents,
            manualStartHeight,
            0.0,
            0.0,
            0.0f
        );
    }

    /**
     * 沿多段路径链式计算目标高度，并限制单一方向的连续爬坡/下坡长度。
     */
    public static List<Integer> computeChainedTargetHeights(
            List<Double> segmentDistances,
            List<Integer> groundStarts,
            List<Integer> groundEnds,
            List<Float> maxSlopePercents,
            Integer manualStartHeight,
            double maxContinuousSlopeLength,
            double relaxedSlopeLength,
            float relaxedSlopePercent) {
        if (segmentDistances == null || segmentDistances.isEmpty()) {
            return List.of();
        }

        int size = segmentDistances.size();
        if (groundStarts.size() != size || groundEnds.size() != size || maxSlopePercents.size() != size) {
            throw new IllegalArgumentException("segment height inputs must have equal length");
        }

        boolean slopeLengthLimitEnabled = maxContinuousSlopeLength > 0.0 && relaxedSlopeLength > 0.0;
        List<Integer> targetEnds = new ArrayList<>(size);
        int currentHeight = manualStartHeight != null
            ? manualStartHeight
            : groundStarts.getFirst();

        int continuousDirection = 0;
        double continuousRunLength = 0.0;
        double relaxedRemaining = 0.0;

        for (int i = 0; i < size; i++) {
            double segmentDistance = segmentDistances.get(i);
            int groundStart = groundStarts.get(i);
            int groundEnd = groundEnds.get(i);
            float maxSlopePercent = maxSlopePercents.get(i);

            double processedInSegment = 0.0;
            while (processedInSegment < segmentDistance - 1e-9) {
                double remainingInSegment = segmentDistance - processedInSegment;
                float effectiveSlope = maxSlopePercent;
                double chunkDistance = remainingInSegment;

                if (slopeLengthLimitEnabled) {
                    if (relaxedRemaining > 0.0) {
                        effectiveSlope = relaxedSlopePercent;
                        chunkDistance = Math.min(remainingInSegment, relaxedRemaining);
                    } else if (continuousRunLength >= maxContinuousSlopeLength) {
                        relaxedRemaining = relaxedSlopeLength;
                        effectiveSlope = relaxedSlopePercent;
                        chunkDistance = Math.min(remainingInSegment, relaxedRemaining);
                    }
                }

                double chunkStartT = processedInSegment / segmentDistance;
                double provisionalEndT = (processedInSegment + chunkDistance) / segmentDistance;
                int provisionalGroundStart = interpolateGroundHeight(groundStart, groundEnd, chunkStartT);
                int provisionalGroundEnd = interpolateGroundHeight(groundStart, groundEnd, provisionalEndT);
                int provisionalTargetEnd = computeTargetEndHeight(
                    currentHeight,
                    provisionalGroundStart,
                    provisionalGroundEnd,
                    chunkDistance,
                    effectiveSlope
                );
                int provisionalDirection = provisionalTargetEnd > currentHeight
                    ? 1
                    : (provisionalTargetEnd < currentHeight ? -1 : 0);

                if (slopeLengthLimitEnabled && relaxedRemaining <= 0.0
                        && continuousRunLength < maxContinuousSlopeLength
                        && provisionalDirection != 0
                        && (continuousDirection == 0 || provisionalDirection == continuousDirection)) {
                    double runSoFar = continuousDirection == provisionalDirection ? continuousRunLength : 0.0;
                    double allowedBeforeLimit = maxContinuousSlopeLength - runSoFar;
                    if (allowedBeforeLimit > 1e-9 && allowedBeforeLimit < chunkDistance) {
                        chunkDistance = allowedBeforeLimit;
                    }
                }

                chunkStartT = processedInSegment / segmentDistance;
                double chunkEndT = (processedInSegment + chunkDistance) / segmentDistance;
                int chunkGroundStart = interpolateGroundHeight(groundStart, groundEnd, chunkStartT);
                int chunkGroundEnd = interpolateGroundHeight(groundStart, groundEnd, chunkEndT);

                int chunkTargetEnd = computeTargetEndHeight(
                    currentHeight,
                    chunkGroundStart,
                    chunkGroundEnd,
                    chunkDistance,
                    effectiveSlope
                );

                int delta = chunkTargetEnd - currentHeight;
                int direction = delta > 0 ? 1 : (delta < 0 ? -1 : 0);
                if (direction == 0 || direction != continuousDirection) {
                    continuousDirection = direction;
                    continuousRunLength = chunkDistance;
                } else {
                    continuousRunLength += chunkDistance;
                }

                if (slopeLengthLimitEnabled && relaxedRemaining > 0.0) {
                    relaxedRemaining -= chunkDistance;
                    if (relaxedRemaining <= 1e-9) {
                        relaxedRemaining = 0.0;
                        continuousRunLength = 0.0;
                        continuousDirection = 0;
                    }
                }

                currentHeight = chunkTargetEnd;
                processedInSegment += chunkDistance;
            }
            targetEnds.add(currentHeight);
        }
        return targetEnds;
    }

    /**
     * 计算从路肩边缘开始，按给定坡度比向外延伸、直到与地形高度相交为止的边坡剖面。
     *
     * @param shoulderEdgeHeight 路肩外缘高度
     * @param direction          +1 表示挖方（cut，目标高度向上升），-1 表示填方（fill，目标高度向下降）
     * @param groundHeightAtOffset 输入：距离路肩边缘的水平偏移（格），输出：该处地形高度
     * @param slopeRatio         水平延伸格数 / 垂直变化 1 格
     * @param maxHorizontalRun   安全上限水平距离
     * @return 每一步的 (水平偏移量, 目标高度) 序列，第一个元素是路肩边缘本身
     */
    public static List<int[]> computeSlopeProfile(
            int shoulderEdgeHeight,
            int direction,
            IntUnaryOperator groundHeightAtOffset,
            float slopeRatio,
            int maxHorizontalRun) {
        List<int[]> profile = new ArrayList<>();
        profile.add(new int[]{0, shoulderEdgeHeight});

        int groundAtEdge = groundHeightAtOffset.applyAsInt(0);
        if (groundAtEdge == shoulderEdgeHeight) {
            return profile;
        }

        float horizontalStep = Math.max(0.5f, slopeRatio);
        int stepIndex = 0;
        double horizontalOffset = 0.0;

        while (horizontalOffset < maxHorizontalRun) {
            horizontalOffset += horizontalStep;
            if (horizontalOffset > maxHorizontalRun) {
                horizontalOffset = maxHorizontalRun;
            }
            stepIndex++;
            int slopeHeight = shoulderEdgeHeight + direction * stepIndex;
            int groundHeight = groundHeightAtOffset.applyAsInt((int) Math.round(horizontalOffset));
            profile.add(new int[]{(int) Math.round(horizontalOffset), slopeHeight});

            if (direction > 0) {
                if (slopeHeight >= groundHeight) {
                    break;
                }
            } else if (direction < 0) {
                if (slopeHeight <= groundHeight) {
                    break;
                }
            }

            if (horizontalOffset >= maxHorizontalRun) {
                break;
            }
        }
        return profile;
    }

    private static int interpolateGroundHeight(int groundStart, int groundEnd, double t) {
        return (int) Math.round(groundStart * (1.0 - t) + groundEnd * t);
    }

    /**
     * 横断面地面高度：取多个采样点高度的算术平均（四舍五入）
     */
    public static int averageGroundHeight(List<Integer> sampledHeights) {
        return averageJunctionHeight(sampledHeights);
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
