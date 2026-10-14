package com.plot.plugin.road.pipeline.crosssection;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.RoadEdgeBuildMetrics;
import com.plot.plugin.road.pipeline.RoadPathStationSampler;
import com.plot.plugin.road.pipeline.RoadGenerationPipelineContext;
import com.plot.plugin.road.pipeline.construction.ConstructionDetection;
import com.plot.plugin.road.pipeline.construction.RoadConstructionClassifier;
import com.plot.plugin.road.pipeline.profile.environment.WaterCrossing;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.geometry.PathSegmentGeometry;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Builds cross-section layers not covered by {@link com.plot.plugin.road.pipeline.corridor.RoadCorridorPlanBuilder}:
 * bridge deck underlays, bridge structures, guardrails, median.
 */
public final class RoadCrossSectionBuilder {
    private RoadCrossSectionBuilder() {
    }

    public static void build(RoadGenerationPipelineContext ctx, RoadGenerationPipelineContext.Host host) {
        CrossSectionBuildContext crossSections = ctx.request().crossSections();
        List<PathSegment> segments = ctx.segments();
        List<SegmentHeightInfo> heightInfos = ctx.heightInfos();
        ConstructionDetection detection = ctx.detection();
        TerrainSampler terrain = ctx.terrain();
        RoadSolidModel solids = ctx.solids();
        RoadEdgeBuildMetrics metrics = ctx.metrics();
        double unitsPerBlock = ctx.unitsPerBlock();
        CrossSectionHost crossSectionHost = CrossSectionHost.from(host);
        DesignElevationSource designElevation = ctx.request().designElevation();
        BuildHeightProfile buildProfile = ctx.buildProfile();
        metrics.bridgeCount = detection.runs().isEmpty()
            ? detection.bridges().size()
            : (int) detection.runCount(RoadConstructionType.BRIDGE);
        metrics.tunnelCount = detection.runs().isEmpty()
            ? detection.tunnels().size()
            : (int) detection.runCount(RoadConstructionType.TUNNEL);

        generateBridgeDeckUnderlays(
            crossSectionHost,
            solids,
            segments,
            heightInfos,
            detection.constructionTypes(),
            terrain,
            crossSections,
            unitsPerBlock,
            designElevation,
            buildProfile);

        generateBridgeStructures(
            crossSectionHost,
            solids,
            detection.constructionTypes(),
            segments,
            heightInfos,
            crossSections,
            terrain,
            unitsPerBlock,
            designElevation,
            buildProfile,
            ctx.request().profileWaterCrossings());

        generateBridgeGuardrails(
            crossSectionHost,
            solids,
            detection.constructionTypes(),
            segments,
            heightInfos,
            crossSections,
            unitsPerBlock,
            designElevation,
            buildProfile,
            ctx.request().profileWaterCrossings());

        generateMedianBlocks(
            crossSectionHost, solids, segments, heightInfos, crossSections, unitsPerBlock, designElevation, buildProfile);
    }

    private interface CrossSectionHost {
        BlockPos canvasToBlockPos(Vec2d canvasPos);

        String resolveBlockId(String material);

        int snapEndpointElevation(Vec2d center, int targetY);

        double estimateCanvasUnitsPerBlock(List<Vec2d> pathPoints, List<PathSegment> segments);

        int bridgeThreshold();

        boolean generateBridgePillars();

        boolean includeBridgeGuardrail();

        String bridgeGuardrailMaterial();

        static CrossSectionHost from(RoadGenerationPipelineContext.Host host) {
            return new CrossSectionHost() {
                @Override
                public BlockPos canvasToBlockPos(Vec2d canvasPos) {
                    return host.canvasToBlockPos(canvasPos);
                }

                @Override
                public String resolveBlockId(String material) {
                    return host.resolveBlockId(material);
                }

                @Override
                public int snapEndpointElevation(Vec2d center, int targetY) {
                    return host.snapEndpointElevation(center, targetY);
                }

                @Override
                public double estimateCanvasUnitsPerBlock(List<Vec2d> pathPoints, List<PathSegment> segments) {
                    return host.estimateCanvasUnitsPerBlock(pathPoints, segments);
                }

                @Override
                public int bridgeThreshold() {
                    return host.bridgeThreshold();
                }

                @Override
                public boolean generateBridgePillars() {
                    return host.config().isGenerateBridgePillars();
                }

                @Override
                public boolean includeBridgeGuardrail() {
                    return host.config().isIncludeBridgeGuardrail();
                }

                @Override
                public String bridgeGuardrailMaterial() {
                    return host.config().getBridgeGuardrailMaterial();
                }
            };
        }
    }

