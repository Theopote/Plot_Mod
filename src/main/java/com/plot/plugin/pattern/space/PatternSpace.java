package com.plot.plugin.pattern.space;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.plugin.pattern.model.PatternFootprint;

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

    public PatternSpace(
            String seedKey,
            Vec2d regionCentroid,
            PolygonRegionUtils.RectBounds regionBounds) {
        this.seedKey = seedKey != null ? seedKey : "";
        this.regionCentroid = regionCentroid != null ? regionCentroid : new Vec2d(0, 0);
        this.regionBounds = Objects.requireNonNull(regionBounds, "regionBounds");
    }

    public static PatternSpace fromFootprint(PatternFootprint footprint) {
        List<Vec2d> outerPoints = footprint.getOuterPoints();
        return new PatternSpace(
            footprint.getId(),
            footprint.computeCentroid(),
            PolygonRegionUtils.computeBounds(outerPoints));
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
}
