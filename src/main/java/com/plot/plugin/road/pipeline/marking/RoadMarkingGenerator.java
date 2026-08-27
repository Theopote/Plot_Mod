package com.plot.plugin.road.pipeline.marking;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadMarkingPasses;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.RoadGenerationPipelineContext;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.ui.tools.impl.modify.helper.OffsetHandler;

import java.util.List;

/**
 * Lane dividers and centerline markings.
 */
public final class RoadMarkingGenerator {
    private RoadMarkingGenerator() {
    }

    public static void generate(RoadGenerationPipelineContext ctx, RoadGenerationPipelineContext.Host host) {
        ResolvedCrossSection crossSection = ctx.request().crossSection();
        if (!crossSection.laneDividers && crossSection.centerLineStyle == CenterLineStyle.NONE) {
            return;
        }

        generateLaneMarkings(
            ctx.solids(),
            ctx.segments(),
            ctx.heightInfos(),
            ctx.pathPoints(),
            crossSection,
            ctx.unitsPerBlock(),
            host::resolveBlockId);
    }

    private static void generateLaneMarkings(
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<Vec2d> pathPoints,
            ResolvedCrossSection crossSection,
            double unitsPerBlock,
            MaterialResolver materialResolver) {
        String blockId = materialResolver.resolve(crossSection.markingMaterial);
        double totalLength = segments.stream().mapToDouble(s -> s.distance).sum();
        double accumulatedSegmentStart;

        List<RoadMarkingPasses.Pass> passes = RoadMarkingPasses.fromCrossSection(crossSection);

        for (RoadMarkingPasses.Pass pass : passes) {
            List<Vec2d> markingLine = OffsetHandler.offsetPolyline(pathPoints, pass.offset() * unitsPerBlock);
            accumulatedSegmentStart = 0.0;
            for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
                SegmentHeightInfo info = heightInfos.get(i);
                PathSegment segment = segments.get(i);
                int samples = Math.max(2, (int) Math.ceil(segment.distance));
                for (int j = 0; j <= samples; j++) {
                    if (!pass.solid() && j % 2 != 0) {
                        continue;
                    }
                    double t = (double) j / samples;
                    int targetY = (int) (info.targetStart * (1 - t) + info.targetEnd * t);
                    double normalized = totalLength > 1e-9
                        ? (accumulatedSegmentStart + t * segment.distance) / totalLength
                        : 0.0;
                    Vec2d point = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(markingLine, normalized);
                    solids.add(point, targetY, RoadSolidLayer.MARKING, blockId);
                }
                accumulatedSegmentStart += segment.distance;
            }
        }
    }

    @FunctionalInterface
    interface MaterialResolver {
        String resolve(String material);
    }
}
