package com.plot.plugin.road.pipeline.marking;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadMarkingPasses;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.RoadGenerationPipelineContext;
import com.plot.plugin.road.pipeline.RoadPathStationSampler;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lane dividers and centerline markings（含铁路中心轨 + 轨枕）。
 */
public final class RoadMarkingGenerator {
    /** 轨枕材质：压在道床表面、铁轨之下。 */
    public static final String RAIL_SLEEPER_MATERIAL = "minecraft:oak_planks";
    /** 每隔多少采样点铺一根轨枕（采样约 1 格）。 */
    public static final int RAIL_SLEEPER_EVERY_SAMPLES = 2;
    /** 轨枕横向半宽（格）：中心 ± 该值，共 2n+1 格。 */
    public static final int RAIL_SLEEPER_HALF_WIDTH = 1;

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
            ctx.request().designElevation(),
            ctx.buildProfile(),
            host::resolveBlockId);
    }

    private static void generateLaneMarkings(
            RoadSolidModel solids,
            List<com.plot.plugin.road.pipeline.geometry.PathSegment> segments,
            List<com.plot.plugin.road.pipeline.profile.SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            double unitsPerBlock,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            MaterialResolver materialResolver) {
        AtomicInteger sampleIndex = new AtomicInteger();
        RoadPathStationSampler.forEach(
            segments,
            heightInfos,
            crossSections.samplingOriented(),
            unitsPerBlock,
            designElevation,
            buildProfile,
            null,
            (center, leftNormal, targetY, chainage, segmentIndex) -> {
                int index = sampleIndex.getAndIncrement();
                ResolvedCrossSection crossSection = crossSections.resolve(chainage);
                if (!crossSection.laneDividers && crossSection.centerLineStyle == CenterLineStyle.NONE) {
                    return;
                }
                String blockId = materialResolver.resolve(crossSection.markingMaterial);
                boolean rail = crossSection.centerLineStyle == CenterLineStyle.RAIL;
                Vec2d lateral = leftNormal.multiply(-1);
                if (rail && index % RAIL_SLEEPER_EVERY_SAMPLES == 0) {
                    String sleeperId = materialResolver.resolve(RAIL_SLEEPER_MATERIAL);
                    for (int lat = -RAIL_SLEEPER_HALF_WIDTH; lat <= RAIL_SLEEPER_HALF_WIDTH; lat++) {
                        Vec2d sleeperPoint = center.add(lateral.multiply(lat * unitsPerBlock));
                        solids.add(sleeperPoint, targetY, RoadSolidLayer.MARKING, sleeperId);
                    }
                }
                int placeY = rail ? targetY + 1 : targetY;
                for (RoadMarkingPasses.Pass pass : RoadMarkingPasses.fromCrossSection(crossSection)) {
                    if (!pass.solid() && index % 2 != 0) {
                        continue;
                    }
                    Vec2d point = center.add(lateral.multiply(pass.offset() * unitsPerBlock));
                    solids.add(point, placeY, RoadSolidLayer.MARKING, blockId);
                }
            });
    }

    @FunctionalInterface
    interface MaterialResolver {
        String resolve(String material);
    }
}
