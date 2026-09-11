package com.plot.plugin.powerline.design.parametric;

import java.util.List;

public record ResolvedTowerParameters(
        double height,
        double baseWidth,
        double baseHalfWidth,
        double baseHalfDepth,
        double armSpan,
        double depthScale,
        double waistRatio,
        List<Double> armLevelScales,
        StructureDensity density,
        List<ResolvedTowerStation> stations,
        List<ResolvedTowerArm> arms,
        double peakDecorationHeight,
        double peakDecorationSize,
        double topWireLift,
        List<ConstraintAdjustment> adjustments) {

    public double requiredTopHeight() {
        double stationTop = stations.stream()
            .mapToDouble(ResolvedTowerStation::height)
            .max()
            .orElse(height);
        double armTop = arms.stream()
            .mapToDouble(ResolvedTowerArm::baseHeight)
            .max()
            .orElse(0.0);
        double peakTop = peakDecorationHeight + Math.max(2.0, Math.round(peakDecorationSize)) + 1.0;
        double topWireTop = armTop + topWireLift;
        return Math.max(Math.max(stationTop, armTop), Math.max(peakTop, topWireTop));
    }
}
