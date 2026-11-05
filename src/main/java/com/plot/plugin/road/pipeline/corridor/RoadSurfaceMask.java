package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidLayerPriority;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 世界 XZ 列上的设计表面 mask。 */
public final class RoadSurfaceMask {
    private final Map<ColumnKey, SurfaceCell> cells = new LinkedHashMap<>();

    public void put(ColumnKey key, SurfaceCell cell) {
        if (key == null || cell == null) {
            return;
        }
        SurfaceCell existing = cells.get(key);
        if (existing == null) {
            cells.put(key, cell);
            return;
        }
        int layerCmp = RoadSolidLayerPriority.compare(cell.layer(), existing.layer());
        if (layerCmp > 0) {
            cells.put(key, cell);
            return;
        }
        // 同层纵坡台阶：同柱多次写入时保留较高路面，避免后写低台阶盖掉高台阶后
        // clearance 仍按高 designY 起算、漏清路面上一格地形。
        if (layerCmp == 0 && cell.surfaceY() >= existing.surfaceY()) {
            cells.put(key, cell);
        }
    }

    public SurfaceCell get(ColumnKey key) {
        return cells.get(key);
    }

    public Set<Map.Entry<ColumnKey, SurfaceCell>> entries() {
        return cells.entrySet();
    }

    public int size() {
        return cells.size();
    }

    public boolean isRoadSurfaceLayer(RoadSolidLayer layer) {
        return layer == RoadSolidLayer.ROAD
            || layer == RoadSolidLayer.SHOULDER
            || layer == RoadSolidLayer.SIDEWALK
            || layer == RoadSolidLayer.BIKE_LANE;
    }

    void remove(ColumnKey key) {
        if (key != null) {
            cells.remove(key);
        }
    }
}
