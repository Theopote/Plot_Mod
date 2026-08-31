package com.plot.plugin.road.pipeline.marking;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadMarkingPasses;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.RoadGenerationPipelineContext;
import com.plot.plugin.road.pipeline.RoadPathStationSampler;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lane dividers and centerline markings.
 */
public final class RoadMarkingGenerator {
    private RoadMarkingGenerator() {
    }

    public static void generate(RoadGenerationPipelineContext ctx, RoadGenerationPipelineContext.Host host) {
        CrossSectionBuildContext crossSections = ctx.request().crossSections();
        ResolvedCrossSection fallback = crossSections.fallback();
        if (!crossSections.isVariable()
            && !fallback.laneDividers
            && fallback.centerLineStyle == CenterLineStyle.NONE) {
            return;
        }

        generateLaneMarkings(
            ctx.solids(),
            ctx.segments(),
            ctx.heightInfos(),
            crossSections,
            ctx.unitsPerBlock(),
            host::resolveBlockId);
    }

    private static void generateLaneMarkings(
            RoadSolidModel solids,
            List<com.plot.plugin.road.pipeline.geometry.PathSegment> segments,
            List<com.plot.plugin.road.pipeline.profile.SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            double unitsPerBlock,
            MaterialResolver materialResolver) {
        AtomicInteger sampleIndex = new AtomicInteger();
        RoadPathStationSampler.forEach(
            segments,
            heightInfos,
            crossSections.segmentStartStation(),
            unitsPerBlock,
            null,
            (center, leftNormal, targetY, chainage) -> {
                int index = sampleIndex.getAndIncrement();
                ResolvedCrossSection crossSection = crossSections.resolve(chainage);
                if (!crossSection.laneDividers && crossSection.centerLineStyle == CenterLineStyle.NONE) {
                    return;
                }
                String blockId = materialResolver.resolve(crossSection.markingMaterial);
                for (RoadMarkingPasses.Pass pass : RoadMarkingPasses.fromCrossSection(crossSection)) {
                    if (!pass.solid() && index % 2 != 0) {
                        continue;
                    }
                    Vec2d direction = leftNormal.multiply(-1);
                    Vec2d point = center.add(direction.multiply(pass.offset() * unitsPerBlock));
                    solids.add(point, targetY, RoadSolidLayer.MARKING, blockId);
                }
            });
    }

    @FunctionalInterface
    interface MaterialResolver {
        String resolve(String material);
    }
}
