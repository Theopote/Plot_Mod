package com.plot.core.geometry;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 带孔洞的多边形区域：外环 + 零或多个内环（孔洞）。
 * <p>
 * 点在内域当且仅当位于外环内且不在任一孔洞内。孔洞可用于表达中庭、水池、
 * 建筑保留区、已建构筑物、禁挖区等不参与（或单独处理）挖填的子区域。
 */
public final class RegionGeometry {
    private final List<Vec2d> outerRing;
    private final List<List<Vec2d>> holes;

    private RegionGeometry(List<Vec2d> outerRing, List<List<Vec2d>> holes) {
        this.outerRing = PolygonRegionUtils.copyPoints(outerRing);
        this.holes = copyHoleRings(holes);
    }

    public static RegionGeometry of(List<Vec2d> outerRing) {
        return new RegionGeometry(outerRing, List.of());
    }

    public static RegionGeometry of(List<Vec2d> outerRing, List<List<Vec2d>> holes) {
        return new RegionGeometry(outerRing, holes);
    }

    public static RegionGeometry empty() {
        return new RegionGeometry(List.of(), List.of());
    }

    public List<Vec2d> outerRing() {
        return PolygonRegionUtils.copyPoints(outerRing);
    }

    public List<List<Vec2d>> holes() {
        return copyHoleRings(holes);
    }

    public boolean isEmpty() {
        return outerRing.size() < 3;
    }

    public boolean hasHoles() {
        return !holes.isEmpty();
    }

    public boolean contains(Vec2d point) {
        return PolygonRegionUtils.containsPoint(outerRing, holes, point);
    }

    public double signedArea() {
        return PolygonRegionUtils.computeSignedArea(outerRing, holes);
    }

    public double area() {
        return Math.abs(signedArea());
    }

    public PolygonRegionUtils.RectBounds bounds() {
        return PolygonRegionUtils.computeBounds(outerRing, holes);
    }

    public List<Vec2d> collectFootprintCellCenters() {
        return PolygonRegionUtils.collectFootprintCellCenters(outerRing, holes);
    }

    public RegionGeometry withOuterRing(List<Vec2d> newOuterRing) {
        return new RegionGeometry(newOuterRing, holes);
    }

    public RegionGeometry withHoles(List<List<Vec2d>> newHoles) {
        return new RegionGeometry(outerRing, newHoles);
    }

    private static List<List<Vec2d>> copyHoleRings(List<List<Vec2d>> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<List<Vec2d>> copy = new ArrayList<>();
        for (List<Vec2d> ring : source) {
            List<Vec2d> normalized = PolygonRegionUtils.normalizeRegionOutline(ring);
            if (normalized.size() >= 3) {
                copy.add(PolygonRegionUtils.copyPoints(normalized));
            }
        }
        return Collections.unmodifiableList(copy);
    }
}
