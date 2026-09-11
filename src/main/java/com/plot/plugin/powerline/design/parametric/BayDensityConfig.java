package com.plot.plugin.powerline.design.parametric;

import com.plot.plugin.powerline.design.structure.BracingPattern;

/** 单个 bay 在某种结构密度下的斜撑配置。 */
public record BayDensityConfig(
        BracingPattern bracing,
        boolean horizontalRing,
        boolean planDiagonal) {
}
