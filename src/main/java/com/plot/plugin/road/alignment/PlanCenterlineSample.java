package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;

/**
 * Plan 中心线采样点，带显式 {@link StationDomain}（禁止仅靠注释区分域）。
 */
public record PlanCenterlineSample(Vec2d position, double canonicalStation, StationDomain domain) {

    public PlanCenterlineSample {
        if (domain == null) {
            domain = StationDomain.CANONICAL;
        }
    }

    /** @deprecated prefer {@link #canonical(Vec2d, double)} */
    @Deprecated
    public PlanCenterlineSample(Vec2d position, double canonicalStation) {
        this(position, canonicalStation, StationDomain.CANONICAL);
    }

    public static PlanCenterlineSample canonical(Vec2d position, double canonicalStation) {
        return new PlanCenterlineSample(position, canonicalStation, StationDomain.CANONICAL);
    }

    public static PlanCenterlineSample haNative(Vec2d position, double nativeStation) {
        return new PlanCenterlineSample(position, nativeStation, StationDomain.HA_NATIVE);
    }

    public static PlanCenterlineSample geometryLocal(Vec2d position, double geometryLocal) {
        return new PlanCenterlineSample(position, geometryLocal, StationDomain.GEOMETRY_LOCAL);
    }
}
