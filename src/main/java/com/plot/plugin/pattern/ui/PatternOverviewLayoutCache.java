package com.plot.plugin.pattern.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.plugin.pattern.model.PatternFootprint;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 区域 Tab 概览区几何缓存（地图、缩略图共用）。 */
final class PatternOverviewLayoutCache {
    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    private record Entry(
            int fingerprint,
            List<Vec2d> outerPoints,
            List<List<Vec2d>> holes,
            PolygonRegionUtils.RectBounds bounds) {
    }

    private PatternOverviewLayoutCache() {
    }

    static List<Vec2d> outerPoints(PatternFootprint footprint) {
        return geometry(footprint).outerPoints;
    }

    static List<List<Vec2d>> holes(PatternFootprint footprint) {
        return geometry(footprint).holes;
    }

    static PolygonRegionUtils.RectBounds bounds(PatternFootprint footprint) {
        return geometry(footprint).bounds;
    }

    private static Entry geometry(PatternFootprint footprint) {
        if (footprint == null) {
            return new Entry(0, List.of(), List.of(), PolygonRegionUtils.computeBounds(List.of()));
        }
        int fingerprint = footprint.geometryFingerprint();
        Entry cached = CACHE.get(footprint.getId());
        if (cached != null && cached.fingerprint == fingerprint) {
            return cached;
        }
        List<Vec2d> outerPoints = footprint.getOuterPoints();
        List<List<Vec2d>> holes = footprint.getHoles();
        Entry entry = new Entry(
            fingerprint,
            outerPoints,
            holes,
            PolygonRegionUtils.computeBounds(outerPoints, holes));
        CACHE.put(footprint.getId(), entry);
        return entry;
    }

    static void retainOnly(Set<String> footprintIds) {
        if (footprintIds == null || footprintIds.isEmpty()) {
            CACHE.clear();
            return;
        }
        Iterator<Map.Entry<String, Entry>> iterator = CACHE.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!footprintIds.contains(iterator.next().getKey())) {
                iterator.remove();
            }
        }
    }
}
