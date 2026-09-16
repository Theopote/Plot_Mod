package com.plot.plugin.pattern.space;

import com.plot.api.geometry.Vec2d;

/**
 * Pattern Space 中的一个采样点（画布 XZ，单位：米）。
 */
public record PatternSample(double x, double z) {
    public static PatternSample fromCanvas(Vec2d canvasCenter) {
        if (canvasCenter == null) {
            return new PatternSample(0, 0);
        }
        return new PatternSample(canvasCenter.x, canvasCenter.y);
    }

    public Vec2d toCanvas() {
        return new Vec2d(x, z);
    }
}
