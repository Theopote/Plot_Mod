package com.plot.plugin.building;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 建筑地基标高计算（纯函数）
 */
public final class BuildingFoundationUtils {
    private BuildingFoundationUtils() {
    }

    /**
     * 根据地面高度采样计算建筑±0标高。
     * 手动指定时直接返回；否则取众数，并列时取较高值。
     */
    public static int computeBaseElevation(List<Integer> groundHeightSamples, Integer manualOverride) {
        if (manualOverride != null) {
            return manualOverride;
        }
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
}
