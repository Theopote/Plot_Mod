package com.plot.plugin.road.alignment;

/**
 * Station domain carried by {@link PlanCenterlineSample}.
 */
public enum StationDomain {
    /** Design / engineering chainage (VA, facilities, overlays). */
    CANONICAL,
    /** Horizontal-alignment native arc length (unrotated materialization). */
    HA_NATIVE,
    /** Edge-local polyline arc length without HA mapping. */
    GEOMETRY_LOCAL
}
