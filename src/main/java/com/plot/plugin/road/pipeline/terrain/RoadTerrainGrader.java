package com.plot.plugin.road.pipeline.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadRoadbedGradingUtils;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.RoadEdgeBuildMetrics;
import com.plot.plugin.road.pipeline.RoadGenerationPipelineContext;
import com.plot.plugin.road.pipeline.construction.RoadConstructionClassifier;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.geometry.PathSegmentGeometry;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.terrain.TerrainSampler;

import java.util.List;

/**
 * Cut/fill grading of terrain around the road envelope.
 */
public final class RoadTerrainGrader {
    private RoadTerrainGrader() {
    }

    public static void grade(RoadGenerationPipelineContext ctx, RoadGenerationPipelineContext.Host host) {
        gradeRoadEnvelope(
            ctx.solids(),
            ctx.metrics(),
            ctx.segments(),
            ctx.heightInfos(),
            ctx.request().crossSection(),
            ctx.terrain(),
            ctx.unitsPerBlock(),
            ctx.detection().constructionTypes(),
            GradingHost.from(host));
    }

    private interface GradingHost {
        RoadSystemConfig config();

        int snapEndpointElevation(Vec2d center, int targetY);

        String resolveBlockId(String material);

        RoadTerrainClearanceUtils.BlockColumnResolver columnResolver();

        static GradingHost from(RoadGenerationPipelineContext.Host host) {
            return new GradingHost() {
                @Override
                public RoadSystemConfig config() {
                    return host.config();
                }

                @Override
                public int snapEndpointElevation(Vec2d center, int targetY) {
                    return host.snapEndpointElevation(center, targetY);
                }

                @Override
                public String resolveBlockId(String material) {
                    return host.resolveBlockId(material);
                }

                @Override
                public RoadTerrainClearanceUtils.BlockColumnResolver columnResolver() {
                    return new RoadTerrainClearanceUtils.BlockColumnResolver() {
                        @Override
                        public int worldX(Vec2d planPoint) {
                            return host.canvasToBlockPos(planPoint).getX();
                        }

                        @Override
                        public int worldZ(Vec2d planPoint) {
                            return host.canvasToBlockPos(planPoint).getZ();
                        }
                    };
                }
            };
        }
    }

    private static void gradeRoadEnvelope(
            RoadSolidModel solids,
            RoadEdgeBuildMetrics metrics,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            ResolvedCrossSection crossSection,
            TerrainSampler terrain,
            double unitsPerBlock,
            List<RoadConstructionType> constructionTypes,
            GradingHost host) {
        int sideBandWidth = crossSection.outerBandBlockCount();
        int envelopeWidth = crossSection.carriagewayWidth + sideBandWidth * 2;
        if (envelopeWidth <= 0) {
            return;
        }

        RoadSystemConfig config = host.config();
        int tunnelThreshold = config.getTunnelThreshold();
        int bridgeThreshold = config.getBridgeThreshold();
        String fillMaterialId = host.resolveBlockId(
            crossSection.fillSlopeMaterial != null && !crossSection.fillSlopeMaterial.isBlank()
                ? crossSection.fillSlopeMaterial
                : config.getFillSlopeMaterial());

        RoadRoadbedGradingUtils.GradingVolumes total = RoadRoadbedGradingUtils.GradingVolumes.ZERO;
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            RoadConstructionType type = RoadConstructionClassifier.constructionTypeAt(constructionTypes, i);
            if (type == RoadConstructionType.BRIDGE) {
                continue;
            }
            PathSegment segment = segments.get(i);
            SegmentHeightInfo info = heightInfos.get(i);
            Vec2d leftNormal = PathSegmentGeometry.leftNormal(segment);
            int samples = Math.max(2, (int) Math.ceil(segment.distance / scale));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                Vec2d center = segment.start.lerp(segment.end, t);
                int targetY = (int) Math.round(info.targetStart * (1 - t) + info.targetEnd * t);
                targetY = host.snapEndpointElevation(center, targetY);
                total = total.add(RoadRoadbedGradingUtils.gradeCrossSectionEnvelope(
                    solids, center, leftNormal, envelopeWidth, targetY,
                    tunnelThreshold, bridgeThreshold, fillMaterialId,
                    terrain, host.columnResolver(), unitsPerBlock));
            }
        }
        metrics.cutVolume = total.cutVolume();
        metrics.fillVolume = total.fillVolume();
    }
}
