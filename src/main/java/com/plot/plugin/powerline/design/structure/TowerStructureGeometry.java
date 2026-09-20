package com.plot.plugin.powerline.design.structure;

import com.plot.plugin.powerline.TowerLocalPoint;

import java.util.List;

/** 塔体角点与局部几何辅助。 */
public final class TowerStructureGeometry {
    public static final int CORNER_COUNT = 4;

    private TowerStructureGeometry() {
    }

    /** 塔身截面半宽/半深（pole-local）。 */
    public record Footprint(double halfWidth, double halfDepth) {
    }

    public static TowerLocalPoint cornerPoint(TowerStation station, int cornerIndex) {
        return cornerPointAt(
            station.getHeight(),
            cornerIndex,
            station.getHalfWidth(),
            station.getHalfDepth());
    }

    public static TowerLocalPoint cornerPointAt(
            double height,
            int cornerIndex,
            double halfWidth,
            double halfDepth) {
        double lateral = cornerLateral(cornerIndex, halfWidth);
        double longitudinal = cornerLongitudinal(cornerIndex, halfDepth);
        return TowerLocalPoint.of(lateral, height, longitudinal);
    }

    /** 在 station 高度之间线性插值塔身截面。 */
    public static Footprint interpolatedFootprintAtHeight(List<TowerStation> stations, double height) {
        if (stations == null || stations.isEmpty()) {
            return new Footprint(0.0, 0.0);
        }
        List<TowerStation> sorted = stations.stream()
            .sorted(java.util.Comparator.comparingDouble(TowerStation::getHeight))
            .toList();
        TowerStation first = sorted.getFirst();
        if (height <= first.getHeight()) {
            return new Footprint(first.getHalfWidth(), first.getHalfDepth());
        }
        TowerStation last = sorted.getLast();
        if (height >= last.getHeight()) {
            return new Footprint(last.getHalfWidth(), last.getHalfDepth());
        }
        for (int i = 1; i < sorted.size(); i++) {
            TowerStation lower = sorted.get(i - 1);
            TowerStation upper = sorted.get(i);
            if (height > upper.getHeight()) {
                continue;
            }
            double span = upper.getHeight() - lower.getHeight();
            double t = span <= 1e-6 ? 0.0 : (height - lower.getHeight()) / span;
            return new Footprint(
                lerp(lower.getHalfWidth(), upper.getHalfWidth(), t),
                lerp(lower.getHalfDepth(), upper.getHalfDepth(), t));
        }
        return new Footprint(last.getHalfWidth(), last.getHalfDepth());
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    public static double cornerLateral(int cornerIndex, double halfWidth) {
        return (cornerIndex == 0 || cornerIndex == 3) ? -halfWidth : halfWidth;
    }

    public static double cornerLongitudinal(int cornerIndex, double halfDepth) {
        return (cornerIndex == 0 || cornerIndex == 1) ? -halfDepth : halfDepth;
    }

    /** 前面（负 forward）两角：0,1；后面：2,3；右侧：1,2；左侧：0,3。 */
    public static int[] frontCorners() {
        return new int[] {0, 1};
    }

    public static int[] backCorners() {
        return new int[] {2, 3};
    }

    public static int[] rightCorners() {
        return new int[] {1, 2};
    }

    public static int[] leftCorners() {
        return new int[] {0, 3};
    }
}
