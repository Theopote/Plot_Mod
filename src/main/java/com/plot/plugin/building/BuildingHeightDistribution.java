package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * District Massing Phase E：对选中建筑应用高度/层数分布，形成城市天际线。
 */
public final class BuildingHeightDistribution {

    public enum Mode {
        /** 全部同一层数（使用 {@link Settings#maxFloors()}） */
        UNIFORM,
        /** min–max 均匀随机 */
        RANDOM,
        /** 占地越大越高 */
        AREA_BASED,
        /** 越靠近选中几何中心越高 */
        CENTER_HIGHER,
        /** 越靠近外围越高 */
        EDGE_HIGHER,
        /** 沿选中包围盒 X 轴从低到高渐变 */
        GRADIENT
    }

    public record Settings(Mode mode, int minFloors, int maxFloors, long seed) {
        public Settings {
            Objects.requireNonNull(mode, "mode");
            int lo = Math.max(1, Math.min(minFloors, maxFloors));
            int hi = Math.max(1, Math.max(minFloors, maxFloors));
            minFloors = Math.min(32, lo);
            maxFloors = Math.min(32, Math.max(lo, hi));
        }

        public static Settings uniform(int floors) {
            int f = clampFloors(floors);
            return new Settings(Mode.UNIFORM, f, f, 0L);
        }

        public static Settings of(Mode mode, int minFloors, int maxFloors) {
            return new Settings(mode, minFloors, maxFloors, System.nanoTime());
        }

        public static Settings of(Mode mode, int minFloors, int maxFloors, long seed) {
            return new Settings(mode, minFloors, maxFloors, seed);
        }
    }

    public record ApplyResult(int updated, int skipped) {
    }

    private BuildingHeightDistribution() {
    }

    public static ApplyResult apply(Collection<BuildingFootprint> buildings, Settings settings) {
        Objects.requireNonNull(settings, "settings");
        if (buildings == null || buildings.isEmpty()) {
            return new ApplyResult(0, 0);
        }

        List<BuildingFootprint> targets = new ArrayList<>();
        for (BuildingFootprint building : buildings) {
            if (building != null) {
                targets.add(building);
            }
        }
        if (targets.isEmpty()) {
            return new ApplyResult(0, buildings.size());
        }

        int[] floors = computeFloors(targets, settings);
        int updated = 0;
        for (int i = 0; i < targets.size(); i++) {
            targets.get(i).setFloors(floors[i]);
            updated++;
        }
        return new ApplyResult(updated, buildings.size() - updated);
    }

    static int[] computeFloors(List<BuildingFootprint> buildings, Settings settings) {
        int n = buildings.size();
        int[] floors = new int[n];
        int min = settings.minFloors();
        int max = settings.maxFloors();

        switch (settings.mode()) {
            case UNIFORM -> {
                for (int i = 0; i < n; i++) {
                    floors[i] = max;
                }
            }
            case RANDOM -> {
                Random random = new Random(settings.seed());
                for (int i = 0; i < n; i++) {
                    floors[i] = min + random.nextInt(max - min + 1);
                }
            }
            case AREA_BASED -> applyByNormalizedMetric(buildings, floors, min, max, true,
                building -> building.computeArea());
            case CENTER_HIGHER -> applyByDistanceToCentroid(buildings, floors, min, max, true);
            case EDGE_HIGHER -> applyByDistanceToCentroid(buildings, floors, min, max, false);
            case GRADIENT -> applyByGradientX(buildings, floors, min, max);
        }
        return floors;
    }

    private static void applyByNormalizedMetric(
            List<BuildingFootprint> buildings,
            int[] floors,
            int min,
            int max,
            boolean higherWhenLarger,
            java.util.function.ToDoubleFunction<BuildingFootprint> metric) {
        double[] values = new double[buildings.size()];
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < buildings.size(); i++) {
            values[i] = metric.applyAsDouble(buildings.get(i));
            lo = Math.min(lo, values[i]);
            hi = Math.max(hi, values[i]);
        }
        double span = hi - lo;
        for (int i = 0; i < buildings.size(); i++) {
            double t = span < 1e-9 ? 0.5 : (values[i] - lo) / span;
            if (!higherWhenLarger) {
                t = 1.0 - t;
            }
            floors[i] = lerpFloors(min, max, t);
        }
    }

    private static void applyByDistanceToCentroid(
            List<BuildingFootprint> buildings,
            int[] floors,
            int min,
            int max,
            boolean centerHigher) {
        Vec2d center = selectionCentroid(buildings);
        double[] distances = new double[buildings.size()];
        double maxDist = 0.0;
        for (int i = 0; i < buildings.size(); i++) {
            Vec2d c = BuildingGeometryUtils.computeCentroid(buildings.get(i).getOuterPoints());
            double d = c.distance(center);
            distances[i] = d;
            maxDist = Math.max(maxDist, d);
        }
        for (int i = 0; i < buildings.size(); i++) {
            double t = maxDist < 1e-9 ? 0.0 : distances[i] / maxDist;
            // centerHigher: t=0 → max；edgeHigher: t=0 → min
            double factor = centerHigher ? (1.0 - t) : t;
            floors[i] = lerpFloors(min, max, factor);
        }
    }

    private static void applyByGradientX(
            List<BuildingFootprint> buildings,
            int[] floors,
            int min,
            int max) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        Vec2d[] centroids = new Vec2d[buildings.size()];
        for (int i = 0; i < buildings.size(); i++) {
            Vec2d c = BuildingGeometryUtils.computeCentroid(buildings.get(i).getOuterPoints());
            centroids[i] = c;
            minX = Math.min(minX, c.x);
            maxX = Math.max(maxX, c.x);
        }
        double span = maxX - minX;
        for (int i = 0; i < buildings.size(); i++) {
            double t = span < 1e-9 ? 0.5 : (centroids[i].x - minX) / span;
            floors[i] = lerpFloors(min, max, t);
        }
    }

    private static Vec2d selectionCentroid(List<BuildingFootprint> buildings) {
        double x = 0.0;
        double y = 0.0;
        int count = 0;
        for (BuildingFootprint building : buildings) {
            Vec2d c = BuildingGeometryUtils.computeCentroid(building.getOuterPoints());
            x += c.x;
            y += c.y;
            count++;
        }
        if (count == 0) {
            return new Vec2d(0, 0);
        }
        return new Vec2d(x / count, y / count);
    }

    static int lerpFloors(int min, int max, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return clampFloors((int) Math.round(min + (max - min) * clamped));
    }

    static int clampFloors(int floors) {
        return Math.max(1, Math.min(32, floors));
    }
}
