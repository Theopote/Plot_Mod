package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.structure.BracingPattern;
import com.plot.plugin.powerline.design.structure.TowerArmShape;

public record TowerArmTemplate(
        String id,
        TowerArmRole role,
        double heightRatio,
        double reachRatio,
        TowerArmShape shape,
        BracingPattern bracing,
        double verticalDropRatio,
        double longitudinalHalfWidthRatio) {
}
