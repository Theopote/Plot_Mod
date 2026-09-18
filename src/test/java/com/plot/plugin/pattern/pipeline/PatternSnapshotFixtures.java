package com.plot.plugin.pattern.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.plugin.pattern.space.PatternSpace;

import java.util.List;

final class PatternSnapshotFixtures {
    private PatternSnapshotFixtures() {
    }

    static PatternSpace squareSpace(double minX, double maxX, double minZ, double maxZ) {
        List<Vec2d> outer = List.of(
            new Vec2d(minX, minZ),
            new Vec2d(maxX, minZ),
            new Vec2d(maxX, maxZ),
            new Vec2d(minX, maxZ));
        return new PatternSpace(
            "snapshot",
            new Vec2d((minX + maxX) * 0.5, (minZ + maxZ) * 0.5),
            PolygonRegionUtils.computeBounds(outer),
            outer,
            List.of());
    }
}
