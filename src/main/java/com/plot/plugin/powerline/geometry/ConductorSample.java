package com.plot.plugin.powerline.geometry;

import com.plot.api.geometry.Vec2d;

/** 导线上的单个采样点（世界坐标 + 平面坐标）。 */
public record ConductorSample(
        double worldX,
        double worldY,
        double worldZ,
        Vec2d planPoint) {
}
