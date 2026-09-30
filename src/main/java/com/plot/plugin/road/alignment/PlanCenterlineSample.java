package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;

/** Plan 中心线采样点：世界坐标与对应的设计 canonical 桩号。 */
public record PlanCenterlineSample(Vec2d position, double canonicalStation) {
}
