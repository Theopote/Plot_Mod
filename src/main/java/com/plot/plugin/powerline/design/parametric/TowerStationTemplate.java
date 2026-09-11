package com.plot.plugin.powerline.design.parametric;

public record TowerStationTemplate(
        String id,
        TowerStationRole role,
        double heightRatio,
        double widthRatio,
        double depthRatio) {
}
