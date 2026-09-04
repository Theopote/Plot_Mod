package com.plot.core.geometry.polygon;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.geometry.RegionGeometry;

import java.util.List;

/**
 * 多边形栅格化：格心采样与点包含。
 */
public final class PolygonRasterizer {
    private PolygonRasterizer() {
    }

    public static List<Vec2d> collectCellCenters(List<Vec2d> outerRing) {
        return PolygonRegionUtils.collectFootprintCellCenters(outerRing);
    }

    public static List<Vec2d> collectCellCenters(List<Vec2d> outerRing, List<List<Vec2d>> holes) {
        return PolygonRegionUtils.collectFootprintCellCenters(outerRing, holes);
    }

    public static List<Vec2d> collectCellCenters(RegionGeometry geometry) {
        return PolygonRegionUtils.collectFootprintCellCenters(geometry);
    }

    public static boolean containsCellCenter(List<Vec2d> outerRing, int blockX, int blockZ) {
        return PolygonBoolean.contains(outerRing, new Vec2d(blockX + 0.5, blockZ + 0.5));
    }
}
