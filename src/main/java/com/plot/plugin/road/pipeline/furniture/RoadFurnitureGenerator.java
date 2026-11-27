package com.plot.plugin.road.pipeline.furniture;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.pipeline.construction.RoadConstructionClassifier;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.RoadGenerationPipelineContext;
import com.plot.plugin.road.pipeline.RoadPathStationSampler;
import com.plot.plugin.road.pipeline.facility.StationFacilityJunctionTrim;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;

import java.util.List;

/**
 * Street furniture such as streetlights along the road corridor.
 */
public final class RoadFurnitureGenerator {
    private RoadFurnitureGenerator() {
    }

    public static void generate(RoadGenerationPipelineContext ctx, RoadGenerationPipelineContext.Host host) {
        CrossSectionBuildContext crossSections = ctx.request().crossSections();
        // 新版路灯设计器优先；旧式路灯需任一桩号启用 spacing，不能只看 fallback。
        if (crossSections.lightingPlacement() != null || !crossSections.hasAnyLegacyStreetlightSpacing()) {
            return;
        }

        generateStreetlights(
            ctx.solids(),
            ctx.segments(),
            ctx.heightInfos(),
            crossSections,
            1,
            ctx.unitsPerBlock(),
            ctx.request().designElevation(),
            ctx.buildProfile(),
            ctx.detection() != null ? ctx.detection().constructionTypes() : null,
            resolveEndpointTrim(ctx));
    }

    /**
     * 路口广场内不设路灯：优先用走廊裁剪；立交等未裁时退回按车行道宽跳过两端。
     */
    static StationFacilityJunctionTrim.FacilityEndpointTrim resolveEndpointTrim(
            RoadGenerationPipelineContext ctx) {
        StationFacilityJunctionTrim.FacilityEndpointTrim trim =
            ctx.request().junctionCorridor() != null
                ? ctx.request().junctionCorridor().endpointTrim()
                : StationFacilityJunctionTrim.FacilityEndpointTrim.NONE;
        if (trim != null && (trim.skipStart() > 1e-9 || trim.skipEnd() > 1e-9)) {
            return trim;
        }
        double units = ctx.unitsPerBlock() > 1e-9 ? ctx.unitsPerBlock() : 1.0;
        double skip = Math.max(4.0, ctx.request().crossSections().fallback().carriagewayWidth) * units;
        return new StationFacilityJunctionTrim.FacilityEndpointTrim(skip, skip);
    }

    static void generateStreetlights(
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            int spacing,
            double unitsPerBlock,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            List<RoadConstructionType> constructionTypes) {
        generateStreetlights(
            solids, segments, heightInfos, crossSections, spacing, unitsPerBlock,
            designElevation, buildProfile, constructionTypes,
            StationFacilityJunctionTrim.FacilityEndpointTrim.NONE);
    }

    static void generateStreetlights(
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            int spacing,
            double unitsPerBlock,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            List<RoadConstructionType> constructionTypes,
            StationFacilityJunctionTrim.FacilityEndpointTrim endpointTrim) {
        Vec2d[] previous = {null};
        double[] traveledHolder = {0.0};
        double[] nextPlacementHolder = {0.0};
        boolean[] placeLeftHolder = {true};
        double edgeLength = 0.0;
        if (segments != null) {
            for (PathSegment segment : segments) {
                if (segment != null) {
                    edgeLength += segment.distance;
                }
            }
        }
        StationFacilityJunctionTrim.FacilityEndpointTrim trim = endpointTrim != null
            ? endpointTrim
            : StationFacilityJunctionTrim.FacilityEndpointTrim.NONE;
        double length = edgeLength;

        RoadPathStationSampler.forEach(
            segments,
            heightInfos,
            crossSections.samplingOriented().forward(),
            crossSections::chainageAtGeometryLocal,
            unitsPerBlock,
            designElevation,
            buildProfile,
            null,
            null,
            (center, leftNormal, targetY, chainage, segmentIndex) -> {
                RoadConstructionType constructionType = RoadConstructionClassifier.constructionTypeAt(
                    constructionTypes, segmentIndex);
                if (constructionType.isStructure()) {
                    return;
                }
                if (previous[0] != null) {
                    traveledHolder[0] += previous[0].distance(center);
                }
                previous[0] = center;
                if (!trim.shouldPlace(traveledHolder[0], length)) {
                    return;
                }
                ResolvedCrossSection crossSection = crossSections.resolve(chainage);
                Integer localSpacing = crossSection.streetlightSpacing;
                int placeSpacing;
                if (crossSections.isVariable()) {
                    // 变截面：仅当前桩号启用 spacing 时放置，避免 fallback 一刀切。
                    if (localSpacing == null || localSpacing <= 0) {
                        return;
                    }
                    placeSpacing = localSpacing;
                } else {
                    placeSpacing = (localSpacing != null && localSpacing > 0) ? localSpacing : spacing;
                    if (placeSpacing <= 0) {
                        return;
                    }
                }
                double skipDistance = crossSection.carriagewayWidth * unitsPerBlock;
                if (traveledHolder[0] + 1e-6 < nextPlacementHolder[0]) {
                    return;
                }
                nextPlacementHolder[0] = traveledHolder[0] + Math.max(placeSpacing, skipDistance);
                double offset = (RoadDimensionUtils.maxLateralOffset(crossSection.carriagewayWidth)
                    + crossSection.outerBandBlockCount()
                    + 0.5) * unitsPerBlock;
                double side = placeLeftHolder[0] ? offset : -offset;
                Vec2d lightPos = center.add(leftNormal.multiply(side));
                solids.add(
                    lightPos,
                    targetY + 1,
                    RoadSolidLayer.STREETLIGHT,
                    crossSection.streetlightBlock);
                placeLeftHolder[0] = !placeLeftHolder[0];
            });
    }
}
