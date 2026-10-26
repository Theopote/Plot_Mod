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
        return computeActualSlopePercent(
                targetStart, (double) targetEnd, segmentDistance);
    }

    public static double computeActualSlopePercent(
            double targetStart,
            double targetEnd,
            double segmentDistance) {
        if (segmentDistance <= 0) {
            return 0.0;
        }
        return Math.abs(targetEnd - targetStart) / segmentDistance * 100.0;
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
            int verticalChange = Math.round(offset / slopeRatio);
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
