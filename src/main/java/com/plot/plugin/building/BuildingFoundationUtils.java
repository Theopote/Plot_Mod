package com.plot.plugin.building;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 建筑地基标高计算（纯函数）
 */
public final class BuildingFoundationUtils {
    public static final double CUT_WEIGHT = 1.0;
    public static final double FILL_WEIGHT = 1.15;
    public static final int BALANCED_SEARCH_RADIUS = 4;

    private BuildingFoundationUtils() {
    }

    public record EarthworkEstimate(int cut, int fill, double weightedCost) {
        public int total() {
            return cut + fill;
        }
    }

    /**
     * 根据地面高度采样计算建筑±0标高。
     * 优先级：手动覆盖 &gt; 土方垫层设计标高 &gt; 地形采样众数（并列取较高值）。
     */
    public static int computeBaseElevation(
            List<Integer> groundHeightSamples,
            Integer manualOverride,
            Integer earthworkPadElevation) {
        if (manualOverride != null) {
            return manualOverride;
        }
        if (earthworkPadElevation != null) {
            return earthworkPadElevation;
        }
        return computeDominantElevation(groundHeightSamples);
    }

    /**
     * 根据地面高度采样计算建筑±0标高。
     * 手动指定时直接返回；否则取众数，并列时取较高值。
     */
    public static int computeBaseElevation(List<Integer> groundHeightSamples, Integer manualOverride) {
        if (manualOverride != null) {
            return manualOverride;
        }
        return computeDominantElevation(groundHeightSamples);
    }

    /** 众数；并列取较高值。空样本 → 64。 */
    public static int computeDominantElevation(List<Integer> groundHeightSamples) {
        if (groundHeightSamples == null || groundHeightSamples.isEmpty()) {
            return 64;
        }

        Map<Integer, Integer> counts = new HashMap<>();
        for (Integer height : groundHeightSamples) {
            if (height == null) {
                continue;
            }
            counts.merge(height, 1, Integer::sum);
        }
        if (counts.isEmpty()) {
            return 64;
        }

        int maxCount = 0;
        for (int count : counts.values()) {
            maxCount = Math.max(maxCount, count);
        }

        int chosen = Integer.MIN_VALUE;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == maxCount) {
                chosen = Math.max(chosen, entry.getKey());
            }
        }
        return chosen;
    }

    /** 整数中位数；空样本 → 64。 */
    public static int computeMedianElevation(List<Integer> groundHeightSamples) {
        List<Integer> sorted = sortedSamples(groundHeightSamples);
        if (sorted.isEmpty()) {
            return 64;
        }
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return (int) Math.round((sorted.get(mid - 1) + sorted.get(mid)) / 2.0);
        }
        return sorted.get(mid);
    }

    /**
     * Balanced 标高：在 dominant ± {@link #BALANCED_SEARCH_RADIUS}（并 clamp 到 min/max）内
     * 最小化加权切填成本；并列优先更接近 dominant，再并列取较高值。
     */
    public static int computeBalancedElevation(List<Integer> groundHeightSamples) {
        List<Integer> samples = sortedSamples(groundHeightSamples);
        if (samples.isEmpty()) {
            return 64;
        }
        int min = samples.getFirst();
        int max = samples.getLast();
        int dominant = computeDominantElevation(samples);
        int lo = Math.max(min, dominant - BALANCED_SEARCH_RADIUS);
        int hi = Math.min(max, dominant + BALANCED_SEARCH_RADIUS);
        if (lo > hi) {
            lo = min;
            hi = max;
        }

        int bestH = dominant;
        double bestCost = Double.POSITIVE_INFINITY;
        int bestDist = Integer.MAX_VALUE;
        for (int h = lo; h <= hi; h++) {
            EarthworkEstimate estimate = estimateEarthwork(samples, h);
            double cost = estimate.weightedCost();
            int dist = Math.abs(h - dominant);
            if (cost < bestCost - 1e-9
                    || (Math.abs(cost - bestCost) <= 1e-9 && dist < bestDist)
                    || (Math.abs(cost - bestCost) <= 1e-9 && dist == bestDist && h > bestH)) {
                bestCost = cost;
                bestDist = dist;
                bestH = h;
            }
        }
        return bestH;
    }

    public static EarthworkEstimate estimateEarthwork(List<Integer> groundHeightSamples, int targetElevation) {
        int cut = 0;
        int fill = 0;
        if (groundHeightSamples != null) {
            for (Integer height : groundHeightSamples) {
                if (height == null) {
                    continue;
                }
                if (height > targetElevation) {
                    cut += height - targetElevation;
                } else if (height < targetElevation) {
                    fill += targetElevation - height;
                }
            }
        }
        double cost = CUT_WEIGHT * cut + FILL_WEIGHT * fill;
        return new EarthworkEstimate(cut, fill, cost);
    }

    private static List<Integer> sortedSamples(List<Integer> groundHeightSamples) {
        if (groundHeightSamples == null || groundHeightSamples.isEmpty()) {
            return List.of();
        }
        List<Integer> sorted = new ArrayList<>();
        for (Integer height : groundHeightSamples) {
            if (height != null) {
                sorted.add(height);
            }
        }
        Collections.sort(sorted);
        return sorted;
    }
}
