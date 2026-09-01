package com.plot.plugin.road.pipeline.crosssection;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadSlopeUtils;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.facility.StationFacilityResolver;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.RoadEdgeBuildMetrics;
import com.plot.plugin.road.pipeline.RoadPathStationSampler;
import com.plot.plugin.road.pipeline.StationFacilityBuildContext;
import com.plot.plugin.road.pipeline.RoadGenerationPipelineContext;
import com.plot.plugin.road.pipeline.construction.ConstructionDetection;
import com.plot.plugin.road.pipeline.construction.RoadConstructionClassifier;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.geometry.PathSegmentGeometry;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Builds cross-section layers: Carriageway, Shoulder, BikeLane, Median, Sidewalk, drainage, slope batter.
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
        metrics.bridgeCount = detection.runs().isEmpty()
            ? detection.bridges().size()
            : (int) detection.runCount(RoadConstructionType.BRIDGE);
        metrics.tunnelCount = detection.runs().isEmpty()
            ? detection.tunnels().size()
            : (int) detection.runCount(RoadConstructionType.TUNNEL);

        generateCarriagewayBlocks(
            crossSectionHost,
            solids,
            segments,
            heightInfos,
            detection.constructionTypes(),
            terrain,
            crossSections,
            ctx.carriagewaySeedKey(),
            unitsPerBlock,
            designElevation);

        generateShoulderBlocks(
            crossSectionHost, solids, segments, heightInfos, crossSections, unitsPerBlock, designElevation);
        generateBikeLaneBlocks(
            crossSectionHost, solids, segments, heightInfos, crossSections, unitsPerBlock, designElevation);
        generateSidewalkBlocks(
            crossSectionHost, solids, segments, heightInfos, crossSections, unitsPerBlock, designElevation);

        if (!usesStationGatedDrainage(ctx.request().stationFacilities())) {
            generateDrainageChannels(
                crossSectionHost, solids, segments, heightInfos, crossSections, unitsPerBlock, designElevation);
        }

        generateSlopeBatterBlocks(
            crossSectionHost,
            solids,
            segments,
            heightInfos,
            crossSections,
            terrain,
            unitsPerBlock,
            detection.constructionTypes(),
            designElevation);

        generateMedianBlocks(
            crossSectionHost, solids, segments, heightInfos, crossSections, unitsPerBlock, designElevation);
    }

    private interface CrossSectionHost {
        BlockPos canvasToBlockPos(Vec2d canvasPos);

        String resolveBlockId(String material);

        int snapEndpointElevation(Vec2d center, int targetY);

        double estimateCanvasUnitsPerBlock(List<Vec2d> pathPoints, List<PathSegment> segments);

        int bridgeThreshold();

        boolean generateBridgePillars();

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
            };
        }
    }

    private static void generateCarriagewayBlocks(
            CrossSectionHost host,
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<RoadConstructionType> constructionTypes,
            TerrainSampler terrain,
            CrossSectionBuildContext crossSections,
            String seedKey,
            double unitsPerBlock,
            DesignElevationSource designElevation) {
        String bridgeStructureBlockId = host.resolveBlockId("material.plot.stone");
        boolean chainForward = crossSections.samplingOriented().forward();
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        double geometryLocalBase = 0.0;
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            PathSegment segment = segments.get(i);
            boolean bridgeSegment = RoadConstructionClassifier.constructionTypeAt(
                constructionTypes, i) == RoadConstructionType.BRIDGE;
            SegmentHeightInfo info = heightInfos.get(i);
            Vec2d normal = PathSegmentGeometry.chainLeftNormal(segment, chainForward);
            int samples = Math.max(2, (int) Math.ceil(segment.distance / scale));
            Vec2d previousCenter = null;
            MaterialMix previousMaterial = null;
            int previousMinOffset = 0;
            int previousMaxOffset = 0;
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                Vec2d center = segment.start.lerp(segment.end, t);
                double geometryLocal = geometryLocalBase + segment.distance * t;
                int targetY = DesignElevationSource.resolveTargetElevation(
                    designElevation,
                    info,
                    geometryLocal,
                    t);
                targetY = host.snapEndpointElevation(center, targetY);
                double chainage = crossSections.chainageAtGeometryLocal(geometryLocal);
                ResolvedCrossSection crossSection = crossSections.resolve(chainage);
                int carriagewayWidth = crossSection.carriagewayWidth;
                MaterialMix carriagewayMaterial = crossSection.carriagewayMaterial;
                int minOffset = RoadDimensionUtils.minLateralOffset(carriagewayWidth);
                int maxOffset = RoadDimensionUtils.maxLateralOffset(carriagewayWidth);
                for (int lateral = minOffset; lateral <= maxOffset; lateral++) {
                    Vec2d planPoint = center.add(normal.multiply(lateral * scale));
                    BlockPos pos = host.canvasToBlockPos(planPoint).withY(targetY);
                    String blockId = MaterialMixResolver.resolve(
                        carriagewayMaterial, pos, seedKey, host::resolveBlockId);
                    solids.add(planPoint, targetY, RoadSolidLayer.ROAD, blockId);
                    // A shallow, cost-selected bridge may sit directly on existing ground at
                    // individual voxels. Only add a structural deck plate where a real gap
                    // exceeds the hard bridge threshold; pillars still follow the finalized
                    // construction run. This also keeps output stable when an edge is split.
                    if (bridgeSegment
                            && targetY - terrain.sampleSurfaceY(planPoint) > host.bridgeThreshold()) {
                        solids.add(
                            planPoint,
                            targetY - 1,
                            RoadSolidLayer.BRIDGE,
                            bridgeStructureBlockId);
                    }
                    if (previousCenter != null
                        && lateral >= previousMinOffset
                        && lateral <= previousMaxOffset
                        && carriagewayMaterial == previousMaterial) {
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
                previousMaterial = carriagewayMaterial;
                previousMinOffset = minOffset;
                previousMaxOffset = maxOffset;
            }
            geometryLocalBase += segment.distance;
        }

        generateBridgeStructures(
            host, solids, constructionTypes, segments, heightInfos,
            crossSections, terrain, unitsPerBlock, designElevation);
    }

    private static void generateShoulderBlocks(
            CrossSectionHost host,
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            double unitsPerBlock,
            DesignElevationSource designElevation) {
        RoadPathStationSampler.forEach(
            segments,
            heightInfos,
            crossSections.samplingOriented(),
            unitsPerBlock,
            designElevation,
            host::snapEndpointElevation,
            (center, leftNormal, targetY, chainage) -> {
                ResolvedCrossSection crossSection = crossSections.resolve(chainage);
                if (!crossSection.includeShoulder || crossSection.shoulderWidth <= 0) {
                    return;
                }
                double centerOffset = crossSection.shoulderCenterOffset() * unitsPerBlock;
                String blockId = host.resolveBlockId(crossSection.shoulderMaterial);
                Vec2d left = center.add(leftNormal.multiply(centerOffset));
                Vec2d right = center.subtract(leftNormal.multiply(centerOffset));
                solids.addLateralStrip(
                    left, leftNormal, crossSection.shoulderWidth, targetY,
                    RoadSolidLayer.SHOULDER, blockId, unitsPerBlock);
                solids.addLateralStrip(
                    right, leftNormal, crossSection.shoulderWidth, targetY,
                    RoadSolidLayer.SHOULDER, blockId, unitsPerBlock);
            });
    }

    private static void generateBikeLaneBlocks(
            CrossSectionHost host,
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            double unitsPerBlock,
            DesignElevationSource designElevation) {
        RoadPathStationSampler.forEach(
            segments,
            heightInfos,
            crossSections.samplingOriented(),
            unitsPerBlock,
            designElevation,
            host::snapEndpointElevation,
            (center, leftNormal, targetY, chainage) -> {
                ResolvedCrossSection crossSection = crossSections.resolve(chainage);
                if (!crossSection.includeBikeLane || crossSection.bikeLaneWidth <= 0) {
                    return;
                }
                double centerOffset = crossSection.bikeLaneCenterOffset() * unitsPerBlock;
                String blockId = host.resolveBlockId(crossSection.bikeLaneMaterial);
                Vec2d left = center.add(leftNormal.multiply(centerOffset));
                Vec2d right = center.subtract(leftNormal.multiply(centerOffset));
                solids.addLateralStrip(
                    left, leftNormal, crossSection.bikeLaneWidth, targetY,
                    RoadSolidLayer.BIKE_LANE, blockId, unitsPerBlock);
                solids.addLateralStrip(
                    right, leftNormal, crossSection.bikeLaneWidth, targetY,
                    RoadSolidLayer.BIKE_LANE, blockId, unitsPerBlock);
            });
    }

    private static void generateSidewalkBlocks(
            CrossSectionHost host,
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            double unitsPerBlock,
            DesignElevationSource designElevation) {
        RoadPathStationSampler.forEach(
            segments,
            heightInfos,
            crossSections.samplingOriented(),
            unitsPerBlock,
            designElevation,
            host::snapEndpointElevation,
            (center, leftNormal, targetY, chainage) -> {
                ResolvedCrossSection crossSection = crossSections.resolve(chainage);
                if (!crossSection.includeSidewalk || crossSection.sidewalkWidth <= 0) {
                    return;
                }
                double centerOffset = crossSection.sidewalkCenterOffset() * unitsPerBlock;
                String blockId = host.resolveBlockId(crossSection.sidewalkMaterial);
                Vec2d left = center.add(leftNormal.multiply(centerOffset));
                Vec2d right = center.subtract(leftNormal.multiply(centerOffset));
                solids.addLateralStrip(
                    left, leftNormal, crossSection.sidewalkWidth, targetY,
                    RoadSolidLayer.SIDEWALK, blockId, unitsPerBlock);
                solids.addLateralStrip(
                    right, leftNormal, crossSection.sidewalkWidth, targetY,
                    RoadSolidLayer.SIDEWALK, blockId, unitsPerBlock);
            });
    }

    private static void generateDrainageChannels(
            CrossSectionHost host,
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            double unitsPerBlock,
            DesignElevationSource designElevation) {
        String blockId = host.resolveBlockId("material.plot.gravel");
        RoadPathStationSampler.forEach(
            segments,
            heightInfos,
            crossSections.samplingOriented(),
            unitsPerBlock,
            designElevation,
            host::snapEndpointElevation,
            (center, leftNormal, targetY, chainage) -> {
                ResolvedCrossSection crossSection = crossSections.resolve(chainage);
                if (!crossSection.includeDrain) {
                    return;
                }
                int drainY = targetY - 1;
                double drainageOffset = crossSection.outerDrainageOffset() * unitsPerBlock;
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
            CrossSectionBuildContext crossSections,
            TerrainSampler terrain,
            double unitsPerBlock,
            List<RoadConstructionType> constructionTypes,
            DesignElevationSource designElevation) {
        int maxHorizontalRun = 16;
        boolean chainForward = crossSections.samplingOriented().forward();
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        double geometryLocalBase = 0.0;
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            RoadConstructionType type = RoadConstructionClassifier.constructionTypeAt(constructionTypes, i);
            if (type == RoadConstructionType.BRIDGE || type == RoadConstructionType.TUNNEL) {
                geometryLocalBase += segments.get(i).distance;
                continue;
            }
            PathSegment segment = segments.get(i);
            SegmentHeightInfo info = heightInfos.get(i);
            Vec2d leftNormal = PathSegmentGeometry.chainLeftNormal(segment, chainForward);
            int samples = Math.max(2, (int) Math.ceil(segment.distance / scale));
            for (int j = 1; j < samples; j++) {
                double t = (double) j / samples;
                Vec2d center = segment.start.lerp(segment.end, t);
                double geometryLocal = geometryLocalBase + segment.distance * t;
                int targetY = DesignElevationSource.resolveTargetElevation(
                    designElevation,
                    info,
                    geometryLocal,
                    t);
                targetY = host.snapEndpointElevation(center, targetY);
                double chainage = crossSections.chainageAtGeometryLocal(geometryLocal);
                ResolvedCrossSection crossSection = crossSections.resolve(chainage);
                if (!crossSection.includeSlopeBatter) {
                    continue;
                }
                String fillBlockId = host.resolveBlockId(crossSection.fillSlopeMaterial);
                String cutBlockId = crossSection.cutSlopeMaterial == null || crossSection.cutSlopeMaterial.isBlank()
                    ? null
                    : host.resolveBlockId(crossSection.cutSlopeMaterial);
                float fillRatio = crossSection.fillSlopeRatio;
                float cutRatio = crossSection.cutSlopeRatio;
                double shoulderCenterOffset = crossSection.slopeAnchorCenterOffset() * unitsPerBlock;
                double outerOffset = RoadDimensionUtils.halfExtentFromCenter(
                    crossSection.slopeAnchorBandWidth()) * unitsPerBlock;
                Vec2d left = center.add(leftNormal.multiply(shoulderCenterOffset));
                Vec2d right = center.subtract(leftNormal.multiply(shoulderCenterOffset));
                placeSlopeBatterAtPoint(solids, left, targetY, outerOffset, terrain,
                    fillRatio, cutRatio, fillBlockId, cutBlockId, maxHorizontalRun,
                    leftNormal, unitsPerBlock);
                placeSlopeBatterAtPoint(solids, right, targetY, outerOffset, terrain,
                    fillRatio, cutRatio, fillBlockId, cutBlockId, maxHorizontalRun,
                    leftNormal.multiply(-1), unitsPerBlock);
            }
            geometryLocalBase += segment.distance;
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
            CrossSectionHost host,
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            double unitsPerBlock,
            DesignElevationSource designElevation) {
        RoadPathStationSampler.forEach(
            segments,
            heightInfos,
            crossSections.samplingOriented(),
            unitsPerBlock,
            designElevation,
            host::snapEndpointElevation,
            (center, leftNormal, targetY, chainage) -> {
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
            DesignElevationSource designElevation) {
        if (constructionTypes.stream().noneMatch(type -> type == RoadConstructionType.BRIDGE)) {
            return;
        }
        if (!host.generateBridgePillars()) {
            return;
        }
        String pillarBlockId = host.resolveBlockId("material.plot.stone");
        double pillarSpacing = Math.max(unitsPerBlock, 6.0 * unitsPerBlock);
        double accumulated = 0.0;
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            PathSegment segment = segments.get(i);
            SegmentHeightInfo info = heightInfos.get(i);
            boolean isBridge = RoadConstructionClassifier.constructionTypeAt(
                constructionTypes, i) == RoadConstructionType.BRIDGE;
            double segmentEnd = accumulated + segment.distance;

            if (isBridge) {
                double chainageA = crossSections.chainageAtGeometryLocal(accumulated);
                double chainageB = crossSections.chainageAtGeometryLocal(segmentEnd);
                double minChainage = Math.min(chainageA, chainageB);
                double maxChainage = Math.max(chainageA, chainageB);
                double pillarChainage = Math.ceil((minChainage - 1e-9) / pillarSpacing) * pillarSpacing;
                while (pillarChainage <= maxChainage + 1e-9) {
                    double geometryDistance = crossSections.orientedSegment() != null
                        ? crossSections.orientedSegment()
                            .geometryLocalAtRoadStation(pillarChainage)
                            .orElse(accumulated)
                        : pillarChainage;
                    placeBridgePillarCrossSection(
                        host, solids, segment, info, geometryDistance, accumulated,
                        crossSections, terrain, pillarBlockId, unitsPerBlock, designElevation);
                    pillarChainage += pillarSpacing;
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
            CrossSectionBuildContext crossSections,
            TerrainSampler terrain,
            String pillarBlockId,
            double unitsPerBlock,
            DesignElevationSource designElevation) {
        double t = segment.distance > 1e-9
            ? Math.max(0.0, Math.min(1.0, (globalDistance - segmentStartDistance) / segment.distance))
            : 0.0;
        int targetY = DesignElevationSource.resolveTargetElevation(
            designElevation,
            info,
            globalDistance,
            t);
        Vec2d center = segment.start.lerp(segment.end, t);
        Vec2d leftNormal = PathSegmentGeometry.chainLeftNormal(
            segment,
            crossSections.samplingOriented().forward());
        double chainage = crossSections.chainageAtGeometryLocal(globalDistance);
        ResolvedCrossSection crossSection = crossSections.resolve(chainage);
        double halfExtent = RoadDimensionUtils.halfExtentFromCenter(crossSection.carriagewayWidth) * unitsPerBlock;
        Vec2d left = center.add(leftNormal.multiply(halfExtent));
        Vec2d right = center.subtract(leftNormal.multiply(halfExtent));
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
        for (int y = groundY + 1; y < deckY; y++) {
            solids.add(canvasPos, y, RoadSolidLayer.BRIDGE, blockId);
        }
    }

    private static boolean usesStationGatedDrainage(StationFacilityBuildContext stationFacilities) {
        if (stationFacilities == null || !stationFacilities.isActive()) {
            return false;
        }
        return StationFacilityResolver.usesStationGatedDrainage(stationFacilities.road());
    }
}
