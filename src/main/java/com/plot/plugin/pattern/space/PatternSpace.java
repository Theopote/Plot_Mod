package com.plot.plugin.pattern.space;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.plugin.pattern.model.PatternFootprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 单次图案生成的坐标系上下文。
 * <p>
 * 所有程序化/图片图案均在 Pattern Space（画布 XZ）内采样；
 * 世界坐标仅在地形投影阶段引入。
 */
public final class PatternSpace {
    private final String seedKey;
    private final Vec2d regionCentroid;
    private final PolygonRegionUtils.RectBounds regionBounds;
    private final List<Vec2d> outerRing;
    private final List<List<Vec2d>> holes;

    public PatternSpace(
            String seedKey,
            Vec2d regionCentroid,
            PolygonRegionUtils.RectBounds regionBounds) {
        this(seedKey, regionCentroid, regionBounds, List.of(), List.of());
    }

    public PatternSpace(
            String seedKey,
            Vec2d regionCentroid,
            PolygonRegionUtils.RectBounds regionBounds,
            List<Vec2d> outerRing,
            List<List<Vec2d>> holes) {
        this.seedKey = seedKey != null ? seedKey : "";
        this.regionCentroid = regionCentroid != null ? regionCentroid : new Vec2d(0, 0);
        this.regionBounds = Objects.requireNonNull(regionBounds, "regionBounds");
        this.outerRing = copyRing(outerRing);
        this.holes = copyHoles(holes);
    }

    public static PatternSpace fromFootprint(PatternFootprint footprint) {
        List<Vec2d> outerPoints = footprint.getOuterPoints();
        List<List<Vec2d>> holes = footprint.getHoles();
        return new PatternSpace(
            footprint.getId(),
            footprint.computeCentroid(),
            PolygonRegionUtils.computeBounds(outerPoints, holes),
            outerPoints,
            holes);
    }

    public String seedKey() {
        return seedKey;
    }

    public Vec2d regionCentroid() {
        return regionCentroid;
    }

    public PolygonRegionUtils.RectBounds regionBounds() {
        return regionBounds;
    }

    public List<Vec2d> outerRing() {
        return outerRing;
    }

    public List<List<Vec2d>> holes() {
        return holes;
    }

    private static List<Vec2d> copyRing(List<Vec2d> ring) {
        if (ring == null || ring.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(PolygonRegionUtils.copyPoints(ring));
    }

    private static List<List<Vec2d>> copyHoles(List<List<Vec2d>> holes) {
        if (holes == null || holes.isEmpty()) {
            return List.of();
        }
        List<List<Vec2d>> copied = new ArrayList<>(holes.size());
        for (List<Vec2d> hole : holes) {
            copied.add(copyRing(hole));
        }
        return Collections.unmodifiableList(copied);
    }
}