    /**
     * Places bridge deck structure at {@code targetY - 1} only.
     * Continuous road surface at {@code targetY} is owned by
     * {@link com.plot.plugin.road.pipeline.corridor.RoadCorridorPlanBuilder}.
     */
    private static void generateBridgeDeckUnderlays(
            CrossSectionHost host,
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<RoadConstructionType> constructionTypes,
            TerrainSampler terrain,
            CrossSectionBuildContext crossSections,
            double unitsPerBlock,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile) {
        String bridgeStructureBlockId = host.resolveBlockId(BridgeStructureGenerator.DECK_SLAB_MATERIAL);
        boolean chainForward = crossSections.samplingOriented().forward();
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        double geometryLocalBase = 0.0;
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            PathSegment segment = segments.get(i);
            boolean bridgeSegment = RoadConstructionClassifier.constructionTypeAt(
                constructionTypes, i).isBridgeFamily();
            if (!bridgeSegment) {
                geometryLocalBase += segment.distance;
                continue;
            }
            SegmentHeightInfo info = heightInfos.get(i);
            Vec2d normal = PathSegmentGeometry.chainLeftNormal(segment, chainForward);
            int samples = Math.max(2, (int) Math.ceil(segment.distance / scale));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                Vec2d center = segment.start.lerp(segment.end, t);
                double geometryLocal = geometryLocalBase + segment.distance * t;
                double worldStation = geometryLocal / scale;
                int targetY = DesignElevationSource.resolveTargetElevation(
                    designElevation,
                    buildProfile,
                    info,
                    geometryLocal,
                    t,
                    worldStation);
                targetY = host.snapEndpointElevation(center, targetY);
                double chainage = crossSections.chainageAtGeometryLocal(geometryLocal);
                ResolvedCrossSection crossSection = crossSections.resolve(chainage);
                int carriagewayWidth = crossSection.carriagewayWidth;
                int minOffset = RoadDimensionUtils.minLateralOffset(carriagewayWidth);
                int maxOffset = RoadDimensionUtils.maxLateralOffset(carriagewayWidth);
                for (int lateral = minOffset; lateral <= maxOffset; lateral++) {
                    Vec2d planPoint = center.add(normal.multiply(lateral * scale));
                    if (targetY - terrain.sampleSurfaceY(planPoint) > host.bridgeThreshold()) {
                        solids.add(
                            planPoint,
                            targetY - 1,
                            RoadSolidLayer.BRIDGE,
                            bridgeStructureBlockId);
                    }
                }
            }
            geometryLocalBase += segment.distance;
        }
    }

    private static void generateMedianBlocks(
            CrossSectionHost host,
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            double unitsPerBlock,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile) {
        RoadPathStationSampler.forEach(
            segments,
            heightInfos,
            crossSections.samplingOriented(),
            unitsPerBlock,
            designElevation,
            buildProfile,
            host::snapEndpointElevation,
            (center, leftNormal, targetY, chainage, segmentIndex) -> {
                ResolvedCrossSection crossSection = crossSections.resolve(chainage);
                if (!crossSection.includeMedian || crossSection.medianWidth <= 0) {
                    return;
                }
                double halfMedian = RoadDimensionUtils.halfExtentFromCenter(crossSection.medianWidth) * unitsPerBlock;
                Vec2d left = center.add(leftNormal.multiply(-halfMedian));
                Vec2d right = center.add(leftNormal.multiply(halfMedian));
                String blockId = host.resolveBlockId(crossSection.medianMaterial);
                solids.addSpan(left, right, targetY, RoadSolidLayer.MEDIAN, blockId);
            });
    }

    private static void generateBridgeStructures(
            CrossSectionHost host,
            RoadSolidModel solids,
            List<RoadConstructionType> constructionTypes,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            TerrainSampler terrain,
            double unitsPerBlock,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            List<WaterCrossing> profileWaterCrossings) {
        if (!host.generateBridgePillars()) {
            return;
        }
        BridgeStructureGenerator.generate(
            new BridgeStructureGenerator.Host() {
                @Override
                public String resolveBlockId(String material) {
                    return host.resolveBlockId(material);
                }

                @Override
                public int snapEndpointElevation(Vec2d center, int targetY) {
                    return host.snapEndpointElevation(center, targetY);
                }
            },
            solids,
            constructionTypes,
            segments,
            heightInfos,
            crossSections,
            terrain,
            unitsPerBlock,
            designElevation,
            buildProfile,
            profileWaterCrossings);
    }

    private static void generateBridgeGuardrails(
            CrossSectionHost host,
            RoadSolidModel solids,
            List<RoadConstructionType> constructionTypes,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            double unitsPerBlock,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            List<com.plot.plugin.road.pipeline.profile.environment.WaterCrossing> profileWaterCrossings) {
        if (!host.includeBridgeGuardrail()) {
            return;
        }
        BridgeGuardrailGenerator.generate(
            new BridgeGuardrailGenerator.Host() {
                @Override
                public String resolveBlockId(String material) {
                    return host.resolveBlockId(material);
                }

                @Override
                public int snapEndpointElevation(Vec2d center, int targetY) {
                    return host.snapEndpointElevation(center, targetY);
                }
            },
            solids,
            constructionTypes,
            segments,
            heightInfos,
            crossSections,
            unitsPerBlock,
            designElevation,
            buildProfile,
            profileWaterCrossings,
            host.bridgeGuardrailMaterial());
    }

}
