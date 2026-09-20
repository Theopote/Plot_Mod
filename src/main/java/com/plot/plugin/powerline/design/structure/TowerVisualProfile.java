package com.plot.plugin.powerline.design.structure;

import java.util.Comparator;
import java.util.List;

/**
 * 从塔体结构定义提取可审查的视觉指标，用于 preset 落地前后对比。
 */
public record TowerVisualProfile(
        TowerSilhouette silhouette,
        double height,
        double baseHalfWidth,
        double topHalfWidth,
        double baseDepthRatio,
        int armCount,
        double maxArmReach,
        double armReachToHeightRatio,
        List<Double> armReachesSorted,
        int bayCount,
        int planDiagonalBayCount) {

    public double taperRatio() {
        if (topHalfWidth <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return baseHalfWidth / topHalfWidth;
    }

    public static TowerVisualProfile of(TowerStructureDesign structure) {
        if (structure == null) {
            throw new IllegalArgumentException("structure required");
        }
        List<TowerStation> stations = structure.sortedStations();
        TowerStation base = stations.isEmpty() ? null : stations.getFirst();
        TowerStation top = stations.isEmpty() ? null : stations.getLast();
        double baseHalfWidth = base != null ? base.getHalfWidth() : 0.0;
        double topHalfWidth = top != null ? top.getHalfWidth() : 0.0;
        double baseDepthRatio = base != null && base.getHalfWidth() > 0
            ? base.getHalfDepth() / base.getHalfWidth()
            : 0.0;

        List<Double> armReaches = structure.getArms().stream()
            .map(TowerArm::getLateralReach)
            .sorted(Comparator.reverseOrder())
            .toList();
        double maxArmReach = armReaches.isEmpty() ? 0.0 : armReaches.getFirst();
        double height = structure.maxHeight();

        return new TowerVisualProfile(
            structure.getSilhouette(),
            height,
            baseHalfWidth,
            topHalfWidth,
            baseDepthRatio,
            structure.getArms().size(),
            maxArmReach,
            height > 0 ? maxArmReach / height : 0.0,
            armReaches,
            structure.getBays().size(),
            (int) structure.getBays().stream().filter(TowerBay::isPlanDiagonalBracing).count());
    }
}
