package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.structure.BracingPattern;
import com.plot.plugin.powerline.design.structure.TowerArmShape;

public record ResolvedTowerArm(
        String id,
        TowerArmRole role,
        double baseHeight,
        double lateralReach,
        double longitudinalHalfWidth,
        double verticalDrop,
        TowerArmShape shape,
        BracingPattern bracing) {
}
