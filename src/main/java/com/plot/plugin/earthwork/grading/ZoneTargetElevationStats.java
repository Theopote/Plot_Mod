package com.plot.plugin.earthwork.grading;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 从 {@link DesignTerrainGrid} 按 {@code zoneId} 聚合的设计标高统计。
 */
public record ZoneTargetElevationStats(int min, int max, int representative) {

    /**
     * 统计各分区覆盖格的目标标高 min / max / 代表值（(min+max)/2）。
     * 不含 excluded 格；无覆盖的分区不会出现在结果中。
     */
    public static Map<String, ZoneTargetElevationStats> fromGrid(DesignTerrainGrid grid) {
        if (grid == null || grid.cellCount() == 0) {
            return Map.of();
        }
        Map<String, Integer> minByZone = new HashMap<>();
        Map<String, Integer> maxByZone = new HashMap<>();
        for (DesignTerrainCell cell : grid.cells().values()) {
            if (cell == null || cell.excluded()) {
                continue;
            }
            String zoneId = cell.zoneId();
            if (zoneId == null || zoneId.isBlank()) {
                continue;
            }
            int targetY = cell.targetY();
            minByZone.merge(zoneId, targetY, Math::min);
            maxByZone.merge(zoneId, targetY, Math::max);
        }
        if (minByZone.isEmpty()) {
            return Map.of();
        }
        Map<String, ZoneTargetElevationStats> stats = new HashMap<>();
        for (Map.Entry<String, Integer> entry : minByZone.entrySet()) {
            String zoneId = entry.getKey();
            int min = entry.getValue();
            int max = maxByZone.getOrDefault(zoneId, min);
            stats.put(zoneId, new ZoneTargetElevationStats(min, max, (min + max) / 2));
        }
        return Collections.unmodifiableMap(stats);
    }
}
