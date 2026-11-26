package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;

/**
 * Plan 中心线采样点。
 * <p>
 * Field {@code canonicalStation} is historical naming; callers must know which domain produced it:
 * <ul>
 *   <li>{@link #canonical} — design/canonical station (traversal / VA / facilities)</li>
 *   <li>{@link #haNative} — HA native station (physical materialization; unrotated)</li>
 *   <li>{@link #geometryLocal} — edge-local arc length (instance polyline without HA)</li>
 * </ul>
 */
public record PlanCenterlineSample(Vec2d position, double canonicalStation) {

    public static PlanCenterlineSample canonical(Vec2d position, double canonicalStation) {
        return new PlanCenterlineSample(position, canonicalStation);
    }

    public static PlanCenterlineSample haNative(Vec2d position, double nativeStation) {
        return new PlanCenterlineSample(position, nativeStation);
    }

    public static PlanCenterlineSample geometryLocal(Vec2d position, double geometryLocal) {
        return new PlanCenterlineSample(position, geometryLocal);
    }
}
