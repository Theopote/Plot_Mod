package com.plot.plugin.road.pipeline.crosssection;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadSlopeUtils;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.RoadEdgeBuildMetrics;
import com.plot.plugin.road.pipeline.RoadGenerationPipelineContext;
import com.plot.plugin.road.pipeline.construction.BridgeSegment;
import com.plot.plugin.road.pipeline.construction.ConstructionDetection;
import com.plot.plugin.road.pipeline.construction.RoadConstructionClassifier;
import com.plot.plugin.road.pipeline.construction.TunnelSegment;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.geometry.PathSegmentGeometry;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.terrain.TerrainSampler;
import com.plot.ui.tools.impl.modify.helper.OffsetHandler;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Builds cross-section layers: Carriageway, Shoulder, BikeLane, Median, Sidewalk, drainage, slope batter.
 */
public final class RoadCrossSectionBuilder {
    private RoadCrossSectionBuilder() {
    }

    public static void build(RoadGenerationPipelineContext ctx, RoadGenerationPipelineContext.Host host) {
        ResolvedCrossSection crossSection = ctx.request().crossSection();
        List<PathSegment> segments = ctx.segments();
        List<SegmentHeightInfo> heightInfos = ctx.heightInfos();
        ConstructionDetection detection = ctx.detection();
        TerrainSampler terrain = ctx.terrain();
        RoadSolidModel solids = ctx.solids();
        RoadEdgeBuildMetrics metrics = ctx.metrics();
        double unitsPerBlock = ctx.unitsPerBlock();
        List<Vec2d> pathPoints = ctx.pathPoints();
        CrossSectionHost crossSectionHost = CrossSectionHost.from(host);

        generateCarriagewayBlocks(
            crossSectionHost,
            solids,
            metrics,
            segments,
            heightInfos,
            detection.bridges(),
            detection.tunnels(),
            terrain,
            crossSection.carriagewayWidth,
            crossSection.carriagewayMaterial,
            ctx.carriagewaySeedKey(),
            unitsPerBlock);

        if (crossSection.includeShoulder && crossSection.shoulderWidth > 0) {
            generateShoulderBlocks(
                crossSectionHost,
                solids,
                segments,
                heightInfos,
                crossSection.shoulderCenterOffset() * unitsPerBlock,
                crossSection.shoulderWidth,
                crossSectionHost.resolveBlockId(crossSection.shoulderMaterial),
                unitsPerBlock);
        }

        if (crossSection.includeBikeLane && crossSection.bikeLaneWidth > 0) {
            generateBikeLaneBlocks(
                crossSectionHost,
                solids,
                segments,
                heightInfos,
                crossSection.bikeLaneCenterOffset() * unitsPerBlock,
                crossSection.bikeLaneWidth,
                crossSectionHost.resolveBlockId(crossSection.bikeLaneMaterial),
                unitsPerBlock);
        }

        if (crossSection.includeSidewalk && crossSection.sidewalkWidth > 0) {
            generateSidewalkBlocks(
                crossSectionHost,
                solids,
                segments,
                heightInfos,
                crossSection.sidewalkCenterOffset() * unitsPerBlock,
                crossSection.sidewalkWidth,
                crossSectionHost.resolveBlockId(crossSection.sidewalkMaterial),
                unitsPerBlock);
        }

        if (crossSection.includeDrain) {
            generateDrainageChannels(
                crossSectionHost,
                solids,
                segments,
                heightInfos,
                crossSection.outerDrainageOffset() * unitsPerBlock,
                crossSectionHost.resolveBlockId("material.plot.gravel"),
                unitsPerBlock);
        }

        if (crossSection.includeSlopeBatter) {
            generateSlopeBatterBlocks(
                crossSectionHost,
                solids,
                segments,
                heightInfos,
                crossSection.slopeAnchorCenterOffset() * unitsPerBlock,
                crossSection.slopeAnchorBandWidth(),
                terrain,
                crossSection,
                unitsPerBlock,
                detection.constructionTypes());
        }

        if (crossSection.includeMedian && crossSection.medianWidth > 0) {
            double halfMedian = RoadDimensionUtils.halfExtentFromCenter(crossSection.medianWidth) * unitsPerBlock;
            List<Vec2d> leftMedian = OffsetHandler.offsetPolyline(pathPoints, -halfMedian);
            List<Vec2d> rightMedian = OffsetHandler.offsetPolyline(pathPoints, halfMedian);
            generateMedianBlocks(
                solids,
                segments,
                heightInfos,
                leftMedian,
                rightMedian,
                crossSectionHost.resolveBlockId(crossSection.medianMaterial));
        }
    }

