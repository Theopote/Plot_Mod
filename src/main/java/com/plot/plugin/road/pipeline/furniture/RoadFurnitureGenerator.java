package com.plot.plugin.road.pipeline.furniture;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.RoadGenerationPipelineContext;
import com.plot.plugin.road.pipeline.RoadPathStationSampler;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
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
        Integer spacing = crossSections.fallback().streetlightSpacing;
        if (spacing == null || spacing <= 0) {
            return;
        }

        generateStreetlights(
            ctx.solids(),
            ctx.segments(),
            ctx.heightInfos(),
            crossSections,
            spacing,
            ctx.unitsPerBlock(),
            ctx.request().designElevation());
    }

    static void generateStreetlights(
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            int spacing,
            double unitsPerBlock,
            DesignElevationSource designElevation) {
        Vec2d[] previous = {null};
        double[] traveledHolder = {0.0};
        double[] nextPlacementHolder = {0.0};
        boolean[] placeLeftHolder = {true};

        RoadPathStationSampler.forEach(
            segments,
            heightInfos,
            crossSections.samplingOriented(),
            unitsPerBlock,
            designElevation,
            null,
            (center, leftNormal, targetY, chainage) -> {
                if (previous[0] != null) {
                    traveledHolder[0] += previous[0].distance(center);
                }
                previous[0] = center;
                ResolvedCrossSection crossSection = crossSections.resolve(chainage);
                double skipDistance = crossSection.carriagewayWidth * unitsPerBlock;
                if (traveledHolder[0] + 1e-6 < nextPlacementHolder[0]) {
                    return;
                }
                nextPlacementHolder[0] = traveledHolder[0] + Math.max(spacing, skipDistance);
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
