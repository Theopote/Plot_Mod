package com.plot.plugin.powerline.design.parametric;

public record ResolvedTowerStation(
        String id,
        TowerStationRole role,
        double height,
        double halfWidth,
        double halfDepth) {
}
