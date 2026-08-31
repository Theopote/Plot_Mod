package com.plot.plugin.road.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.profile.EndpointElevationSnaps;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.terrain.TerrainSampler;

import java.util.List;

/**
 * Inputs for a single centerline build pass through {@link RoadGenerationPipeline}.
 */
public record RoadGenerationBuildRequest(
        List<Vec2d> pathPoints,
        TerrainSampler terrain,
        ResolvedCrossSection crossSection,
        CrossSectionBuildContext crossSections,
        List<SegmentHeightInfo> heightInfos,
        double pathLength,
        EndpointElevationSnaps endpointSnaps,
        String carriagewaySeedKey,
        StationFacilityBuildContext stationFacilities) {

    public RoadGenerationBuildRequest {
        if (stationFacilities == null) {
            stationFacilities = StationFacilityBuildContext.EMPTY;
        }
        if (crossSections == null) {
            crossSections = CrossSectionBuildContext.fixed(crossSection);
        }
    }
}
