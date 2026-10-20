package com.plot.plugin.earthwork.overlay;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.geometry.RegionGeometry;

import java.util.ArrayList;
import java.util.List;

/** 土方区域画布叠加层条目（外轮廓 + 孔洞）。 */
public record EarthworkRegionOverlayEntry(
        String regionId,
        String displayName,
        List<Vec2d> outerPoints,
        List<List<Vec2d>> holes,
        EarthworkRegionOverlayState state,
        long geometryVersion,
        int colorIndex) {

    public EarthworkRegionOverlayEntry {
        outerPoints = outerPoints != null ? List.copyOf(outerPoints) : List.of();
        holes = copyHoles(holes);
    }

    public RegionGeometry toGeometry() {
        return RegionGeometry.of(outerPoints, holes);
    }

    /** 在外轮廓内且不在任何孔洞内。 */
    public boolean containsWorldPoint(double worldX, double worldY) {
        return PolygonRegionUtils.containsPoint(outerPoints, holes, new Vec2d(worldX, worldY));
    }

    /** 净面积（外环减去孔洞），用于重叠时优先选最小区域。 */
    public double absoluteArea() {
        return Math.abs(PolygonRegionUtils.computeSignedArea(outerPoints, holes));
    }

    private static List<List<Vec2d>> copyHoles(List<List<Vec2d>> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<List<Vec2d>> copy = new ArrayList<>(source.size());
        for (List<Vec2d> hole : source) {
            if (hole != null && hole.size() >= 3) {
                copy.add(List.copyOf(hole));
            }
        }
        return List.copyOf(copy);
    }
}
