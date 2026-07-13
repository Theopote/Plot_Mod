package com.plot.plugin.road.solid;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadDimensionUtils;

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
    private static final int MAX_DEDUP_KEYS = 100000; // 限制去重键最大数量，防止内存泄漏

    private final List<RoadSolidPrimitive> primitives = new ArrayList<>();
    private final Set<String> dedupKeys = new LinkedHashSet<>();

    public boolean add(RoadSolidPrimitive primitive) {
        if (primitive == null) {
            return false;
        }

        // 当去重键数量过大时，清理以防止内存泄漏
        if (dedupKeys.size() >= MAX_DEDUP_KEYS) {
            dedupKeys.clear();
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

    /**
     * 沿法线方向铺设固定方块宽度的条带（1m = 1 方块，宽度为四舍五入后的整数）。
     */
    public void addLateralStrip(
            Vec2d center,
            Vec2d leftNormal,
            int widthBlocks,
            int elevation,
            RoadSolidLayer layer,
            String materialId) {
        if (center == null || leftNormal == null || widthBlocks <= 0) {
            return;
        }
        Vec2d normal = leftNormal.lengthSquared() > 1e-12
            ? leftNormal.normalize()
            : new Vec2d(0, 1);
        int minOffset = RoadDimensionUtils.minLateralOffset(widthBlocks);
        int maxOffset = RoadDimensionUtils.maxLateralOffset(widthBlocks);
        for (int lateral = minOffset; lateral <= maxOffset; lateral++) {
            add(center.add(normal.multiply(lateral)), elevation, layer, materialId);
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

    /**
     * 清空所有图元和去重键，释放内存
     */
    public void clear() {
        primitives.clear();
        dedupKeys.clear();
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
