package com.plot.plugin.road;

import com.plot.plugin.earthwork.EarthworkBalanceUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路引导线计算：根据路径地形样本，在起终点平衡标高之间生成平滑高度引导曲线。
 */
public final class RoadGuideLineUtils {
    private static final double MAX_ANCHOR_WINDOW = 15.0;
    private static final double ANCHOR_WINDOW_RATIO = 0.2;

    private RoadGuideLineUtils() {
    }

    public static List<Integer> computeGuideLine(
            List<Integer> groundHeightSamples,
            List<Double> cumulativeDistances,
            float fillFactor) {
        return computeGuideLine(groundHeightSamples, cumulativeDistances, fillFactor, null, null);
    }

    /**
     * @param startAnchorOverride 起点手动标高（非 null 时直接作为起点锚点）
     * @param endAnchorOverride   终点手动标高（非 null 时直接作为终点锚点）
     */
    public static List<Integer> computeGuideLine(
            List<Integer> groundHeightSamples,
            List<Double> cumulativeDistances,
            float fillFactor,
            Integer startAnchorOverride,
            Integer endAnchorOverride) {
        if (groundHeightSamples == null
            || cumulativeDistances == null
            || groundHeightSamples.isEmpty()
            || cumulativeDistances.size() != groundHeightSamples.size()) {
            return List.of();
        }

        double totalLength = cumulativeDistances.getLast();
        double anchorWindow = Math.min(totalLength * ANCHOR_WINDOW_RATIO, MAX_ANCHOR_WINDOW);
        boolean shortPath = totalLength < MAX_ANCHOR_WINDOW * 2.0;
        if (shortPath) {
            int constant = resolveShortPathAnchor(
                groundHeightSamples, fillFactor, startAnchorOverride, endAnchorOverride);
            List<Integer> guideLine = new ArrayList<>(groundHeightSamples.size());
            for (int i = 0; i < groundHeightSamples.size(); i++) {
                guideLine.add(constant);
            }
            return guideLine;
        }

        int startAnchor = resolveAnchor(
            collectWindowSamples(groundHeightSamples, cumulativeDistances, 0.0, anchorWindow),
            fillFactor,
            startAnchorOverride);
        int endAnchor = resolveAnchor(
            collectWindowSamples(
                groundHeightSamples, cumulativeDistances, totalLength - anchorWindow, totalLength),
            fillFactor,
            endAnchorOverride);

        List<Integer> guideLine = new ArrayList<>(groundHeightSamples.size());
        for (int i = 0; i < groundHeightSamples.size(); i++) {
            double distance = cumulativeDistances.get(i);
            double ratio = totalLength <= 1e-9 ? 0.0 : distance / totalLength;
            guideLine.add((int) Math.round(startAnchor + (endAnchor - startAnchor) * ratio));
        }
        return guideLine;
    }

    private static int resolveShortPathAnchor(
            List<Integer> allSamples,
            float fillFactor,
            Integer startAnchorOverride,
            Integer endAnchorOverride) {
        if (startAnchorOverride != null && endAnchorOverride != null) {
            return (int) Math.round((startAnchorOverride + endAnchorOverride) / 2.0);
        }
        if (startAnchorOverride != null) {
            return startAnchorOverride;
        }
        if (endAnchorOverride != null) {
            return endAnchorOverride;
        }
        return EarthworkBalanceUtils.findBalancedElevation(allSamples, fillFactor);
    }

    private static int resolveAnchor(List<Integer> windowSamples, float fillFactor, Integer override) {
        if (override != null) {
            return override;
        }
        if (windowSamples.isEmpty()) {
            return EarthworkBalanceUtils.findBalancedElevation(List.of(), fillFactor);
        }
        return EarthworkBalanceUtils.findBalancedElevation(windowSamples, fillFactor);
    }

    private static List<Integer> collectWindowSamples(
            List<Integer> groundHeightSamples,
            List<Double> cumulativeDistances,
            double windowStart,
            double windowEnd) {
        List<Integer> samples = new ArrayList<>();
        for (int i = 0; i < groundHeightSamples.size(); i++) {
            double distance = cumulativeDistances.get(i);
            if (distance >= windowStart - 1e-9 && distance <= windowEnd + 1e-9) {
                samples.add(groundHeightSamples.get(i));
            }
        }
        return samples;
    }
}
