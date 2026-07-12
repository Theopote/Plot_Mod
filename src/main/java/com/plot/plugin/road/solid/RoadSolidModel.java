package com.plot.plugin.road.solid;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 道路实体图元集合（几何层输出，供 rasterizer 或未来 mesh exporter 消费）。
 */
public final class RoadSolidModel {
    private final List<RoadSolidPrimitive> primitives = new ArrayList<>();
    private final Set<String> dedupKeys = new LinkedHashSet<>();

    public boolean add(RoadSolidPrimitive primitive) {
        if (primitive == null) {
            return false;
        }
        if (!dedupKeys.add(primitive.dedupKey())) {
            return false;
        }
        primitives.add(primitive);
        return true;
    }

    public boolean add(Vec2d planPoint, int elevation, RoadSolidLayer layer) {
        return add(new RoadSolidPrimitive(planPoint, elevation, layer));
    }

    public boolean add(Vec2d planPoint, int elevation, RoadSolidLayer layer, String materialId) {
        return add(new RoadSolidPrimitive(planPoint, elevation, layer, materialId));
    }

    public void addSpan(Vec2d left, Vec2d right, int elevation, RoadSolidLayer layer, String materialId) {
        for (Vec2d point : RoadVoxelRasterizer.sampleSpanPoints(left, right)) {
            add(point, elevation, layer, materialId);
        }
    }

    public void addStrip(Vec2d center, int width, int elevation, RoadSolidLayer layer, String materialId) {
        if (center == null || width <= 0) {
            return;
        }
        int half = Math.max(0, (int) Math.ceil(width / 2.0));
        double radiusSquared = Math.max(0.25, width * width / 4.0);
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                if ((dx * dx + dz * dz) > radiusSquared) {
                    continue;
                }
                add(new Vec2d(center.x + dx, center.y + dz), elevation, layer, materialId);
            }
        }
    }

    public List<RoadSolidPrimitive> primitives() {
        return Collections.unmodifiableList(primitives);
    }

    public List<RoadSolidPrimitive> byLayer(RoadSolidLayer layer) {
        return primitives.stream()
            .filter(primitive -> primitive.layer() == layer)
            .collect(Collectors.toList());
    }

    public int count(RoadSolidLayer layer) {
        return (int) primitives.stream().filter(primitive -> primitive.layer() == layer).count();
    }

    public boolean isEmpty() {
        return primitives.isEmpty();
    }

    public void addAll(RoadSolidModel other) {
        if (other == null) {
            return;
        }
        for (RoadSolidPrimitive primitive : other.primitives) {
            add(primitive);
        }
    }
}