    private interface CrossSectionHost {
        BlockPos canvasToBlockPos(Vec2d canvasPos);

        String resolveBlockId(String material);

        int snapEndpointElevation(Vec2d center, int targetY);

        double estimateCanvasUnitsPerBlock(List<Vec2d> pathPoints, List<PathSegment> segments);

        int bridgeThreshold();

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
            };
        }
    }

    @FunctionalInterface
    private interface PathSampleConsumer {
        void accept(Vec2d center, Vec2d leftNormal, int targetY);
    }

    private static void generateCarriagewayBlocks(
            CrossSectionHost host,
            RoadSolidModel solids,
            RoadEdgeBuildMetrics metrics,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<BridgeSegment> bridges,
            List<TunnelSegment> tunnels,
            TerrainSampler terrain,
            int carriagewayWidth,
            MaterialMix carriagewayMaterial,
            String seedKey,
            double unitsPerBlock) {
        metrics.bridgeCount = bridges.size();
        metrics.tunnelCount = tunnels.size();

        double halfExtent = RoadDimensionUtils.halfExtentFromCenter(carriagewayWidth) * unitsPerBlock;
        List<Vec2d> pathPoints = new ArrayList<>();
        for (PathSegment segment : segments) {
            if (pathPoints.isEmpty()) {
                pathPoints.add(segment.start);
            }
            pathPoints.add(segment.end);
        }
        List<Vec2d> leftBoundary = OffsetHandler.offsetPolyline(pathPoints, halfExtent);
        List<Vec2d> rightBoundary = OffsetHandler.offsetPolyline(pathPoints, -halfExtent);

        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        int minOffset = RoadDimensionUtils.minLateralOffset(carriagewayWidth);
        int maxOffset = RoadDimensionUtils.maxLateralOffset(carriagewayWidth);
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            PathSegment segment = segments.get(i);
            SegmentHeightInfo info = heightInfos.get(i);
            Vec2d normal = PathSegmentGeometry.leftNormal(segment);
            int samples = Math.max(2, (int) Math.ceil(segment.distance / scale));
            Vec2d previousCenter = null;
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                Vec2d center = segment.start.lerp(segment.end, t);
                int targetY = (int) Math.round(info.targetStart * (1 - t) + info.targetEnd * t);
                targetY = host.snapEndpointElevation(center, targetY);
                for (int lateral = minOffset; lateral <= maxOffset; lateral++) {
                    Vec2d planPoint = center.add(normal.multiply(lateral * scale));
                    BlockPos pos = host.canvasToBlockPos(planPoint).withY(targetY);
                    String blockId = MaterialMixResolver.resolve(
                        carriagewayMaterial, pos, seedKey, host::resolveBlockId);
                    solids.add(planPoint, targetY, RoadSolidLayer.ROAD, blockId);
                    if (previousCenter != null) {
                        Vec2d previousPoint = previousCenter.add(normal.multiply(lateral * scale));
                        solids.addSpan(
                            previousPoint,
                            planPoint,
                            targetY,
                            RoadSolidLayer.ROAD,
                            blockId);
                    }
                }
                previousCenter = center;
            }
        }

        generateBridgeStructures(host, solids, bridges, segments, heightInfos, leftBoundary, rightBoundary, terrain);
    }

    private static void generateShoulderBlocks(
            CrossSectionHost host,
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            double centerOffset,
            int shoulderWidth,
            String blockId,
            double unitsPerBlock) {
        forEachPathSample(host, segments, heightInfos, (center, leftNormal, targetY) -> {
            Vec2d left = center.add(leftNormal.multiply(centerOffset));
            Vec2d right = center.subtract(leftNormal.multiply(centerOffset));
            solids.addLateralStrip(
                left, leftNormal, shoulderWidth, targetY, RoadSolidLayer.SHOULDER, blockId, unitsPerBlock);
            solids.addLateralStrip(
                right, leftNormal, shoulderWidth, targetY, RoadSolidLayer.SHOULDER, blockId, unitsPerBlock);
        });
    }

    private static void generateBikeLaneBlocks(
            CrossSectionHost host,
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            double centerOffset,
            int bikeLaneWidth,
            String blockId,
            double unitsPerBlock) {
        forEachPathSample(host, segments, heightInfos, (center, leftNormal, targetY) -> {
            Vec2d left = center.add(leftNormal.multiply(centerOffset));
            Vec2d right = center.subtract(leftNormal.multiply(centerOffset));
            solids.addLateralStrip(
                left, leftNormal, bikeLaneWidth, targetY, RoadSolidLayer.BIKE_LANE, blockId, unitsPerBlock);
            solids.addLateralStrip(
                right, leftNormal, bikeLaneWidth, targetY, RoadSolidLayer.BIKE_LANE, blockId, unitsPerBlock);
        });
    }

    private static void generateSidewalkBlocks(
            CrossSectionHost host,
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            double centerOffset,
            int sidewalkWidth,
            String blockId,
            double unitsPerBlock) {
        forEachPathSample(host, segments, heightInfos, (center, leftNormal, targetY) -> {
            Vec2d left = center.add(leftNormal.multiply(centerOffset));
            Vec2d right = center.subtract(leftNormal.multiply(centerOffset));
            solids.addLateralStrip(
                left, leftNormal, sidewalkWidth, targetY, RoadSolidLayer.SIDEWALK, blockId, unitsPerBlock);
            solids.addLateralStrip(
                right, leftNormal, sidewalkWidth, targetY, RoadSolidLayer.SIDEWALK, blockId, unitsPerBlock);
        });
    }

    private static void generateDrainageChannels(
            CrossSectionHost host,
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            double drainageOffset,
            String blockId,
            double unitsPerBlock) {
        forEachPathSample(host, segments, heightInfos, (center, leftNormal, targetY) -> {
            int drainY = targetY - 1;
            Vec2d left = center.add(leftNormal.multiply(drainageOffset));
            Vec2d right = center.subtract(leftNormal.multiply(drainageOffset));
            solids.addLateralStrip(left, leftNormal, 1, drainY, RoadSolidLayer.DRAIN, blockId, unitsPerBlock);
            solids.addLateralStrip(right, leftNormal, 1, drainY, RoadSolidLayer.DRAIN, blockId, unitsPerBlock);
        });
    }

    private static void generateSlopeBatterBlocks(
            CrossSectionHost host,
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            double shoulderCenterOffset,
            int shoulderWidth,
            TerrainSampler terrain,
            ResolvedCrossSection crossSection,
            double unitsPerBlock,
            List<RoadConstructionType> constructionTypes) {
        if (!crossSection.includeSlopeBatter) {
            return;
        }
        String fillBlockId = host.resolveBlockId(crossSection.fillSlopeMaterial);
        String cutBlockId = crossSection.cutSlopeMaterial == null || crossSection.cutSlopeMaterial.isBlank()
            ? null
            : host.resolveBlockId(crossSection.cutSlopeMaterial);
        float fillRatio = crossSection.fillSlopeRatio;
        float cutRatio = crossSection.cutSlopeRatio;
        int maxHorizontalRun = 16;
        double outerOffset = RoadDimensionUtils.halfExtentFromCenter(shoulderWidth) * unitsPerBlock;

        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            RoadConstructionType type = RoadConstructionClassifier.constructionTypeAt(constructionTypes, i);
            if (type == RoadConstructionType.BRIDGE || type == RoadConstructionType.TUNNEL) {
                continue;
            }
            PathSegment segment = segments.get(i);
            SegmentHeightInfo info = heightInfos.get(i);
            Vec2d leftNormal = PathSegmentGeometry.leftNormal(segment);
            int samples = Math.max(2, (int) Math.ceil(segment.distance / scale));
            for (int j = 1; j < samples; j++) {
                double t = (double) j / samples;
                Vec2d center = segment.start.lerp(segment.end, t);
                int targetY = (int) Math.round(info.targetStart * (1 - t) + info.targetEnd * t);
                targetY = host.snapEndpointElevation(center, targetY);
                Vec2d left = center.add(leftNormal.multiply(shoulderCenterOffset));
                Vec2d right = center.subtract(leftNormal.multiply(shoulderCenterOffset));
                placeSlopeBatterAtPoint(solids, left, targetY, outerOffset, terrain,
                    fillRatio, cutRatio, fillBlockId, cutBlockId, maxHorizontalRun,
                    leftNormal, unitsPerBlock);
                placeSlopeBatterAtPoint(solids, right, targetY, outerOffset, terrain,
                    fillRatio, cutRatio, fillBlockId, cutBlockId, maxHorizontalRun,
                    leftNormal.multiply(-1), unitsPerBlock);
            }
        }
    }

    private static void placeSlopeBatterAtPoint(
            RoadSolidModel solids,
            Vec2d shoulderCenter,
            int targetY,
            double outerOffset,
            TerrainSampler terrain,
            float fillRatio,
            float cutRatio,
            String fillBlockId,
            String cutBlockId,
            int maxHorizontalRun,
            Vec2d outwardNormal,
            double unitsPerBlock) {
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        Vec2d normal = outwardNormal != null && outwardNormal.lengthSquared() > 1e-12
            ? outwardNormal.normalize()
            : new Vec2d(0, 1);
        Vec2d outerEdge = shoulderCenter.add(normal.multiply(outerOffset));

        int groundAtEdge = terrain.sampleSurfaceY(outerEdge);
        if (targetY == groundAtEdge) {
            return;
        }

        boolean isFill = targetY > groundAtEdge;
        int profileDirection = isFill ? -1 : 1;
        float slopeRatio = isFill ? fillRatio : cutRatio;
        int heightDifference = Math.abs(targetY - groundAtEdge);
        int usefulHorizontalRun = Math.min(
            maxHorizontalRun,
            Math.max(2, (int) Math.ceil(heightDifference * Math.max(0.5f, slopeRatio)) + 2));

        List<int[]> profile = RoadSlopeUtils.computeSlopeProfile(
            targetY,
            profileDirection,
            horizontalOffset -> terrain.sampleSurfaceY(
                outerEdge.add(normal.multiply(horizontalOffset * scale))),
            slopeRatio,
            usefulHorizontalRun
        );

        for (int step = 1; step < profile.size(); step++) {
            int[] point = profile.get(step);
            int horizontalOffset = point[0];
            int slopeHeight = point[1];
            Vec2d sample = outerEdge.add(normal.multiply(horizontalOffset * scale));
            int groundY = terrain.sampleSurfaceY(sample);

            if (isFill) {
                for (int y = groundY + 1; y <= slopeHeight; y++) {
                    solids.add(sample, y, RoadSolidLayer.SHOULDER, fillBlockId);
                }
            } else {
                for (int y = slopeHeight + 1; y <= groundY; y++) {
                    solids.add(sample, y, RoadSolidLayer.SHOULDER, "minecraft:air");
                }
                if (cutBlockId != null) {
                    solids.add(sample, slopeHeight, RoadSolidLayer.SHOULDER, cutBlockId);
                }
            }
        }
    }

    private static void generateMedianBlocks(
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<Vec2d> leftBoundary,
            List<Vec2d> rightBoundary,
            String blockId) {
        double totalLength = segments.stream().mapToDouble(s -> s.distance).sum();
        double accumulatedSegmentStart = 0.0;

        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            PathSegment segment = segments.get(i);
            int samples = Math.max(2, (int) Math.ceil(segment.distance));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                int targetY = (int) (info.targetStart * (1 - t) + info.targetEnd * t);
                double normalized = totalLength > 1e-9
                    ? (accumulatedSegmentStart + t * segment.distance) / totalLength
                    : 0.0;
                Vec2d left = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(leftBoundary, normalized);
                Vec2d right = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(rightBoundary, normalized);
                solids.addSpan(left, right, targetY, RoadSolidLayer.MEDIAN, blockId);
            }
            accumulatedSegmentStart += segment.distance;
        }
    }

    private static void generateBridgeStructures(
            CrossSectionHost host,
            RoadSolidModel solids,
            List<BridgeSegment> bridges,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<Vec2d> leftBoundary,
            List<Vec2d> rightBoundary,
            TerrainSampler terrain) {
        if (bridges.isEmpty()) {
            return;
        }
        String pillarBlockId = host.resolveBlockId("material.plot.stone");
        double totalLength = segments.stream().mapToDouble(s -> s.distance).sum();
        double unitsPerBlock = host.estimateCanvasUnitsPerBlock(null, segments);
        double pillarSpacing = Math.max(unitsPerBlock, 6.0 * unitsPerBlock);
        Set<PathSegment> bridgeSegments = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BridgeSegment bridge : bridges) {
            bridgeSegments.add(bridge.segment());
        }

        double accumulated = 0.0;
        double nextPillarDistance = Double.NaN;
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            PathSegment segment = segments.get(i);
            SegmentHeightInfo info = heightInfos.get(i);
            boolean isBridge = bridgeSegments.contains(segment);
            boolean previousIsBridge = i > 0 && bridgeSegments.contains(segments.get(i - 1));
            boolean nextIsBridge = i + 1 < segments.size() && bridgeSegments.contains(segments.get(i + 1));
            double segmentEnd = accumulated + segment.distance;

            if (isBridge) {
                if (!previousIsBridge) {
                    nextPillarDistance = accumulated;
                }
                while (nextPillarDistance <= segmentEnd + 1e-9) {
                    placeBridgePillarCrossSection(
                        host, solids, segment, info, nextPillarDistance, accumulated, totalLength,
                        leftBoundary, rightBoundary, terrain, pillarBlockId);
                    nextPillarDistance += pillarSpacing;
                }
                if (!nextIsBridge && nextPillarDistance - pillarSpacing < segmentEnd - 1e-6) {
                    placeBridgePillarCrossSection(
                        host, solids, segment, info, segmentEnd, accumulated, totalLength,
                        leftBoundary, rightBoundary, terrain, pillarBlockId);
                }
            }
            accumulated = segmentEnd;
        }
    }

    private static void placeBridgePillarCrossSection(
            CrossSectionHost host,
            RoadSolidModel solids,
            PathSegment segment,
            SegmentHeightInfo info,
            double globalDistance,
            double segmentStartDistance,
            double totalLength,
            List<Vec2d> leftBoundary,
            List<Vec2d> rightBoundary,
            TerrainSampler terrain,
            String pillarBlockId) {
        double t = segment.distance > 1e-9
            ? Math.max(0.0, Math.min(1.0, (globalDistance - segmentStartDistance) / segment.distance))
            : 0.0;
        double normalized = totalLength > 1e-9 ? globalDistance / totalLength : 0.0;
        int targetY = (int) Math.round(info.targetStart * (1 - t) + info.targetEnd * t);
        Vec2d center = segment.start.lerp(segment.end, t);
        Vec2d left = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(leftBoundary, normalized);
        Vec2d right = RoadGeometryUtils.interpolatePolylineByNormalizedDistance(rightBoundary, normalized);
        placeBridgePillars(host, solids, center, targetY, terrain, pillarBlockId);
        placeBridgePillars(host, solids, left, targetY, terrain, pillarBlockId);
        placeBridgePillars(host, solids, right, targetY, terrain, pillarBlockId);
    }

    private static void placeBridgePillars(
            CrossSectionHost host,
            RoadSolidModel solids,
            Vec2d canvasPos,
            int deckY,
            TerrainSampler terrain,
            String blockId) {
        int groundY = terrain.sampleSurfaceY(canvasPos);
        if (deckY - groundY <= host.bridgeThreshold()) {
            return;
        }
        for (int y = groundY + 1; y < deckY; y++) {
            solids.add(canvasPos, y, RoadSolidLayer.BRIDGE, blockId);
        }
    }

    private static void forEachPathSample(
            CrossSectionHost host,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            PathSampleConsumer consumer) {
        double scale = host.estimateCanvasUnitsPerBlock(null, segments);
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            PathSegment segment = segments.get(i);
            SegmentHeightInfo info = heightInfos.get(i);
            Vec2d leftNormal = PathSegmentGeometry.leftNormal(segment);
            int samples = Math.max(2, (int) Math.ceil(segment.distance / scale));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                Vec2d center = segment.start.lerp(segment.end, t);
                int targetY = (int) Math.round(info.targetStart * (1 - t) + info.targetEnd * t);
                targetY = host.snapEndpointElevation(center, targetY);
                consumer.accept(center, leftNormal, targetY);
            }
        }
    }
}
