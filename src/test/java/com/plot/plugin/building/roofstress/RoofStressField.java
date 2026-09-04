package com.plot.plugin.building.roofstress;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.polygon.PolygonBoolean;
import com.plot.core.geometry.polygon.PolygonRasterizer;
import com.plot.core.geometry.polygon.StraightSkeleton;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单次 HIP 屋顶高度场采样结果。
 */
public final class RoofStressField {
    private final StraightSkeleton.Result skeleton;
    private final int pitch;
    private final Map<Long, Integer> riseByKey;
    private final Map<Long, Vec2d> cellByKey;
    private final int minRise;
    private final int maxRise;
    private final int positiveCellCount;

    private RoofStressField(
            StraightSkeleton.Result skeleton,
            int pitch,
            Map<Long, Integer> riseByKey,
            Map<Long, Vec2d> cellByKey,
            int minRise,
            int maxRise,
            int positiveCellCount) {
        this.skeleton = skeleton;
        this.pitch = pitch;
        this.riseByKey = riseByKey;
        this.cellByKey = cellByKey;
        this.minRise = minRise;
        this.maxRise = maxRise;
        this.positiveCellCount = positiveCellCount;
    }

    public static RoofStressField sample(List<Vec2d> polygon, int pitch) {
        StraightSkeleton.Result skeleton = StraightSkeleton.compute(polygon);
        if (!skeleton.success()) {
            return new RoofStressField(
                skeleton, Math.max(1, pitch), Map.of(), Map.of(), 0, 0, 0);
        }
        int p = Math.max(1, pitch);
        Map<Long, Integer> riseByKey = new HashMap<>();
        Map<Long, Vec2d> cellByKey = new HashMap<>();
        int minRise = Integer.MAX_VALUE;
        int maxRise = Integer.MIN_VALUE;
        int positive = 0;
        for (Vec2d cell : PolygonRasterizer.collectCellCenters(skeleton.polygon())) {
            if (!PolygonBoolean.contains(skeleton.polygon(), cell)) {
                continue;
            }
            int rise = (int) Math.floor(skeleton.skeletalTime(cell) / p);
            long key = cellKey(cell);
            riseByKey.put(key, rise);
            cellByKey.put(key, cell);
            minRise = Math.min(minRise, rise);
            maxRise = Math.max(maxRise, rise);
            if (rise > 0) {
                positive++;
            }
        }
        if (riseByKey.isEmpty()) {
            minRise = 0;
            maxRise = 0;
        }
        return new RoofStressField(
            skeleton,
            p,
            Collections.unmodifiableMap(riseByKey),
            Collections.unmodifiableMap(cellByKey),
            minRise,
            maxRise,
            positive
        );
    }

    public StraightSkeleton.Result skeleton() {
        return skeleton;
    }

    public int pitch() {
        return pitch;
    }

    public Map<Long, Integer> riseByKey() {
        return riseByKey;
    }

    public Map<Long, Vec2d> cellByKey() {
        return cellByKey;
    }

    public int minRise() {
        return minRise;
    }

    public int maxRise() {
        return maxRise;
    }

    public int positiveCellCount() {
        return positiveCellCount;
    }

    public int cellCount() {
        return riseByKey.size();
    }

    public Integer riseAt(Vec2d cell) {
        return riseByKey.get(cellKey(cell));
    }

    public static long cellKey(Vec2d cell) {
        int x = (int) Math.round(cell.x * 2);
        int y = (int) Math.round(cell.y * 2);
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    public static List<Vec2d> orthogonalNeighbors(Vec2d cell) {
        return List.of(
            new Vec2d(cell.x + 1, cell.y),
            new Vec2d(cell.x - 1, cell.y),
            new Vec2d(cell.x, cell.y + 1),
            new Vec2d(cell.x, cell.y - 1)
        );
    }
}
