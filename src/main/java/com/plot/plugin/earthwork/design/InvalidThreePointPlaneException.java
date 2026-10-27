package com.plot.plugin.earthwork.design;

/** 三点控制点在 XZ 平面共线或退化，无法确定坡面。 */
public final class InvalidThreePointPlaneException extends RuntimeException {
    public InvalidThreePointPlaneException() {
        super("Three-point plane controls are collinear or degenerate");
    }
}
