package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixResolver;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.RoadSlopeUtils;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelCrossSectionMask;
import com.plot.plugin.road.tunnel.TunnelPortalExcavationMask;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.RoadGenerationPipelineContext;
import com.plot.plugin.road.pipeline.RoadPathStationSampler;
import com.plot.plugin.road.pipeline.construction.RoadConstructionClassifier;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import com.plot.plugin.road.pipeline.construction.RoadEarthworkPolicy;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadVoxelRasterizer;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 构建连续道路施工计划：1-block 栅格化路径 span + 列级边坡 envelope 合并。
 */
public final class RoadCorridorPlanBuilder {
    private RoadCorridorPlanBuilder() {
    }

    private record StationSample(
            Vec2d center,
            Vec2d leftNormal,
            int targetY,
            double chainage,
            int segmentIndex,
            RoadConstructionType constructionType) {
    }

    public static RoadCorridorPlan build(RoadGenerationPipelineContext ctx, RoadGenerationPipelineContext.Host host) {
        PlanHost planHost = PlanHost.from(
            host, ctx.request().terrainStyle(), ctx.request().tunnelStyle());
        RoadCorridorPlan plan = build(
            ctx.segments(),
            ctx.heightInfos(),
            ctx.request().crossSections(),
            ctx.detection().constructionTypes(),
            ctx.request().designElevation(),
            ctx.buildProfile(),
            ctx.terrain(),
            ctx.unitsPerBlock(),
            ctx.carriagewaySeedKey(),
            ctx.request().junctionCorridor(),
            planHost);
        return plan;
    }

    static RoadCorridorPlan build(
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            List<RoadConstructionType> constructionTypes,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            TerrainSampler terrain,
            double unitsPerBlock,
            String carriagewaySeedKey,
            JunctionCorridorBuildContext junctionCorridor,
            PlanHost host) {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        List<StationSample> stations = collectStations(
            segments, heightInfos, crossSections, constructionTypes,
            designElevation, buildProfile, host::snapEndpointElevation, unitsPerBlock,
            junctionCorridor != null && junctionCorridor.exclusionPolygons().isEmpty()
                ? junctionCorridor.endpointTrim() : null);
        if (stations.isEmpty()) {
            return plan;
        }
        RoadSystemConfig config = host.config();
        int tunnelThreshold = host.tunnelThreshold();
        List<SlopeProfileSample> slopeSamples = new ArrayList<>();
        long[] stationSampleSeq = {0L};
        for (int i = 0; i < stations.size(); i++) {
            StationSample sample = stations.get(i);
            ResolvedCrossSection crossSection = crossSections.resolve(sample.chainage());
            boolean suppressBatter = shouldSuppressCorridorBatter(
                stations, i, terrain, tunnelThreshold);
            rasterizeStation(plan, sample, crossSection, terrain, unitsPerBlock,
                carriagewaySeedKey, host, config, tunnelThreshold, slopeSamples, stationSampleSeq,
                suppressBatter);
            if (i > 0) {
                // 邻接洞门/洞身/深覆土的路基站及其 span 都不放坡，避免边坡贴到洞口掏山
                boolean suppressSpanBatter = suppressBatter
                    || shouldSuppressCorridorBatter(stations, i - 1, terrain, tunnelThreshold);
                rasterizeSpan(plan, stations.get(i - 1), sample, crossSection, terrain,
                    unitsPerBlock, carriagewaySeedKey, host, config, tunnelThreshold, slopeSamples,
                    stationSampleSeq, suppressSpanBatter);
            }
        }
        SlopeSurfaceConsolidator.apply(plan, slopeSamples, terrain);
        // 空间保护：剔除伸进洞身/洞门山体包络的开放边坡（仅靠站点邻接不够）
        ResolvedCrossSection protectSection = stations.isEmpty()
            ? ResolvedCrossSection.fromConfig(config)
            : crossSections.resolve(stations.getFirst().chainage());
        TunnelMountainProtection.from(plan, protectSection).scrubOpenBatter(plan);
        SlopeEnvelopeClearancePass.apply(plan, terrain);
        // Exact polygon ownership, rather than also skipping an entire endpoint
        // length, keeps the approach pavement connected to the plaza boundary.
        JunctionCorridorPlanFilter.apply(plan, junctionCorridor);
        return plan;
    }

    private static List<StationSample> collectStations(
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            List<RoadConstructionType> constructionTypes,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            RoadPathStationSampler.ElevationSnapper snapper,
            double unitsPerBlock,
            com.plot.plugin.road.pipeline.facility.StationFacilityJunctionTrim.FacilityEndpointTrim endpointTrim) {
        List<StationSample> stations = new ArrayList<>();
        RoadPathStationSampler.forEach(
            segments,
            heightInfos,
            crossSections.samplingOriented(),
            unitsPerBlock,
            designElevation,
            buildProfile,
            snapper,
            endpointTrim,
            (center, leftNormal, targetY, chainage, segmentIndex) -> stations.add(
                new StationSample(
                    center,
                    leftNormal,
                    targetY,
                    chainage,
                    segmentIndex,
                    RoadConstructionClassifier.constructionTypeAt(constructionTypes, segmentIndex))));
        return stations;
    }

