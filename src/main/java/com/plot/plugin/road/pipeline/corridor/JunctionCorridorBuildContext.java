package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.pipeline.facility.StationFacilityJunctionTrim;

import java.util.List;

/**
 * Per-edge junction exclusion used while building {@link RoadCorridorPlan}.
 * Edge corridors are trimmed near degree-3+ nodes so {@link com.plot.plugin.road.RoadJunctionGenerator}
 * owns the shared intersection surface.
 * <p>
 * Grade-separated lower approaches keep clearance columns inside the plaza so the underpass
 * headroom is cleared even when the junction surface stamp does not emit air.
 */
public record JunctionCorridorBuildContext(
        StationFacilityJunctionTrim.FacilityEndpointTrim endpointTrim,
        List<List<Vec2d>> exclusionPolygons,
        boolean preserveClearanceInExclusion) {

    public static final JunctionCorridorBuildContext NONE = new JunctionCorridorBuildContext(
        StationFacilityJunctionTrim.FacilityEndpointTrim.NONE,
        List.of(),
        false);

    public JunctionCorridorBuildContext(
            StationFacilityJunctionTrim.FacilityEndpointTrim endpointTrim,
            List<List<Vec2d>> exclusionPolygons) {
        this(endpointTrim, exclusionPolygons, false);
    }

    public JunctionCorridorBuildContext {
        if (endpointTrim == null) {
            endpointTrim = StationFacilityJunctionTrim.FacilityEndpointTrim.NONE;
        }
        if (exclusionPolygons == null) {
            exclusionPolygons = List.of();
        } else {
            exclusionPolygons = List.copyOf(exclusionPolygons);
        }
    }
}
