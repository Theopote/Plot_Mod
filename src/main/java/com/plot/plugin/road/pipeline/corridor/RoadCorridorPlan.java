package com.plot.plugin.road.pipeline.corridor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 单次 edge 构建的不可变施工计划（mask + envelope）。 */
public final class RoadCorridorPlan {
    private final RoadSurfaceMask surfaceMask = new RoadSurfaceMask();
    private final Map<ColumnKey, ClearanceCell> clearanceCells = new LinkedHashMap<>();
    private final Map<ColumnKey, SlopeColumnCell> slopeCells = new LinkedHashMap<>();

    public RoadSurfaceMask surfaceMask() {
        return surfaceMask;
    }

    public ClearanceCell clearanceCell(ColumnKey key) {
        return clearanceCells.get(key);
    }

    public Set<Map.Entry<ColumnKey, ClearanceCell>> clearanceEntries() {
        return clearanceCells.entrySet();
    }

    public SlopeColumnCell slopeCell(ColumnKey key) {
        return slopeCells.get(key);
    }

    public Set<Map.Entry<ColumnKey, SlopeColumnCell>> slopeEntries() {
        return slopeCells.entrySet();
    }

    void putClearance(ColumnKey key, ClearanceCell cell) {
        if (key == null || cell == null) {
            return;
        }
        ClearanceCell existing = clearanceCells.get(key);
        if (existing == null || cell.clearTopY() > existing.clearTopY()) {
            clearanceCells.put(key, cell);
        } else if (cell.clearTopY() == existing.clearTopY()
                && cell.designSurfaceY() > existing.designSurfaceY()) {
            clearanceCells.put(key, cell);
        }
    }

    SlopeColumnCell slopeCellOrCreate(ColumnKey key, com.plot.api.geometry.Vec2d planPoint) {
        return slopeCells.computeIfAbsent(key, ignored -> new SlopeColumnCell(planPoint));
    }
}