    /** 邻接窗口：锐弯处边坡扇可能跨过一站，故检查 ±2 站内是否有隧道族/棚洞。 */
    private static final int TUNNEL_BATTER_SUPPRESS_STATION_RADIUS = 2;

    private static boolean shouldSuppressCorridorBatter(
            List<StationSample> stations,
            int index,
            TerrainSampler terrain,
            int tunnelThreshold) {
        if (stations == null || index < 0 || index >= stations.size()) {
            return false;
        }
        StationSample sample = stations.get(index);
        boolean nearTunnel = adjacentTunnelOrShelter(stations, index);
        int cover = 0;
        if (terrain != null && sample.center() != null && terrain.isChunkLoaded(sample.center())) {
            cover = terrain.sampleSurfaceY(sample.center()) - sample.targetY();
        }
        return RoadEarthworkPolicy.suppressOpenDaylightBatter(
            sample.constructionType(), nearTunnel, cover, tunnelThreshold);
    }

    private static boolean adjacentTunnelOrShelter(List<StationSample> stations, int index) {
        if (stations == null || index < 0 || index >= stations.size()) {
            return false;
        }
        int from = Math.max(0, index - TUNNEL_BATTER_SUPPRESS_STATION_RADIUS);
        int to = Math.min(stations.size() - 1, index + TUNNEL_BATTER_SUPPRESS_STATION_RADIUS);
        for (int i = from; i <= to; i++) {
            if (i == index) {
                continue;
            }
            RoadConstructionType type = stations.get(i).constructionType();
            if (type != null && (type.isTunnelFamily() || type.isRockShelter())) {
                return true;
            }
        }
        return false;
    }

