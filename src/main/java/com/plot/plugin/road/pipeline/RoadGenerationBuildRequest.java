package com.plot.plugin.road.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.EndpointElevationSnaps;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.pipeline.profile.environment.WaterCrossing;
import com.plot.core.terrain.TerrainSampler;

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
        StationFacilityBuildContext stationFacilities,
        DesignElevationSource designElevation,
        BuildHeightProfile buildProfile,
        List<WaterCrossing> profileWaterCrossings) {

    public RoadGenerationBuildRequest {
        if (stationFacilities == null) {
            stationFacilities = StationFacilityBuildContext.EMPTY;
        }
        if (crossSections == null) {
            crossSections = CrossSectionBuildContext.fixed(crossSection);
        }
        if (designElevation == null) {
            designElevation = DesignElevationSource.inactive();
        }
        if (buildProfile == null) {
            buildProfile = BuildHeightProfile.inactive();
        }
        if (profileWaterCrossings == null) {
            profileWaterCrossings = List.of();
        }
    }

    public RoadGenerationBuildRequest(
            List<Vec2d> pathPoints,
            TerrainSampler terrain,
            ResolvedCrossSection crossSection,
            CrossSectionBuildContext crossSections,
            List<SegmentHeightInfo> heightInfos,
            double pathLength,
            EndpointElevationSnaps endpointSnaps,
            String carriagewaySeedKey,
            StationFacilityBuildContext stationFacilities,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile) {
        this(
            pathPoints,
            terrain,
            crossSection,
            crossSections,
            heightInfos,
            pathLength,
            endpointSnaps,
            carriagewaySeedKey,
            stationFacilities,
            designElevation,
            buildProfile,
            List.of());
    }

    public RoadGenerationBuildRequest(
            List<Vec2d> pathPoints,
            TerrainSampler terrain,
            ResolvedCrossSection crossSection,
            CrossSectionBuildContext crossSections,
            List<SegmentHeightInfo> heightInfos,
            double pathLength,
            EndpointElevationSnaps endpointSnaps,
            String carriagewaySeedKey,
            StationFacilityBuildContext stationFacilities) {
        this(
            pathPoints,
            terrain,
            crossSection,
            crossSections,
            heightInfos,
            pathLength,
            endpointSnaps,
            carriagewaySeedKey,
            stationFacilities,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            List.of());
    }
}
