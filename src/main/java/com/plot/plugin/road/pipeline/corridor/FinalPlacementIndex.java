package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.solid.RoadGenerationResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 合并后只读终态索引：最终方块列桶 + 设计期望路面列桶，供路口校验与全网验收复用。
 */
public final class FinalPlacementIndex {
    private final PlacementRecordColumnIndex placements;
    private final Map<ColumnKey, List<ExpectedRoadSurface>> expectedSurfacesByColumn;

    private FinalPlacementIndex(
            PlacementRecordColumnIndex placements,
            Map<ColumnKey, List<ExpectedRoadSurface>> expectedSurfacesByColumn) {
        this.placements = placements;
        this.expectedSurfacesByColumn = expectedSurfacesByColumn;
    }

    public static FinalPlacementIndex build(RoadGenerationResult aggregate) {
        PlacementRecordColumnIndex placements = PlacementRecordColumnIndex.build(aggregate);
        if (aggregate == null || aggregate.expectedRoadSurfaces == null || aggregate.expectedRoadSurfaces.isEmpty()) {
            return new FinalPlacementIndex(placements, Map.of());
        }
        Map<ColumnKey, List<ExpectedRoadSurface>> byColumn = new HashMap<>();
        for (ExpectedRoadSurface expected : aggregate.expectedRoadSurfaces.values()) {
            if (expected == null || expected.position() == null) {
                continue;
            }
            BlockPos pos = expected.position();
            ColumnKey key = new ColumnKey(pos.getX(), pos.getZ());
            byColumn.computeIfAbsent(key, ignored -> new ArrayList<>()).add(expected);
        }
        Map<ColumnKey, List<ExpectedRoadSurface>> frozen = new HashMap<>(byColumn.size());
        for (var entry : byColumn.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return new FinalPlacementIndex(placements, Collections.unmodifiableMap(frozen));
    }

    public PlacementRecordColumnIndex placements() {
        return placements;
    }

    public List<ExpectedRoadSurface> expectedSurfacesInColumn(ColumnKey column) {
        if (column == null) {
            return List.of();
        }
        return expectedSurfacesByColumn.getOrDefault(column, List.of());
    }

    public Map<ColumnKey, List<ExpectedRoadSurface>> expectedSurfacesByColumn() {
        return expectedSurfacesByColumn;
    }
}