    private static void rasterizeStation(
            RoadCorridorPlan plan,
            StationSample sample,
            ResolvedCrossSection crossSection,
            TerrainSampler terrain,
            double unitsPerBlock,
            String seedKey,
            PlanHost host,
            RoadSystemConfig config,
            int tunnelThreshold,
            List<SlopeProfileSample> slopeSamples,
            long[] stationSampleSeq,
            boolean suppressBatter) {
        if (sample.constructionType() == RoadConstructionType.TUNNEL_PORTAL) {
            Vec2d leftNormal = normalize(sample.leftNormal());
            // leftNormal = (-fy, fx) for forward (fx, fy) → forward = (ly, -lx)
            Vec2d forward = new Vec2d(leftNormal.y, -leftNormal.x);
            plan.registerPortalBackfillSite(new PortalBackfillSite(
                sample.center(),
                leftNormal,
                forward,
                sample.targetY(),
                RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection),
                unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0));
        }
        rasterizeSurfaceBands(plan, sample.center(), sample.center(), sample.leftNormal(), sample.leftNormal(),
            sample.targetY(), sample.targetY(), crossSection, terrain, unitsPerBlock,
            seedKey, host, sample.constructionType(), config, tunnelThreshold);
        if (!suppressBatter
                && crossSection.includeSlopeBatter
                && RoadEarthworkPolicy.allowsCorridorBatter(sample.constructionType())) {
            slopeSamples.addAll(mergeSlopeWedge(
                plan, sample, crossSection, terrain, unitsPerBlock, host, stationSampleSeq,
                tunnelThreshold));
        }
    }

    private static void rasterizeSpan(
            RoadCorridorPlan plan,
            StationSample from,
            StationSample to,
            ResolvedCrossSection crossSection,
            TerrainSampler terrain,
            double unitsPerBlock,
            String seedKey,
            PlanHost host,
            RoadSystemConfig config,
            int tunnelThreshold,
            List<SlopeProfileSample> slopeSamples,
            long[] stationSampleSeq,
            boolean suppressSpanBatter) {
        ResolvedCrossSection toSection = crossSection;
        RoadConstructionType fromType = from.constructionType();
        RoadConstructionType toType = to.constructionType();
        // Split type-boundary spans so PORTAL→TUNNEL / ROAD→BRIDGE does not tag the
        // transition half as STRUCTURE_ONLY (which strips slope and skips open clearance).
        if (fromType == toType) {
            rasterizeSurfaceBands(plan, from.center(), to.center(), from.leftNormal(), to.leftNormal(),
                from.targetY(), to.targetY(), toSection, terrain, unitsPerBlock,
                seedKey, host, toType, config, tunnelThreshold);
        } else {
            Vec2d midCenter = from.center().lerp(to.center(), 0.5);
            int midY = (int) Math.round(from.targetY() + (to.targetY() - from.targetY()) * 0.5);
            Vec2d midNormal = normalize(from.leftNormal().lerp(to.leftNormal(), 0.5));
            rasterizeSurfaceBands(plan, from.center(), midCenter, from.leftNormal(), midNormal,
                from.targetY(), midY, toSection, terrain, unitsPerBlock,
                seedKey, host, fromType, config, tunnelThreshold);
            rasterizeSurfaceBands(plan, midCenter, to.center(), midNormal, to.leftNormal(),
                midY, to.targetY(), toSection, terrain, unitsPerBlock,
                seedKey, host, toType, config, tunnelThreshold);
        }
        // 两端都允许且不贴隧道/深覆土才放坡：避免洞口路基 span 把边坡伸进山体
        if (!suppressSpanBatter
                && toSection.includeSlopeBatter
                && RoadEarthworkPolicy.allowsCorridorBatter(fromType)
                && RoadEarthworkPolicy.allowsCorridorBatter(toType)) {
            slopeSamples.addAll(mergeSlopeSpan(
                plan, from, to, toSection, terrain, unitsPerBlock, host, stationSampleSeq,
                tunnelThreshold));
        }
    }

    private static void rasterizeSurfaceBands(
            RoadCorridorPlan plan,
            Vec2d fromCenter,
            Vec2d toCenter,
            Vec2d fromNormal,
            Vec2d toNormal,
            int fromY,
            int toY,
            ResolvedCrossSection crossSection,
            TerrainSampler terrain,
            double unitsPerBlock,
            String seedKey,
            PlanHost host,
            RoadConstructionType constructionType,
            RoadSystemConfig config,
            int tunnelThreshold) {
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        Vec2d startNormal = normalize(fromNormal);
        Vec2d endNormal = normalize(toNormal);

        CrossSectionSpanRasterizer.rasterizeSpan(
            plan, fromCenter, toCenter, startNormal, endNormal, fromY, toY, scale, crossSection,
            seedKey, host, terrain, constructionType, tunnelThreshold);
        expandClearanceEnvelope(plan, fromCenter, toCenter, startNormal, fromY, toY, scale,
            crossSection, terrain, constructionType, config, tunnelThreshold, host);
    }

    private static void expandClearanceEnvelope(
            RoadCorridorPlan plan,
            Vec2d fromCenter,
            Vec2d toCenter,
            Vec2d leftNormal,
            int fromY,
            int toY,
            double scale,
            ResolvedCrossSection crossSection,
            TerrainSampler terrain,
            RoadConstructionType constructionType,
            RoadSystemConfig config,
            int tunnelThreshold,
            PlanHost host) {
        if (!RoadEarthworkPolicy.of(constructionType).allowsOpenCorridorClearance()) {
            return;
        }
        // Hard-surface envelope only. Slope laterals get their own floor-at-batter
        // clearance in SlopeEnvelopeClearancePass; do not punch OPEN from road Y.
        int envelopeWidth = RoadCorridorWidth.pavementEnvelopeWidthBlocks(crossSection);
        if (constructionType == RoadConstructionType.TUNNEL_PORTAL
                || constructionType == RoadConstructionType.BRIDGE_ABUTMENT) {
            envelopeWidth = Math.min(envelopeWidth, RoadCorridorWidth.PORTAL_TRANSITION_CLEAR_BAND_BLOCKS);
        }
        if (envelopeWidth <= 0) {
            return;
        }
        int minOffset = RoadDimensionUtils.minLateralOffset(envelopeWidth);
        int maxOffset = RoadDimensionUtils.maxLateralOffset(envelopeWidth);
        for (int lateral = minOffset; lateral <= maxOffset; lateral++) {
            double offset = lateral * scale;
            writeClearanceSpan(plan, fromCenter, toCenter, leftNormal, offset, fromY, toY,
                terrain, constructionType, tunnelThreshold, host, lateral);
        }
    }

    private static void writeClearanceSpan(
            RoadCorridorPlan plan,
            Vec2d fromCenter,
            Vec2d toCenter,
            Vec2d leftNormal,
            double lateralOffset,
            int fromY,
            int toY,
            TerrainSampler terrain,
            RoadConstructionType constructionType,
            int tunnelThreshold,
            PlanHost host,
            int corridorLateral) {
        Vec2d fromPoint = fromCenter.add(leftNormal.multiply(lateralOffset));
        Vec2d toPoint = toCenter.add(leftNormal.multiply(lateralOffset));
        List<Vec2d> span = RoadVoxelRasterizer.sampleSpanPoints(fromPoint, toPoint);
        int last = span.size() - 1;
        for (int i = 0; i < span.size(); i++) {
            Vec2d planPoint = host.canonicalColumnPoint(span.get(i));
            int surfaceY = last == 0
                ? fromY
                : (int) Math.round(fromY + (toY - fromY) * ((double) i / last));
            int worldX = host.worldX(planPoint);
            int worldZ = host.worldZ(planPoint);
            if (!terrain.isChunkLoaded(worldX, worldZ)) {
                continue;
            }
            ColumnKey key = new ColumnKey(worldX, worldZ);
            RoadCorridorConstructionMode mode = toMode(constructionType);
            int clearTopY = computeClearTopY(mode, surfaceY, planPoint, terrain, tunnelThreshold, host);
            Integer lateralTag = constructionType == RoadConstructionType.TUNNEL_PORTAL
                ? corridorLateral
                : null;
            plan.putClearance(key, new ClearanceCell(
                planPoint, surfaceY, clearTopY, mode, constructionType, lateralTag));
        }
    }

    private static void rasterizeLateralBand(
            RoadCorridorPlan plan,
            Vec2d fromCenter,
            Vec2d toCenter,
            Vec2d leftNormal,
            int fromY,
            int toY,
            double scale,
            int widthBlocks,
            double centerOffset,
            RoadSolidLayer layer,
            MaterialMix materialMix,
            String seedKey,
            PlanHost host,
            TerrainSampler terrain,
            RoadConstructionType constructionType,
            RoadSystemConfig config,
            int tunnelThreshold) {
        if (widthBlocks <= 0) {
            return;
        }
        int minOffset = RoadDimensionUtils.minLateralOffset(widthBlocks);
        int maxOffset = RoadDimensionUtils.maxLateralOffset(widthBlocks);
        rasterizeOffsetBand(
            plan, fromCenter, toCenter, leftNormal, fromY, toY, scale,
            minOffset, maxOffset, centerOffset, true, layer, materialMix, seedKey, host,
            terrain, constructionType, tunnelThreshold);
    }

    private static void rasterizeLateralBand(
            RoadCorridorPlan plan,
            Vec2d fromCenter,
            Vec2d toCenter,
            Vec2d leftNormal,
            int fromY,
            int toY,
            double scale,
            int widthBlocks,
            double centerOffset,
            RoadSolidLayer layer,
            String materialId,
            PlanHost host,
            TerrainSampler terrain,
            RoadConstructionType constructionType,
            int tunnelThreshold) {
        if (widthBlocks <= 0) {
            return;
        }
        int minOffset = RoadDimensionUtils.minLateralOffset(widthBlocks);
        int maxOffset = RoadDimensionUtils.maxLateralOffset(widthBlocks);
        rasterizeOffsetBand(
            plan, fromCenter, toCenter, leftNormal, fromY, toY, scale,
            minOffset, maxOffset, centerOffset, true, layer, materialId, host,
            terrain, constructionType, tunnelThreshold);
    }

    private static void rasterizeOffsetBand(
            RoadCorridorPlan plan,
            Vec2d fromCenter,
            Vec2d toCenter,
            Vec2d leftNormal,
            int fromY,
            int toY,
            double scale,
            int minOffset,
            int maxOffset,
            double centerOffset,
            boolean mirrorToOppositeSide,
            RoadSolidLayer layer,
            MaterialMix materialMix,
            String seedKey,
            PlanHost host,
            TerrainSampler terrain,
            RoadConstructionType constructionType,
            int tunnelThreshold) {
        for (int lateral = minOffset; lateral <= maxOffset; lateral++) {
            double offset = lateral * scale;
            writeLateralSpan(plan, fromCenter, toCenter, leftNormal, fromY, toY, scale,
                centerOffset + offset, layer, materialMix, seedKey, host,
                terrain, constructionType, tunnelThreshold);
            if (mirrorToOppositeSide) {
                writeLateralSpan(plan, fromCenter, toCenter, leftNormal, fromY, toY, scale,
                    -(centerOffset + offset), layer, materialMix, seedKey, host,
                    terrain, constructionType, tunnelThreshold);
            }
        }
    }

    private static void rasterizeOffsetBand(
            RoadCorridorPlan plan,
            Vec2d fromCenter,
            Vec2d toCenter,
            Vec2d leftNormal,
            int fromY,
            int toY,
            double scale,
            int minOffset,
            int maxOffset,
            double centerOffset,
            boolean mirrorToOppositeSide,
            RoadSolidLayer layer,
            String materialId,
            PlanHost host,
            TerrainSampler terrain,
            RoadConstructionType constructionType,
            int tunnelThreshold) {
        for (int lateral = minOffset; lateral <= maxOffset; lateral++) {
            double offset = lateral * scale;
            writeLateralSpan(plan, fromCenter, toCenter, leftNormal, fromY, toY, scale,
                centerOffset + offset, layer, materialId, host, terrain, constructionType, tunnelThreshold);
            if (mirrorToOppositeSide) {
                writeLateralSpan(plan, fromCenter, toCenter, leftNormal, fromY, toY, scale,
                    -(centerOffset + offset), layer, materialId, host, terrain, constructionType, tunnelThreshold);
            }
        }
    }

    private static void writeLateralSpan(
            RoadCorridorPlan plan,
            Vec2d fromCenter,
            Vec2d toCenter,
            Vec2d leftNormal,
            int fromY,
            int toY,
            double scale,
            double lateralOffset,
            RoadSolidLayer layer,
            MaterialMix materialMix,
            String seedKey,
            PlanHost host,
            TerrainSampler terrain,
            RoadConstructionType constructionType,
            int tunnelThreshold) {
        Vec2d fromPoint = fromCenter.add(leftNormal.multiply(lateralOffset));
        Vec2d toPoint = toCenter.add(leftNormal.multiply(lateralOffset));
        List<Vec2d> span = RoadVoxelRasterizer.sampleSpanPoints(fromPoint, toPoint);
        int last = span.size() - 1;
        for (int i = 0; i < span.size(); i++) {
            Vec2d planPoint = host.canonicalColumnPoint(span.get(i));
            int surfaceY = last == 0
                ? fromY
                : (int) Math.round(fromY + (toY - fromY) * ((double) i / last));
            BlockPos pos = host.canvasToBlockPos(planPoint).withY(surfaceY);
            String materialId = MaterialMixResolver.resolve(
                materialMix, pos, seedKey, host::resolveBlockId);
            writeSurfaceAndClearance(plan, planPoint, surfaceY, layer, materialId, host,
                terrain, constructionType, tunnelThreshold);
        }
    }

    private static void writeLateralSpan(
            RoadCorridorPlan plan,
            Vec2d fromCenter,
            Vec2d toCenter,
            Vec2d leftNormal,
            int fromY,
            int toY,
            double scale,
            double lateralOffset,
            RoadSolidLayer layer,
            String materialId,
            PlanHost host,
            TerrainSampler terrain,
            RoadConstructionType constructionType,
            int tunnelThreshold) {
        Vec2d fromPoint = fromCenter.add(leftNormal.multiply(lateralOffset));
        Vec2d toPoint = toCenter.add(leftNormal.multiply(lateralOffset));
        List<Vec2d> span = RoadVoxelRasterizer.sampleSpanPoints(fromPoint, toPoint);
        int last = span.size() - 1;
        for (int i = 0; i < span.size(); i++) {
            Vec2d planPoint = host.canonicalColumnPoint(span.get(i));
            int surfaceY = last == 0
                ? fromY
                : (int) Math.round(fromY + (toY - fromY) * ((double) i / last));
            writeSurfaceAndClearance(plan, planPoint, surfaceY, layer, materialId, host,
                terrain, constructionType, tunnelThreshold);
        }
    }

    static void writeSurfaceAndClearance(
            RoadCorridorPlan plan,
            Vec2d planPoint,
            int surfaceY,
            RoadSolidLayer layer,
            String materialId,
            PlanHost host,
            TerrainSampler terrain,
            RoadConstructionType constructionType,
            int tunnelThreshold) {
        planPoint = host.canonicalColumnPoint(planPoint);
        BlockPos worldPos = host.canvasToBlockPos(planPoint);
        int worldX = worldPos.getX();
        int worldZ = worldPos.getZ();
        if (!terrain.isChunkLoaded(worldX, worldZ)) {
            return;
        }
        ColumnKey key = new ColumnKey(worldX, worldZ);
        plan.surfaceMask().put(key, new SurfaceCell(planPoint, surfaceY, layer, materialId));

        RoadCorridorConstructionMode mode = toMode(constructionType);
        int clearTopY = computeClearTopY(mode, surfaceY, planPoint, terrain, tunnelThreshold, host);
        plan.putClearance(key, new ClearanceCell(planPoint, surfaceY, clearTopY, mode, constructionType));
    }

    private static List<SlopeProfileSample> mergeSlopeWedge(
            RoadCorridorPlan plan,
            StationSample sample,
            ResolvedCrossSection crossSection,
            TerrainSampler terrain,
            double unitsPerBlock,
            PlanHost host,
            long[] stationSampleSeq,
            int tunnelThreshold) {
        return mergeSlopeAtCenter(plan, sample.center(), sample.leftNormal(), sample.targetY(),
            crossSection, terrain, unitsPerBlock, host, stationSampleSeq[0]++, tunnelThreshold);
    }

    /** 急折外侧扇区：仅当转角够大且路径采样偏稀时加密；上限避免预览算力爆炸。 */
    private static final double SLOPE_FAN_MIN_TURN_RADIANS = Math.toRadians(25.0);
    private static final double SLOPE_FAN_GAP_BLOCKS = 1.5;
    private static final int SLOPE_FAN_MAX_STEPS = 16;

    /**
     * 沿 span 采样边坡射线。急折且路径短时角向加密，填外侧三角空隙；步数封顶保预览性能。
     */
    private static List<SlopeProfileSample> mergeSlopeSpan(
            RoadCorridorPlan plan,
            StationSample from,
            StationSample to,
            ResolvedCrossSection crossSection,
            TerrainSampler terrain,
            double unitsPerBlock,
            PlanHost host,
            long[] stationSampleSeq,
            int tunnelThreshold) {
        List<SlopeProfileSample> samples = new ArrayList<>();
        List<Vec2d> span = RoadVoxelRasterizer.sampleSpanPoints(from.center(), to.center());
        int pathLast = Math.max(0, span.size() - 1);
        Vec2d fromNormal = normalize(from.leftNormal());
        Vec2d toNormal = normalize(to.leftNormal());
        double turn = signedAngle(fromNormal, toNormal);
        int steps = pathLast;
        if (Math.abs(turn) >= SLOPE_FAN_MIN_TURN_RADIANS) {
            int maxRun = RoadCorridorWidth.MAX_SLOPE_BATTER_RUN_BLOCKS;
            double maxGapRadians = SLOPE_FAN_GAP_BLOCKS / Math.max(1, maxRun);
            int angularSteps = Math.max(1, (int) Math.ceil(Math.abs(turn) / maxGapRadians));
            if (angularSteps > pathLast) {
                steps = Math.max(pathLast, Math.min(angularSteps, SLOPE_FAN_MAX_STEPS));
            }
        }
        if (steps <= 0) {
            RoadConstructionType stepType = resolveStepConstructionType(from, to, 1.0);
            if (RoadEarthworkPolicy.allowsCorridorBatter(stepType)) {
                samples.addAll(mergeSlopeAtCenter(plan, to.center(), toNormal, to.targetY(),
                    crossSection, terrain, unitsPerBlock, host, stationSampleSeq[0]++,
                    tunnelThreshold));
            }
            return samples;
        }
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vec2d center = centerAlongSpan(span, pathLast, from.center(), to.center(), t);
            int targetY = (int) Math.round(from.targetY() + (to.targetY() - from.targetY()) * t);
            Vec2d leftNormal = Math.abs(turn) <= 1e-6
                ? toNormal
                : normalize(fromNormal.rotate(turn * t));
            RoadConstructionType stepType = resolveStepConstructionType(from, to, t);
            if (!RoadEarthworkPolicy.allowsCorridorBatter(stepType)) {
                continue;
            }
            samples.addAll(mergeSlopeAtCenter(plan, center, leftNormal, targetY,
                crossSection, terrain, unitsPerBlock, host, stationSampleSeq[0]++,
                tunnelThreshold));
        }
        return samples;
    }

    private static Vec2d centerAlongSpan(
            List<Vec2d> span,
            int pathLast,
            Vec2d from,
            Vec2d to,
            double t) {
        if (span == null || span.isEmpty() || pathLast <= 0) {
            return from.lerp(to, t);
        }
        double exact = t * pathLast;
        int index = (int) Math.floor(exact);
        if (index >= pathLast) {
            return span.get(pathLast);
        }
        double localT = exact - index;
        return span.get(index).lerp(span.get(index + 1), localT);
    }

    /** 从 {@code from} 转到 {@code to} 的有向夹角，范围 (-π, π]。 */
    private static double signedAngle(Vec2d from, Vec2d to) {
        Vec2d a = normalize(from);
        Vec2d b = normalize(to);
        if (a.lengthSquared() < 1e-12 || b.lengthSquared() < 1e-12) {
            return 0.0;
        }
        return Math.atan2(a.cross(b), a.dot(b));
    }

    private static RoadConstructionType resolveStepConstructionType(
            StationSample from,
            StationSample to,
            double t) {
        RoadConstructionType fromType = from.constructionType();
        RoadConstructionType toType = to.constructionType();
        if (fromType == toType) {
            return fromType;
        }
        return t <= 0.5 ? fromType : toType;
    }

    private static List<SlopeProfileSample> mergeSlopeAtCenter(
            RoadCorridorPlan plan,
            Vec2d center,
            Vec2d leftNormal,
            int targetY,
            ResolvedCrossSection crossSection,
            TerrainSampler terrain,
            double unitsPerBlock,
            PlanHost host,
            long stationSampleId,
            int tunnelThreshold) {
        int maxHorizontalRun = RoadCorridorWidth.MAX_SLOPE_BATTER_RUN_BLOCKS;
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        Vec2d normal = normalize(leftNormal);
        String fillBlockId = host.resolveBlockId(crossSection.fillSlopeMaterial);
        String cutBlockId = crossSection.cutSlopeMaterial == null || crossSection.cutSlopeMaterial.isBlank()
            ? null
            : host.resolveBlockId(crossSection.cutSlopeMaterial);
        float fillRatio = crossSection.fillSlopeRatio;
        float cutRatio = crossSection.cutSlopeRatio;

        List<SlopeProfileSample> samples = new ArrayList<>();
        samples.addAll(mergeSlopeAtShoulder(plan, center, targetY, crossSection.maxSurfaceLateral(), terrain,
            fillRatio, cutRatio, fillBlockId, cutBlockId, maxHorizontalRun,
            normal, normal, scale, host, new SlopeRayId(stationSampleId, SlopeRayId.ShoulderSide.LEFT),
            tunnelThreshold));
        samples.addAll(mergeSlopeAtShoulder(plan, center, targetY, crossSection.minSurfaceLateral(), terrain,
            fillRatio, cutRatio, fillBlockId, cutBlockId, maxHorizontalRun,
            normal, normal.multiply(-1), scale, host, new SlopeRayId(stationSampleId, SlopeRayId.ShoulderSide.RIGHT),
            tunnelThreshold));
        return samples;
    }

    private static List<SlopeProfileSample> mergeSlopeAtShoulder(
            RoadCorridorPlan plan,
            Vec2d center,
            int targetY,
            int signedOuterLateral,
            TerrainSampler terrain,
            float fillRatio,
            float cutRatio,
            String fillBlockId,
            String cutBlockId,
            int maxHorizontalRun,
            Vec2d corridorLeftNormal,
            Vec2d outwardNormal,
            double unitsPerBlock,
            PlanHost host,
            SlopeRayId rayId,
            int tunnelThreshold) {
        List<SlopeProfileSample> samples = new ArrayList<>();
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        Vec2d leftNormal = normalize(corridorLeftNormal);
        Vec2d normal = normalize(outwardNormal);
        Vec2d outerEdge = center.add(leftNormal.multiply(signedOuterLateral * scale));
        if (!terrain.isChunkLoaded(outerEdge)) {
            return samples;
        }
        int groundAtEdge = terrain.sampleSurfaceY(outerEdge);
        if (targetY == groundAtEdge) {
            return samples;
        }
        boolean isFill = targetY > groundAtEdge;
        // 挂壁/半隧靠山侧：外缘覆土已达隧道阈值，不开敞削坡
        if (RoadEarthworkPolicy.suppressShoulderCutBatter(
                isFill, groundAtEdge - targetY, tunnelThreshold)) {
            return samples;
        }
        int profileDirection = isFill ? -1 : 1;
        float slopeRatio = isFill ? fillRatio : cutRatio;
        int heightDifference = Math.abs(targetY - groundAtEdge);
        int unconstrainedRun = (int) Math.ceil(heightDifference * Math.max(0.5f, slopeRatio)) + 2;
        boolean widthCapped = unconstrainedRun > maxHorizontalRun;
        if (widthCapped) {
            plan.noteSlopeBatterWidthCap();
        }
        int usefulHorizontalRun = Math.clamp(unconstrainedRun, 2, maxHorizontalRun);

        List<int[]> profile = RoadSlopeUtils.computeSlopeProfile(
            targetY,
            profileDirection,
            horizontalOffset -> {
                Vec2d samplePoint = outerEdge.add(normal.multiply(horizontalOffset * scale));
                if (!terrain.isChunkLoaded(samplePoint)) {
                    return targetY;
                }
                return terrain.sampleSurfaceY(samplePoint);
            },
            slopeRatio,
            usefulHorizontalRun);

        if (profile.size() <= 1) {
            plan.noteUnresolvedSlopeConstruction();
            return samples;
        }

        boolean meetsTerrain = slopeProfileMeetsTerrain(profile, isFill, outerEdge, normal, scale, terrain);
        SlopeConstructionOutcome outcome = SlopeConstructionOutcome.classify(meetsTerrain, widthCapped);
        String materialId = isFill ? fillBlockId : cutBlockId;

        Vec2d previousPoint = null;
        Integer previousHeight = null;
        Integer previousOffset = null;
        for (int step = 0; step < profile.size(); step++) {
            int[] point = profile.get(step);
            int horizontalOffset = point[0];
            int slopeHeight = point[1];
            Vec2d samplePoint = outerEdge.add(normal.multiply(horizontalOffset * scale));
            if (previousPoint != null) {
                List<Vec2d> span = RoadVoxelRasterizer.sampleSpanPoints(previousPoint, samplePoint);
                int last = span.size() - 1;
                for (int i = 0; i < span.size(); i++) {
                    double t = last <= 0 ? 1.0 : (double) i / last;
                    int y = (int) Math.round(previousHeight + (slopeHeight - previousHeight) * t);
                    int offset = (int) Math.round(previousOffset + (horizontalOffset - previousOffset) * t);
                    addSlopeSample(
                        samples, span.get(i), y, isFill, materialId, rayId, offset, slopeRatio, outcome,
                        terrain, host);
                }
            } else {
                addSlopeSample(
                    samples, samplePoint, slopeHeight, isFill, materialId, rayId,
                    horizontalOffset, slopeRatio, outcome, terrain, host);
            }
            previousPoint = samplePoint;
            previousHeight = slopeHeight;
            previousOffset = horizontalOffset;
        }
        return samples;
    }

    private static void addSlopeSample(
            List<SlopeProfileSample> samples,
            Vec2d samplePoint,
            int slopeHeight,
            boolean isFill,
            String materialId,
            SlopeRayId rayId,
            int horizontalOffset,
            float slopeRatio,
            SlopeConstructionOutcome outcome,
            TerrainSampler terrain,
            PlanHost host) {
        if (samplePoint == null || !terrain.isChunkLoaded(samplePoint)) {
            return;
        }
        Vec2d planPoint = host.canonicalColumnPoint(samplePoint);
        ColumnKey key = new ColumnKey(host.worldX(planPoint), host.worldZ(planPoint));
        samples.add(new SlopeProfileSample(
            key, planPoint, slopeHeight, isFill, materialId, rayId,
            horizontalOffset, slopeRatio, outcome));
    }

    private static boolean slopeProfileMeetsTerrain(
            List<int[]> profile,
            boolean isFill,
            Vec2d outerEdge,
            Vec2d outwardNormal,
            double scale,
            TerrainSampler terrain) {
        if (profile == null || profile.size() <= 1) {
            return true;
        }
        int[] last = profile.getLast();
        int horizontalOffset = last[0];
        int slopeHeight = last[1];
        Vec2d samplePoint = outerEdge.add(outwardNormal.multiply(horizontalOffset * scale));
        if (!terrain.isChunkLoaded(samplePoint)) {
            return false;
        }
        int groundHeight = terrain.sampleSurfaceY(samplePoint);
        if (isFill) {
            return slopeHeight <= groundHeight + 1;
        }
        return slopeHeight >= groundHeight - 1;
    }

    private static RoadCorridorConstructionMode toMode(RoadConstructionType type) {
        if (type != null && type.isRockShelter()) return RoadCorridorConstructionMode.SHELTER;
        if (type == RoadConstructionType.TUNNEL_PORTAL) {
            return RoadCorridorConstructionMode.PORTAL;
        }
        if (type != null && type.isBridgeFamily()) {
            return RoadCorridorConstructionMode.BRIDGE;
        }
        if (type == RoadConstructionType.TUNNEL) {
            return RoadCorridorConstructionMode.TUNNEL;
        }
        return RoadCorridorConstructionMode.OPEN;
    }

    private static int computeClearTopY(
            RoadCorridorConstructionMode mode,
            int designSurfaceY,
            Vec2d planPoint,
            TerrainSampler terrain,
            int tunnelThreshold,
            PlanHost host) {
        return switch (mode) {
            case BRIDGE -> Math.max(
                designSurfaceY + host.tunnelStyle().clearHeight(),
                terrain.sampleColumnTopY(planPoint));
            case TUNNEL -> tunnelInteriorRoofTopY(host, designSurfaceY);
            case PORTAL -> portalRoofTopY(host, designSurfaceY);
            case SHELTER -> designSurfaceY + host.tunnelStyle().clearHeight();
            case OPEN -> Math.max(terrain.sampleColumnTopY(planPoint),
                designSurfaceY + host.tunnelStyle().clearHeight());
        };
    }

    static int portalRoofTopY(PlanHost host, int designSurfaceY) {
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(host.config());
        int envelopeWidth = RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection);
        return TunnelPortalExcavationMask.forPortal(
            host.tunnelStyle(), envelopeWidth, designSurfaceY).roofTopY();
    }

    static int tunnelInteriorRoofTopY(PlanHost host, int designSurfaceY) {
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(host.config());
        int envelopeWidth = RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection);
        ResolvedTunnelStyle tunnelStyle = host.tunnelStyle();
        return TunnelCrossSectionMask.build(
            tunnelStyle.shape(),
            envelopeWidth,
            tunnelStyle.sideClearance(),
            tunnelStyle.clearHeight(),
            tunnelStyle.liningThickness(),
            designSurfaceY,
            false).maxY();
    }

    private static Vec2d normalize(Vec2d vector) {
        return vector != null && vector.lengthSquared() > 1e-12
            ? vector.normalize()
            : new Vec2d(0, 1);
    }

    public interface PlanHost {
        RoadSystemConfig config();

        String resolveBlockId(String material);

        int snapEndpointElevation(Vec2d center, int targetY);

        BlockPos canvasToBlockPos(Vec2d canvasPos);

        /** Exact world-column center in canvas space; ownership uses the same point as placement. */
        default Vec2d canonicalColumnPoint(Vec2d point) { return point; }

        int worldX(Vec2d planPoint);

        int worldZ(Vec2d planPoint);

        int tunnelThreshold();

        int bridgeThreshold();

        ResolvedTunnelStyle tunnelStyle();

        static PlanHost from(
                RoadGenerationPipelineContext.Host host,
                RoadTerrainStyle terrainStyle,
                ResolvedTunnelStyle tunnelStyle) {
            RoadTerrainClearanceUtils.BlockColumnResolver resolver =
                new RoadTerrainClearanceUtils.BlockColumnResolver() {
                    @Override
                    public int worldX(Vec2d planPoint) {
                        return host.canvasToBlockPos(planPoint).getX();
                    }

                    @Override
                    public int worldZ(Vec2d planPoint) {
                        return host.canvasToBlockPos(planPoint).getZ();
                    }
                };
            int tunnelThreshold = terrainStyle != null
                ? RoadConstructionHeuristics.tunnelThreshold(terrainStyle)
                : RoadConstructionHeuristics.tunnelThreshold(host.config());
            var coordinates = host.coordinateTransformer();
            var projection = coordinates != null ? coordinates.captureProjection() : null;
            return new PlanHost() {
                @Override
                public Vec2d canonicalColumnPoint(Vec2d point) {
                    if (projection == null || !projection.isValid()) return point;
                    var column = host.canvasToBlockPos(point);
                    return projection.toCanvas(new Vec2d(column.getX(), column.getZ()));
                }

                @Override
                public RoadSystemConfig config() {
                    return host.config();
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
                public BlockPos canvasToBlockPos(Vec2d canvasPos) {
                    return host.canvasToBlockPos(canvasPos);
                }

                @Override
                public int worldX(Vec2d planPoint) {
                    return resolver.worldX(planPoint);
                }

                @Override
                public int worldZ(Vec2d planPoint) {
                    return resolver.worldZ(planPoint);
                }

                @Override
                public int tunnelThreshold() {
                    return tunnelThreshold;
                }

                @Override
                public int bridgeThreshold() {
                    return host.bridgeThreshold();
                }

                @Override
                public ResolvedTunnelStyle tunnelStyle() {
                    return tunnelStyle != null ? tunnelStyle : ResolvedTunnelStyle.defaults();
                }
            };
        }
    }
}
