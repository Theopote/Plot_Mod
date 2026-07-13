package com.plot.plugin.road;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * 道路坡度与高度计算的纯数值逻辑（不依赖 Minecraft World）
 */
public final class RoadSlopeUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/RoadSlopeUtils");

    private RoadSlopeUtils() {
    }

    /**
     * 跨分段累积 fractional 高程变化，避免短分段上 (int) maxRise 截断为 0。
     */
    public static final class ElevationAccumulator {
        private double remainder;

        public int advance(int currentHeight, double continuousDelta) {
            remainder += continuousDelta;
            int step = remainder >= 0
                ? (int) Math.floor(remainder + 1e-9)
                : (int) Math.ceil(remainder - 1e-9);
            if (step != 0) {
                remainder -= step;
            }
            return currentHeight + step;
        }

        double remainderForTests() {
            return remainder;
        }
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
        return computeTargetEndHeight(
            currentHeight, groundStart, groundEnd, segmentDistance, maxSlopePercent, null);
    }

    public static int computeTargetEndHeight(
            int currentHeight,
            int groundStart,
            int groundEnd,
            double segmentDistance,
            float maxSlopePercent,
            ElevationAccumulator accumulator) {
        double maxRise = segmentDistance * maxSlopePercent / 100.0;
        int idealRise = groundEnd - groundStart;

        if (Math.abs(idealRise) <= maxRise + 1e-9) {
            int deltaToGround = groundEnd - currentHeight;
            if (accumulator != null) {
                return accumulator.advance(currentHeight, deltaToGround);
            }
            return groundEnd;
        }
        double continuousStep = idealRise > 0 ? maxRise : -maxRise;
        if (accumulator != null) {
            return accumulator.advance(currentHeight, continuousStep);
        }
        return currentHeight + (int) continuousStep;
    }

    /**
     * 将目标高度向 fromHeight 拉近，并遵守坡度限制（用于手动标高修正）。
     */
    public static int clampTowardTarget(
            int fromHeight,
            int targetHeight,
            double distance,
            float maxSlopePercent) {
        return clampTowardTarget(fromHeight, targetHeight, distance, maxSlopePercent, null);
    }

    public static int clampTowardTarget(
            int fromHeight,
            int targetHeight,
            double distance,
            float maxSlopePercent,
            ElevationAccumulator accumulator) {
        double maxRise = distance * maxSlopePercent / 100.0;
        int delta = targetHeight - fromHeight;
        if (Math.abs(delta) <= maxRise + 1e-9) {
            if (accumulator != null) {
                return accumulator.advance(fromHeight, delta);
            }
            return targetHeight;
        }
        double continuousStep = delta > 0 ? maxRise : -maxRise;
        if (accumulator != null) {
            return accumulator.advance(fromHeight, continuousStep);
        }
        return fromHeight + (int) continuousStep;
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
        return computeChainedTargetHeights(
            segmentDistances,
            groundStarts,
            groundEnds,
            maxSlopePercents,
            manualStartHeight,
            null,
            maxContinuousSlopeLength,
            relaxedSlopeLength,
            relaxedSlopePercent
        );
    }

    /**
     * 沿多段路径链式计算目标高度，并限制单一方向的连续爬坡/下坡长度。
     * 若指定 {@code manualEndHeight}，则在正向计算后反向修正，使链条末端精确等于该值。
     */
    public static List<Integer> computeChainedTargetHeights(
            List<Double> segmentDistances,
            List<Integer> groundStarts,
            List<Integer> groundEnds,
            List<Float> maxSlopePercents,
            Integer manualStartHeight,
            Integer manualEndHeight,
            double maxContinuousSlopeLength,
            double relaxedSlopeLength,
            float relaxedSlopePercent) {
        List<Integer> targetEnds = computeChainedTargetHeightsForward(
            segmentDistances,
            groundStarts,
            groundEnds,
            maxSlopePercents,
            manualStartHeight,
            maxContinuousSlopeLength,
            relaxedSlopeLength,
            relaxedSlopePercent
        );
        if (manualEndHeight == null || targetEnds.isEmpty()) {
            return targetEnds;
        }
        return applyManualEndHeightCorrection(
            targetEnds,
            segmentDistances,
            maxSlopePercents,
            manualStartHeight,
            manualEndHeight
        );
    }

    private static List<Integer> computeChainedTargetHeightsForward(
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
        ElevationAccumulator elevationAccumulator = new ElevationAccumulator();

        int continuousDirection = 0;
        double continuousRunLength = 0.0;
        double relaxedRemaining = 0.0;
        Float previousSegmentSlope = null;

        for (int i = 0; i < size; i++) {
            double segmentDistance = segmentDistances.get(i);
            int groundStart = groundStarts.get(i);
            int groundEnd = groundEnds.get(i);
            float maxSlopePercent = maxSlopePercents.get(i);
            if (previousSegmentSlope != null && previousSegmentSlope != maxSlopePercent) {
                elevationAccumulator = new ElevationAccumulator();
            }
            previousSegmentSlope = maxSlopePercent;

            double processedInSegment = 0.0;
            while (processedInSegment < segmentDistance - 1e-9) {
                double remainingInSegment = segmentDistance - processedInSegment;
                float effectiveSlope = maxSlopePercent;
                double chunkDistance = remainingInSegment;
                boolean enteringRelaxedSlope = false;

                if (slopeLengthLimitEnabled) {
                    if (relaxedRemaining > 0.0) {
                        effectiveSlope = relaxedSlopePercent;
                        chunkDistance = Math.min(remainingInSegment, relaxedRemaining);
                    } else if (continuousRunLength >= maxContinuousSlopeLength) {
                        relaxedRemaining = relaxedSlopeLength;
                        effectiveSlope = relaxedSlopePercent;
                        chunkDistance = Math.min(remainingInSegment, relaxedRemaining);
                        enteringRelaxedSlope = true;
                    }
                }

                if (enteringRelaxedSlope) {
                    elevationAccumulator = new ElevationAccumulator();
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
                    effectiveSlope,
                    elevationAccumulator
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
                    effectiveSlope,
                    elevationAccumulator
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

    private static List<Integer> applyManualEndHeightCorrection(
            List<Integer> targetEnds,
            List<Double> segmentDistances,
            List<Float> maxSlopePercents,
            Integer manualStartHeight,
            int manualEndHeight) {
        int size = targetEnds.size();
        int lastIdx = size - 1;
        int previousLast = targetEnds.get(lastIdx);
        targetEnds.set(lastIdx, manualEndHeight);

        boolean slopeViolation = previousLast != manualEndHeight;
        for (int i = lastIdx; i > 0; i--) {
            int segmentEnd = targetEnds.get(i);
            int previousSegmentEnd = targetEnds.get(i - 1);
            int adjustedStart = clampTowardTarget(
                segmentEnd,
                previousSegmentEnd,
                segmentDistances.get(i),
                maxSlopePercents.get(i)
            );
            if (adjustedStart != previousSegmentEnd) {
                targetEnds.set(i - 1, adjustedStart);
                slopeViolation = true;
            }
        }

        if (manualStartHeight != null) {
            int reachableEnd = clampTowardTarget(
                manualStartHeight,
                targetEnds.get(0),
                segmentDistances.get(0),
                maxSlopePercents.get(0)
            );
            if (reachableEnd != targetEnds.get(0)) {
                slopeViolation = true;
            }
        }

        if (slopeViolation) {
            LOGGER.warn("该边长度不足以在坡度限制内连接两端强制标高");
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

        for (int offset = 1; offset <= maxHorizontalRun; offset++) {
            int verticalChange = (int) Math.round(offset / slopeRatio);
            int slopeHeight = shoulderEdgeHeight + direction * verticalChange;
            int groundHeight = groundHeightAtOffset.applyAsInt(offset);
            profile.add(new int[]{offset, slopeHeight});

            if (direction > 0) {
                if (slopeHeight >= groundHeight) {
                    break;
                }
            } else if (direction < 0) {
                if (slopeHeight <= groundHeight) {
                    break;
                }
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
        return resolveJunctionHeight(connectedEdgeHeights).height();
    }

    /**
     * 路口高程决议结果：目标高度 + 汇聚边高程散布。
     */
    public record JunctionHeightResolution(int height, int min, int max, int spread) {
        /** 散布超过该阈值时视为显著不一致（易出现台阶）。 */
        public static final int SIGNIFICANT_SPREAD_THRESHOLD = 2;

        public boolean isSignificantMismatch() {
            return spread > SIGNIFICANT_SPREAD_THRESHOLD;
        }
    }

    /**
     * 根据各相连边在节点处的自然高程，决议路口统一标高。
     * 策略：算术平均（四舍五入）；空列表回落到默认海平面 64。
     */
    public static JunctionHeightResolution resolveJunctionHeight(List<Integer> connectedEdgeHeights) {
        if (connectedEdgeHeights == null || connectedEdgeHeights.isEmpty()) {
            return new JunctionHeightResolution(64, 64, 64, 0);
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        long sum = 0;
        for (int height : connectedEdgeHeights) {
            min = Math.min(min, height);
            max = Math.max(max, height);
            sum += height;
        }
        int average = (int) Math.round(sum / (double) connectedEdgeHeights.size());
        return new JunctionHeightResolution(average, min, max, max - min);
    }
}
