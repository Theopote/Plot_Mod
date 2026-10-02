package com.plot.plugin.road.pipeline.profile.environment;

/**
 * Per-station vertical design bounds for the profile solver.
 */
public record VerticalStationConstraint(
        double preferredElevation,
        Double minimumElevation,
        Double maximumElevation,
        SurfaceContext context) {

    public static VerticalStationConstraint land(double preferred) {
        return new VerticalStationConstraint(preferred, null, null, SurfaceContext.LAND);
    }
}
