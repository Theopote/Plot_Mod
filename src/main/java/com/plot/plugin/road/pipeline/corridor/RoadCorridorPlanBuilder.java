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
import com.plot.plugin.road.pipeline.construction.WaterCrossingConstructionResolver;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.pipeline.profile.environment.WaterCrossing;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadVoxelRasterizer;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
            RoadConstructionType constructionType,
            boolean allowWaterEmbankment,
            boolean localDaylightAuthorized) {
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
            planHost,
            ctx.request().profileWaterCrossings(),
            ctx.detection() != null ? ctx.detection().localDaylightSegments() : Set.of());
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
        return build(
            segments, heightInfos, crossSections, constructionTypes, designElevation, buildProfile,
            terrain, unitsPerBlock, carriagewaySeedKey, junctionCorridor, host, List.of());
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
            PlanHost host,
            List<WaterCrossing> profileWaterCrossings) {
        return build(
            segments, heightInfos, crossSections, constructionTypes,
            designElevation, buildProfile, terrain, unitsPerBlock, carriagewaySeedKey,
            junctionCorridor, host, profileWaterCrossings, Set.of());
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
            PlanHost host,
            List<WaterCrossing> profileWaterCrossings,
            Set<Integer> localDaylightSegments) {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        List<StationSample> stations = collectStations(
            segments, heightInfos, crossSections, constructionTypes,
            designElevation, buildProfile, host::snapEndpointElevation, unitsPerBlock,
            junctionCorridor != null
                    && junctionCorridor.exclusionPolygons().isEmpty()
                    && !junctionCorridor.hasColumnMask()
                ? junctionCorridor.endpointTrim() : null,
            profileWaterCrossings,
            localDaylightSegments != null ? localDaylightSegments : Set.of());
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
            ResolvedTunnelStyle tunnelStyle = host.tunnelStyle();
            boolean suppressBatter = shouldSuppressCorridorBatter(
                stations, i, terrain, tunnelStyle, tunnelThreshold);
            rasterizeStation(plan, sample, crossSection, terrain, unitsPerBlock,
                carriagewaySeedKey, host, config, tunnelThreshold, slopeSamples, stationSampleSeq,
                suppressBatter, segments, stations, i);
            if (i > 0) {
                // 邻接洞门/洞身/深覆土的路基站及其 span 都不放坡，避免边坡贴到洞口掏山
                boolean suppressSpanBatter = suppressBatter
                    || shouldSuppressCorridorBatter(stations, i - 1, terrain, tunnelStyle, tunnelThreshold);
                rasterizeSpan(plan, stations.get(i - 1), sample, crossSection, terrain,
                    unitsPerBlock, carriagewaySeedKey, host, config, tunnelThreshold, slopeSamples,
                    stationSampleSeq, suppressSpanBatter, segments, profileWaterCrossings);
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
            com.plot.plugin.road.pipeline.facility.StationFacilityJunctionTrim.FacilityEndpointTrim endpointTrim,
            List<WaterCrossing> profileWaterCrossings,
            Set<Integer> localDaylightSegments) {
        Set<Integer> daylight = localDaylightSegments != null ? localDaylightSegments : Set.of();
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
                    RoadConstructionClassifier.constructionTypeAt(constructionTypes, segmentIndex),
                    WaterCrossingConstructionResolver.isCausewayFillStation(
                        profileWaterCrossings, chainage),
                    daylight.contains(segmentIndex))));
        return stations;
    }

    /** 邻接窗口：锐弯处边坡扇可能跨过一站，故检查 ±2 站内是否有隧道族/棚洞。 */
    private static final int TUNNEL_BATTER_SUPPRESS_STATION_RADIUS = 2;

    private static boolean shouldSuppressCorridorBatter(
            List<StationSample> stations,
            int index,
            TerrainSampler terrain,
            ResolvedTunnelStyle tunnelStyle,
            int tunnelThreshold) {
        if (stations == null || index < 0 || index >= stations.size()) {
            return false;
        }
        StationSample sample = stations.get(index);
        // Approved local daylight CUT may carve deep one-sided outcrops.
        if (sample.localDaylightAuthorized()) {
            return false;
        }
        boolean nearTunnel = adjacentTunnelOrShelter(stations, index);
        int cover = 0;
        if (terrain != null && sample.center() != null && terrain.isChunkLoaded(sample.center())) {
            cover = terrain.sampleSurfaceY(sample.center()) - sample.targetY();
        }
        return RoadEarthworkPolicy.suppressOpenDaylightBatter(
            sample.constructionType(), nearTunnel && cover > 0, cover, tunnelStyle, tunnelThreshold);
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

    /**
     * Unit tangent pointing toward increasing {@link StationSample#chainage()}.
     * Independent of {@code samplingOriented().forward()} / leftNormal flip.
     */
    static Vec2d stationIncreasingForward(List<StationSample> stations, int index) {
        if (stations == null || index < 0 || index >= stations.size()) {
            return new Vec2d(1, 0);
        }
        StationSample sample = stations.get(index);
        StationSample higher = null;
        StationSample lower = null;
        for (int i = 0; i < stations.size(); i++) {
            if (i == index) {
                continue;
            }
            StationSample other = stations.get(i);
            if (other.chainage() > sample.chainage() + 1e-9) {
                if (higher == null || other.chainage() < higher.chainage()) {
                    higher = other;
                }
            } else if (other.chainage() < sample.chainage() - 1e-9) {
                if (lower == null || other.chainage() > lower.chainage()) {
                    lower = other;
                }
            }
        }
        if (higher != null) {
            Vec2d delta = higher.center().subtract(sample.center());
            if (delta.lengthSquared() > 1e-12) {
                return delta.normalize();
            }
        }
        if (lower != null) {
            Vec2d delta = sample.center().subtract(lower.center());
            if (delta.lengthSquared() > 1e-12) {
                return delta.normalize();
            }
        }
        Vec2d leftNormal = normalize(sample.leftNormal());
        return new Vec2d(leftNormal.y, -leftNormal.x);
    }

    /**
     * +1 along {@link #stationIncreasingForward} is into the hill / tunnel; -1 is the open approach.
     * Compares tunnel-family density by chainage, not list index (reverse sampling flips normals
     * but keeps geometric list order).
     */
    static int inferPortalIntoHillSign(List<StationSample> stations, int index) {
        if (stations == null || index < 0 || index >= stations.size()) {
            return 0;
        }
        double chainage = stations.get(index).chainage();
        int tunnelPlus = 0;
        int tunnelMinus = 0;
        for (StationSample other : stations) {
            RoadConstructionType type = other.constructionType();
            if (type == null || !type.isTunnelFamily()) {
                continue;
            }
            double delta = other.chainage() - chainage;
            if (delta > 1e-9 && delta <= 3.0 + 1e-9) {
                tunnelPlus++;
            } else if (delta < -1e-9 && delta >= -3.0 - 1e-9) {
                tunnelMinus++;
            }
        }
        return Integer.compare(tunnelPlus, tunnelMinus);
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
            boolean suppressBatter,
            List<PathSegment> polylineSegments,
            List<StationSample> stations,
            int stationIndex) {
        if (sample.constructionType() == RoadConstructionType.TUNNEL_PORTAL) {
            Vec2d leftNormal = normalize(sample.leftNormal());
            Vec2d forward = stationIncreasingForward(stations, stationIndex);
            plan.registerPortalBackfillSite(new PortalBackfillSite(
                sample.center(),
                leftNormal,
                forward,
                sample.targetY(),
                RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection),
                unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0,
                inferPortalIntoHillSign(stations, stationIndex)));
        }
        plan.setRoadbedWaterMarkingActive(sample.allowWaterEmbankment());
        plan.setLocalDaylightMarkingActive(sample.localDaylightAuthorized());
        try {
            rasterizeSurfaceBands(plan, sample.center(), sample.center(), sample.leftNormal(), sample.leftNormal(),
                sample.targetY(), sample.targetY(), crossSection, terrain, unitsPerBlock,
                seedKey, host, sample.constructionType(), config, tunnelThreshold, polylineSegments);
            if (!suppressBatter
                    && crossSection.includeSlopeBatter
                    && RoadEarthworkPolicy.allowsCorridorBatter(sample.constructionType())) {
                slopeSamples.addAll(mergeSlopeWedge(
                    plan, sample, crossSection, terrain, unitsPerBlock, host, stationSampleSeq,
                    tunnelThreshold));
            }
        } finally {
            plan.setLocalDaylightMarkingActive(false);
            plan.setRoadbedWaterMarkingActive(false);
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
            boolean suppressSpanBatter,
            List<PathSegment> polylineSegments,
            List<WaterCrossing> profileWaterCrossings) {
        ResolvedCrossSection toSection = crossSection;
        RoadConstructionType fromType = from.constructionType();
        RoadConstructionType toType = to.constructionType();
        // Split type-boundary / 堤道边界 / 局部明挖授权边界 spans，按真实桩号切分而非固定中点。
        // Daylight auth must NOT OR across a CUT↔CUT span — matches resolveStepLocalDaylight.
        boolean waterAuthDiffers = from.allowWaterEmbankment() != to.allowWaterEmbankment();
        boolean daylightAuthDiffers = from.localDaylightAuthorized() != to.localDaylightAuthorized();
        if (fromType == toType && !waterAuthDiffers && !daylightAuthDiffers) {
            plan.setRoadbedWaterMarkingActive(from.allowWaterEmbankment());
            plan.setLocalDaylightMarkingActive(from.localDaylightAuthorized());
            try {
                rasterizeSurfaceBands(plan, from.center(), to.center(), from.leftNormal(), to.leftNormal(),
                    from.targetY(), to.targetY(), toSection, terrain, unitsPerBlock,
                    seedKey, host, toType, config, tunnelThreshold, polylineSegments);
            } finally {
                plan.setLocalDaylightMarkingActive(false);
                plan.setRoadbedWaterMarkingActive(false);
            }
        } else {
            double splitT = waterAuthDiffers
                ? causewayBoundarySplitT(
                    from.chainage(),
                    to.chainage(),
                    from.allowWaterEmbankment(),
                    profileWaterCrossings)
                : 0.5;
            Vec2d midCenter = from.center().lerp(to.center(), splitT);
            int midY = (int) Math.round(from.targetY() + (to.targetY() - from.targetY()) * splitT);
            Vec2d midNormal = normalize(from.leftNormal().lerp(to.leftNormal(), splitT));
            plan.setRoadbedWaterMarkingActive(from.allowWaterEmbankment());
            plan.setLocalDaylightMarkingActive(from.localDaylightAuthorized());
            try {
                rasterizeSurfaceBands(plan, from.center(), midCenter, from.leftNormal(), midNormal,
                    from.targetY(), midY, toSection, terrain, unitsPerBlock,
                    seedKey, host, fromType, config, tunnelThreshold, polylineSegments);
            } finally {
                plan.setLocalDaylightMarkingActive(false);
                plan.setRoadbedWaterMarkingActive(false);
            }
            plan.setRoadbedWaterMarkingActive(to.allowWaterEmbankment());
            plan.setLocalDaylightMarkingActive(to.localDaylightAuthorized());
            try {
                rasterizeSurfaceBands(plan, midCenter, to.center(), midNormal, to.leftNormal(),
                    midY, to.targetY(), toSection, terrain, unitsPerBlock,
                    seedKey, host, toType, config, tunnelThreshold, polylineSegments);
            } finally {
                plan.setLocalDaylightMarkingActive(false);
                plan.setRoadbedWaterMarkingActive(false);
            }
        }
        // 两端都允许且不贴隧道/深覆土才放坡：避免洞口路基 span 把边坡伸进山体。
        // 边坡水域授权随 SlopeProfileSample 传递，在 consolidator 胜出列后落盘。
        if (!suppressSpanBatter
                && toSection.includeSlopeBatter
                && RoadEarthworkPolicy.allowsCorridorBatter(fromType)
                && RoadEarthworkPolicy.allowsCorridorBatter(toType)) {
            slopeSamples.addAll(mergeSlopeSpan(
                plan, from, to, toSection, terrain, unitsPerBlock, host, stationSampleSeq,
                tunnelThreshold, profileWaterCrossings));
        }
    }

    /**
     * 在 from→to 桩号区间内按真实堤道边界定位授权切换点；找不到时回退 0.5。
     * 包可见供单元测试校验，避免固定中点近似。
     */
    static double causewayBoundarySplitT(
            double startStation,
            double endStation,
            boolean fromAllow,
            List<WaterCrossing> crossings) {
        if (Math.abs(endStation - startStation) < 1e-9) {
            return 0.5;
        }
        for (int i = 1; i <= 64; i++) {
            double t = i / 64.0;
            double station = startStation + (endStation - startStation) * t;
            boolean allow = WaterCrossingConstructionResolver.isCausewayFillStation(crossings, station);
            if (allow != fromAllow) {
                return t;
            }
        }
        return 0.5;
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
            int tunnelThreshold,
            List<PathSegment> polylineSegments) {
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        Vec2d startNormal = normalize(fromNormal);
        Vec2d endNormal = normalize(toNormal);

        CrossSectionSpanRasterizer.rasterizeSpan(
            plan, fromCenter, toCenter, startNormal, endNormal, fromY, toY, scale, crossSection,
            seedKey, host, terrain, constructionType, tunnelThreshold, polylineSegments);
        expandClearanceEnvelope(plan, fromCenter, toCenter, startNormal, endNormal, fromY, toY, scale,
            crossSection, terrain, constructionType, config, tunnelThreshold, host);
    }

    private static void expandClearanceEnvelope(
            RoadCorridorPlan plan,
            Vec2d fromCenter,
            Vec2d toCenter,
            Vec2d fromNormal,
            Vec2d toNormal,
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
        int gradingEnvelope = RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection);
        Integer envelopeTag = constructionType != null && constructionType.isTunnelFamily()
            ? gradingEnvelope
            : null;
        int minOffset = RoadDimensionUtils.minLateralOffset(envelopeWidth);
        int maxOffset = RoadDimensionUtils.maxLateralOffset(envelopeWidth);
        for (int lateral = minOffset; lateral <= maxOffset; lateral++) {
            double offset = lateral * scale;
            writeClearanceSpan(plan, fromCenter, toCenter, fromNormal, toNormal, offset, fromY, toY,
                terrain, constructionType, tunnelThreshold, host, lateral, envelopeTag);
        }
    }

    private static void writeClearanceSpan(
            RoadCorridorPlan plan,
            Vec2d fromCenter,
            Vec2d toCenter,
            Vec2d fromNormal,
            Vec2d toNormal,
            double lateralOffset,
            int fromY,
            int toY,
            TerrainSampler terrain,
            RoadConstructionType constructionType,
            int tunnelThreshold,
            PlanHost host,
            int corridorLateral,
            Integer envelopeWidthBlocks) {
        Vec2d startNormal = normalize(fromNormal);
        Vec2d endNormal = normalize(toNormal);
        double turn = signedAngle(startNormal, endNormal);
        boolean acute = Math.abs(turn) >= Math.PI * 0.5;
        Vec2d chordNormal = chordLeftNormal(fromCenter, toCenter, startNormal, endNormal);
        List<Vec2d> centers = RoadVoxelRasterizer.sampleSpanPoints(fromCenter, toCenter);
        int last = centers.size() - 1;
        for (int i = 0; i < centers.size(); i++) {
            double t = last == 0 ? 0.0 : (double) i / last;
            Vec2d center = centers.get(i);
            Vec2d normal;
            if (acute) {
                if (t <= 1e-9) {
                    normal = startNormal;
                } else if (t >= 1.0 - 1e-9) {
                    normal = endNormal;
                } else {
                    normal = chordNormal;
                }
            } else if (Math.abs(turn) <= 1e-9) {
                normal = endNormal;
            } else {
                normal = normalize(startNormal.rotate(turn * t));
            }
            Vec2d planPoint = host.canonicalColumnPoint(center.add(normal.multiply(lateralOffset)));
            int surfaceY = last == 0
                ? fromY
                : (int) Math.round(fromY + (toY - fromY) * t);
            int worldX = host.worldX(planPoint);
            int worldZ = host.worldZ(planPoint);
            if (!terrain.isChunkLoaded(worldX, worldZ)) {
                continue;
            }
            ColumnKey key = new ColumnKey(worldX, worldZ);
            RoadCorridorConstructionMode mode = toMode(constructionType);
            int clearTopY = computeClearTopY(
                plan, mode, surfaceY, planPoint, terrain, tunnelThreshold, host, envelopeWidthBlocks);
            Integer lateralTag = constructionType != null && constructionType.isTunnelFamily()
                ? corridorLateral
                : null;
            plan.putClearance(key, new ClearanceCell(
                planPoint, surfaceY, clearTopY, mode, constructionType, lateralTag, envelopeWidthBlocks));
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
        writeSurfaceAndClearance(
            plan, planPoint, surfaceY, layer, materialId, host, terrain, constructionType,
            tunnelThreshold, null, null);
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
            int tunnelThreshold,
            Integer envelopeWidthBlocks,
            Integer corridorLateral) {
        planPoint = host.canonicalColumnPoint(planPoint);
        BlockPos worldPos = host.canvasToBlockPos(planPoint);
        int worldX = worldPos.getX();
        int worldZ = worldPos.getZ();
        if (!terrain.isChunkLoaded(worldX, worldZ)) {
            return;
        }
        ColumnKey key = new ColumnKey(worldX, worldZ);
        plan.surfaceMask().put(key, new SurfaceCell(planPoint, surfaceY, layer, materialId));
        SurfaceCell finalSurface = plan.surfaceMask().get(key);
        int designY = finalSurface != null ? finalSurface.surfaceY() : surfaceY;

        RoadCorridorConstructionMode mode = toMode(constructionType);
        Integer envelopeTag = envelopeWidthBlocks;
        Integer lateralTag = constructionType != null && constructionType.isTunnelFamily()
            ? corridorLateral
            : null;
        int clearTopY = computeClearTopY(
            plan, mode, designY, planPoint, terrain, tunnelThreshold, host, envelopeTag);
        plan.putClearance(key, new ClearanceCell(
            planPoint, designY, clearTopY, mode, constructionType, lateralTag, envelopeTag));
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
            crossSection, terrain, unitsPerBlock, host, stationSampleSeq[0]++, tunnelThreshold,
            sample.constructionType(), sample.allowWaterEmbankment(),
            sample.localDaylightAuthorized()).samples();
    }

    /** 单站点两侧肩部射线：样本 + 可供急弯环带使用的剖面快照。 */
    private record CenterSlopeBuild(
            List<SlopeProfileSample> samples,
            SlopeBandRasterizer.ShoulderRay leftRay,
            SlopeBandRasterizer.ShoulderRay rightRay) {
    }

    /** 单侧肩部构建结果。 */
    private record ShoulderSlopeBuild(
            List<SlopeProfileSample> samples,
            SlopeBandRasterizer.ShoulderRay bandRay) {
        static ShoulderSlopeBuild empty() {
            return new ShoulderSlopeBuild(List.of(), null);
        }
    }

    /** 急折外侧扇区：仅当转角够大且路径采样偏稀时加密；上限避免预览算力爆炸。 */
    private static final double SLOPE_FAN_MIN_TURN_RADIANS = Math.toRadians(20.0);
    private static final double SLOPE_FAN_GAP_BLOCKS = 1.25;
    private static final int SLOPE_FAN_MAX_STEPS = 24;

    /**
     * 沿 span 采样边坡射线。急折时角向加密射线算高，外侧相邻射线再以二维环带补齐三角空隙。
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
            int tunnelThreshold,
            List<WaterCrossing> profileWaterCrossings) {
        List<SlopeProfileSample> samples = new ArrayList<>();
        List<Vec2d> span = RoadVoxelRasterizer.sampleSpanPoints(from.center(), to.center());
        int pathLast = Math.max(0, span.size() - 1);
        Vec2d fromNormal = normalize(from.leftNormal());
        Vec2d toNormal = normalize(to.leftNormal());
        double turn = signedAngle(fromNormal, toNormal);
        boolean significantTurn = Math.abs(turn) >= SLOPE_FAN_MIN_TURN_RADIANS;
        // turn>0：左法向逆时针扫，外侧肩为 RIGHT；turn<0：外侧肩为 LEFT。
        SlopeRayId.ShoulderSide outerSide = turn > 0
            ? SlopeRayId.ShoulderSide.RIGHT
            : SlopeRayId.ShoulderSide.LEFT;
        int steps = pathLast;
        if (significantTurn) {
            int maxRun = RoadCorridorWidth.slopeBatterMaxRunBlocks(host.config());
            double maxGapRadians = SLOPE_FAN_GAP_BLOCKS / Math.max(1, maxRun);
            int angularSteps = Math.max(1, (int) Math.ceil(Math.abs(turn) / maxGapRadians));
            // Shared vertex (pathLast==0): angular fan still runs; early-return below is
            // only for non-significant turns with coincident centers.
            if (angularSteps > pathLast) {
                // 至少保留 path 采样数，至多 SLOPE_FAN_MAX_STEPS（勿对调 clamp 上下界）
                steps = Math.max(pathLast, Math.min(angularSteps, SLOPE_FAN_MAX_STEPS));
            }
        }
        // 连续有效外侧射线分组；禁坡/抑制站点打断，禁止跨水面或结构空隙连环带。
        List<List<SlopeBandRasterizer.ShoulderRay>> outerRayGroups = new ArrayList<>();
        List<SlopeBandRasterizer.ShoulderRay> currentOuterGroup = new ArrayList<>();
        if (steps <= 0) {
            RoadConstructionType stepType = resolveStepConstructionType(from, to, 1.0);
            boolean allowWater = resolveStepAllowWaterEmbankment(from, to, 1.0, profileWaterCrossings);
            if (RoadEarthworkPolicy.allowsCorridorBatter(stepType)) {
                CenterSlopeBuild built = mergeSlopeAtCenter(plan, to.center(), toNormal, to.targetY(),
                    crossSection, terrain, unitsPerBlock, host, stationSampleSeq[0]++,
                    tunnelThreshold, stepType, allowWater,
                    resolveStepLocalDaylight(from, to, 1.0));
                samples.addAll(built.samples());
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
            boolean allowWater = resolveStepAllowWaterEmbankment(from, to, t, profileWaterCrossings);
            boolean localDaylight = resolveStepLocalDaylight(from, to, t);
            if (!RoadEarthworkPolicy.allowsCorridorBatter(stepType)) {
                flushOuterRayGroup(outerRayGroups, currentOuterGroup);
                continue;
            }
            CenterSlopeBuild built = mergeSlopeAtCenter(plan, center, leftNormal, targetY,
                crossSection, terrain, unitsPerBlock, host, stationSampleSeq[0]++,
                tunnelThreshold, stepType, allowWater, localDaylight);
            samples.addAll(built.samples());
            if (!significantTurn) {
                continue;
            }
            SlopeBandRasterizer.ShoulderRay outer = outerSide == SlopeRayId.ShoulderSide.LEFT
                ? built.leftRay()
                : built.rightRay();
            if (outer == null) {
                // 水面禁填、未加载或高架抑制：中断环带连接。
                flushOuterRayGroup(outerRayGroups, currentOuterGroup);
                continue;
            }
            if (!currentOuterGroup.isEmpty()
                    && !SlopeBandRasterizer.canConnectBand(currentOuterGroup.getLast(), outer)) {
                flushOuterRayGroup(outerRayGroups, currentOuterGroup);
            }
            currentOuterGroup.add(outer);
        }
        flushOuterRayGroup(outerRayGroups, currentOuterGroup);
        if (significantTurn) {
            for (List<SlopeBandRasterizer.ShoulderRay> group : outerRayGroups) {
                for (int i = 0; i + 1 < group.size(); i++) {
                    samples.addAll(SlopeBandRasterizer.fillBetweenRays(
                        group.get(i), group.get(i + 1), terrain, host));
                }
            }
        }
        return samples;
    }

    private static void flushOuterRayGroup(
            List<List<SlopeBandRasterizer.ShoulderRay>> groups,
            List<SlopeBandRasterizer.ShoulderRay> current) {
        if (current == null || current.isEmpty()) {
            return;
        }
        groups.add(List.copyOf(current));
        current.clear();
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

    private static boolean resolveStepAllowWaterEmbankment(
            StationSample from,
            StationSample to,
            double t,
            List<WaterCrossing> crossings) {
        if (from.allowWaterEmbankment() == to.allowWaterEmbankment()) {
            return from.allowWaterEmbankment();
        }
        double station = from.chainage() + (to.chainage() - from.chainage()) * t;
        return WaterCrossingConstructionResolver.isCausewayFillStation(crossings, station);
    }

    /**
     * Local-daylight authorization follows the nearer endpoint — never OR across a
     * ROAD↔CUT boundary (would widen the deep-cover carve exception).
     */
    private static boolean resolveStepLocalDaylight(StationSample from, StationSample to, double t) {
        if (from.localDaylightAuthorized() == to.localDaylightAuthorized()) {
            return from.localDaylightAuthorized();
        }
        return t <= 0.5 ? from.localDaylightAuthorized() : to.localDaylightAuthorized();
    }

    private static CenterSlopeBuild mergeSlopeAtCenter(
            RoadCorridorPlan plan,
            Vec2d center,
            Vec2d leftNormal,
            int targetY,
            ResolvedCrossSection crossSection,
            TerrainSampler terrain,
            double unitsPerBlock,
            PlanHost host,
            long stationSampleId,
            int tunnelThreshold,
            RoadConstructionType constructionType,
            boolean allowWaterEmbankment,
            boolean localDaylightAuthorized) {
        int maxHorizontalRun = RoadCorridorWidth.slopeBatterMaxRunBlocks(host.config());
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        Vec2d normal = normalize(leftNormal);
        String fillBlockId = host.resolveBlockId(crossSection.fillSlopeMaterial);
        String cutBlockId = crossSection.cutSlopeMaterial == null || crossSection.cutSlopeMaterial.isBlank()
            ? null
            : host.resolveBlockId(crossSection.cutSlopeMaterial);
        float fillRatio = crossSection.fillSlopeRatio;
        float cutRatio = crossSection.cutSlopeRatio;

        ShoulderSlopeBuild left = mergeSlopeAtShoulder(plan, center, targetY, crossSection.maxSurfaceLateral(), terrain,
            fillRatio, cutRatio, fillBlockId, cutBlockId, maxHorizontalRun,
            normal, normal, scale, host, new SlopeRayId(stationSampleId, SlopeRayId.ShoulderSide.LEFT),
            tunnelThreshold, constructionType, allowWaterEmbankment, localDaylightAuthorized);
        ShoulderSlopeBuild right = mergeSlopeAtShoulder(plan, center, targetY, crossSection.minSurfaceLateral(), terrain,
            fillRatio, cutRatio, fillBlockId, cutBlockId, maxHorizontalRun,
            normal, normal.multiply(-1), scale, host, new SlopeRayId(stationSampleId, SlopeRayId.ShoulderSide.RIGHT),
            tunnelThreshold, constructionType, allowWaterEmbankment, localDaylightAuthorized);
        List<SlopeProfileSample> samples = new ArrayList<>(left.samples().size() + right.samples().size());
        samples.addAll(left.samples());
        samples.addAll(right.samples());
        return new CenterSlopeBuild(samples, left.bandRay(), right.bandRay());
    }

    private static ShoulderSlopeBuild mergeSlopeAtShoulder(
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
            int tunnelThreshold,
            RoadConstructionType constructionType,
            boolean allowWaterEmbankment,
            boolean localDaylightAuthorized) {
        List<SlopeProfileSample> samples = new ArrayList<>();
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        Vec2d leftNormal = normalize(corridorLeftNormal);
        Vec2d normal = normalize(outwardNormal);
        Vec2d outerEdge = center.add(leftNormal.multiply(signedOuterLateral * scale));
        if (!terrain.isChunkLoaded(outerEdge)) {
            return ShoulderSlopeBuild.empty();
        }
        int groundAtEdge = terrain.sampleSurfaceY(outerEdge);
        if (targetY == groundAtEdge) {
            return ShoulderSlopeBuild.empty();
        }
        boolean isFill = targetY > groundAtEdge;
        int heightAboveGround = Math.abs(targetY - groundAtEdge);
        // 挂壁/半隧靠山侧：外缘覆土已达结构高度+盖层，不开敞削坡
        // LocalDaylightCut 已批准的短段例外：允许削掉单侧矮土堆（显式参数，不依赖临时标记）。
        if (!localDaylightAuthorized
                && RoadEarthworkPolicy.suppressShoulderCutBatter(
                    isFill, groundAtEdge - targetY, host.tunnelStyle(), tunnelThreshold)) {
            return ShoulderSlopeBuild.empty();
        }
        // 高架填方：路底相对地面净高超过桥阈值则不堆土坡（交给桥梁/净空）
        if (RoadEarthworkPolicy.suppressFillBatter(isFill, heightAboveGround, host.bridgeThreshold())) {
            return ShoulderSlopeBuild.empty();
        }
        // 水面柱禁止普通填方；仅授权堤道（CAUSEWAY）例外，普通 FILL 不放行
        if (RoadEarthworkPolicy.suppressFillOverWater(
                isFill, allowWaterEmbankment, terrain.findExposedWaterSurface(outerEdge))) {
            return ShoulderSlopeBuild.empty();
        }
        int profileDirection = isFill ? -1 : 1;
        float slopeRatio = isFill ? fillRatio : cutRatio;
        // Terrain can rise/fall outward: the shoulder height alone cannot bound daylight distance.
        int usefulHorizontalRun = maxHorizontalRun;

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
            return ShoulderSlopeBuild.empty();
        }

        boolean meetsTerrain = slopeProfileMeetsTerrain(profile, isFill, outerEdge, normal, scale, terrain);
        boolean widthCapped = !meetsTerrain && profile.getLast()[0] >= maxHorizontalRun;
        if (widthCapped) {
            plan.noteSlopeBatterWidthCap();
        }
        if (widthCapped && !meetsTerrain) {
            meetsTerrain = trySoftGroundWidthCap(profile, isFill, outerEdge, normal, scale, terrain);
        }
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
                        samples, span.get(i), y, isFill, materialId, rayId, offset, slopeRatio,
                        outcome, terrain, host, allowWaterEmbankment, localDaylightAuthorized);
                }
            } else {
                addSlopeSample(
                    samples, samplePoint, slopeHeight, isFill, materialId, rayId,
                    horizontalOffset, slopeRatio, outcome, terrain, host, allowWaterEmbankment,
                    localDaylightAuthorized);
            }
            previousPoint = samplePoint;
            previousHeight = slopeHeight;
            previousOffset = horizontalOffset;
        }
        int maxProfileOffset = profile.stream().mapToInt(p -> p[0]).max().orElse(0);
        maxProfileOffset = Math.min(maxProfileOffset, maxHorizontalRun);
        SlopeBandRasterizer.ShoulderRay bandRay = new SlopeBandRasterizer.ShoulderRay(
            rayId,
            outerEdge,
            normal,
            SlopeBandRasterizer.heightTableFromProfile(profile, maxProfileOffset),
            isFill,
            materialId,
            slopeRatio,
            outcome,
            allowWaterEmbankment,
            localDaylightAuthorized,
            scale);
        return new ShoulderSlopeBuild(samples, bandRay);
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
            PlanHost host,
            boolean allowWaterEmbankment,
            boolean localDaylightAuthorized) {
        if (samplePoint == null || !terrain.isChunkLoaded(samplePoint)) {
            return;
        }
        // 射线剖面逐步外扩时，每柱复核水面禁填，避免岸上起点把样本写入禁填水域。
        if (RoadEarthworkPolicy.suppressFillOverWater(
                isFill, allowWaterEmbankment, terrain.findExposedWaterSurface(samplePoint))) {
            return;
        }
        Vec2d planPoint = host.canonicalColumnPoint(samplePoint);
        ColumnKey key = new ColumnKey(host.worldX(planPoint), host.worldZ(planPoint));
        samples.add(new SlopeProfileSample(
            key, planPoint, slopeHeight, isFill, materialId, rayId,
            horizontalOffset, slopeRatio, outcome, allowWaterEmbankment, localDaylightAuthorized,
            false));
    }

    /**
     * 宽度上限截断且剩余高差较小时，把坡面末端拉向自然地面，避免悬空半坡。
     */
    private static boolean trySoftGroundWidthCap(
            List<int[]> profile,
            boolean isFill,
            Vec2d outerEdge,
            Vec2d outwardNormal,
            double scale,
            TerrainSampler terrain) {
        if (profile == null || profile.isEmpty()) {
            return false;
        }
        int[] last = profile.getLast();
        Vec2d tip = outerEdge.add(outwardNormal.multiply(last[0] * scale));
        if (!terrain.isChunkLoaded(tip)) {
            return false;
        }
        int groundHeight = terrain.sampleSurfaceY(tip);
        int remaining = Math.abs(last[1] - groundHeight);
        if (remaining > RoadEarthworkPolicy.WIDTH_CAP_SOFT_GROUND_REMAINING) {
            return false;
        }
        last[1] = groundHeight;
        return slopeProfileMeetsTerrain(profile, isFill, outerEdge, outwardNormal, scale, terrain);
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
            RoadCorridorPlan plan,
            RoadCorridorConstructionMode mode,
            int designSurfaceY,
            Vec2d planPoint,
            TerrainSampler terrain,
            int tunnelThreshold,
            PlanHost host,
            Integer envelopeWidthBlocks) {
        return switch (mode) {
            case BRIDGE -> Math.max(
                designSurfaceY + host.tunnelStyle().clearHeight(),
                terrain.sampleColumnTopY(planPoint));
            case TUNNEL -> tunnelInteriorRoofTopY(host, designSurfaceY, envelopeWidthBlocks);
            case PORTAL -> portalRoofTopY(host, designSurfaceY, envelopeWidthBlocks);
            case SHELTER -> designSurfaceY + host.tunnelStyle().clearHeight();
            case OPEN -> openCorridorClearTopY(
                designSurfaceY, planPoint, terrain, tunnelThreshold, host,
                plan != null && plan.isLocalDaylightMarkingActive());
        };
    }

    /**
     * 开敞净空顶：浅路堑连到柱顶；洞穴/深覆土（净高以上仍有空洞或盖层过厚）只清到隧道设计净高，
     * 避免把洞顶上方整座山体打成天窗。
     * <p>
     * 覆土与洞穴判定只用工程实体地体；树冠等可清理装饰不计入「深覆土/洞穴」，
     * 否则林冠下的空隙会被误判为隧道/洞穴净空，边坡被抑制且树叶残留。
     * LocalDaylightCut 已批准时改为清到柱顶（短段单侧土堆削坡）。
     */
    static int openCorridorClearTopY(
            int designSurfaceY,
            Vec2d planPoint,
            TerrainSampler terrain,
            int tunnelThreshold,
            PlanHost host) {
        return openCorridorClearTopY(
            designSurfaceY, planPoint, terrain, tunnelThreshold, host, false);
    }

    static int openCorridorClearTopY(
            int designSurfaceY,
            Vec2d planPoint,
            TerrainSampler terrain,
            int tunnelThreshold,
            PlanHost host,
            boolean localDaylightAuthorized) {
        int clearHeight = Math.max(1, host.tunnelStyle().clearHeight());
        int localTop = designSurfaceY + clearHeight;
        if (terrain == null || planPoint == null) {
            return localTop;
        }
        int columnTop = terrain.sampleColumnTopY(planPoint);
        if (columnTop <= localTop) {
            return Math.max(columnTop, localTop);
        }
        if (localDaylightAuthorized) {
            return columnTop;
        }
        int worldX = host.worldX(planPoint);
        int worldZ = host.worldZ(planPoint);
        // Engineering surface only — tree canopy must not invent deep cover.
        int cover = terrain.sampleSurfaceY(planPoint) - designSurfaceY;
        boolean deepCover = cover >= RoadEarthworkPolicy.deepDaylightCoverBlocks(
            host.tunnelStyle(), tunnelThreshold);
        boolean voidAboveHeadroom = hasCaveCeilingAbove(
            terrain, worldX, worldZ, localTop, columnTop);
        if (deepCover || voidAboveHeadroom) {
            return localTop;
        }
        return columnTop;
    }

    /**
     * True cave/overhang: air (or non-engineering gap) under a later solid engineering roof.
     * Clearable decoration (leaves/logs) is skipped so forest canopy is not treated as a tunnel roof.
     */
    static boolean hasCaveCeilingAbove(
            TerrainSampler terrain,
            int worldX,
            int worldZ,
            int fromYExclusive,
            int toYInclusive) {
        if (terrain == null || toYInclusive <= fromYExclusive) {
            return false;
        }
        boolean sawGap = false;
        for (int y = fromYExclusive + 1; y <= toYInclusive; y++) {
            if (terrain.isRoadClearableDecoration(worldX, y, worldZ)) {
                continue;
            }
            if (!terrain.isSolidBlock(worldX, y, worldZ)) {
                sawGap = true;
                continue;
            }
            if (sawGap) {
                return true;
            }
        }
        return false;
    }

    static int portalRoofTopY(PlanHost host, int designSurfaceY) {
        return portalRoofTopY(host, designSurfaceY, null);
    }

    static int portalRoofTopY(PlanHost host, int designSurfaceY, Integer envelopeWidthBlocks) {
        int envelopeWidth = envelopeWidthBlocks != null && envelopeWidthBlocks > 0
            ? envelopeWidthBlocks
            : RoadCorridorWidth.gradingEnvelopeWidthBlocks(
                ResolvedCrossSection.fromConfig(host.config()));
        return TunnelPortalExcavationMask.forPortal(
            host.tunnelStyle(), envelopeWidth, designSurfaceY).roofTopY();
    }

    static int tunnelInteriorRoofTopY(PlanHost host, int designSurfaceY) {
        return tunnelInteriorRoofTopY(host, designSurfaceY, null);
    }

    static int tunnelInteriorRoofTopY(PlanHost host, int designSurfaceY, Integer envelopeWidthBlocks) {
        int envelopeWidth = envelopeWidthBlocks != null && envelopeWidthBlocks > 0
            ? envelopeWidthBlocks
            : RoadCorridorWidth.gradingEnvelopeWidthBlocks(
                ResolvedCrossSection.fromConfig(host.config()));
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

    private static Vec2d chordLeftNormal(Vec2d fromCenter, Vec2d toCenter, Vec2d startNormal, Vec2d endNormal) {
        Vec2d forward = toCenter.subtract(fromCenter);
        if (forward.lengthSquared() < 1e-12) {
            return startNormal;
        }
        Vec2d unit = forward.normalize();
        Vec2d chord = new Vec2d(-unit.y, unit.x);
        if (chord.dot(startNormal) + chord.dot(endNormal) < 0.0) {
            chord = chord.multiply(-1.0);
        }
        return normalize(chord);
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

        /**
         * Canvas plan point for an already-resolved world XZ column.
         * Must not treat world integers as canvas coordinates under a non-identity projection.
         */
        default Vec2d planPointForWorldColumn(int worldX, int worldZ) {
            return new Vec2d(worldX, worldZ);
        }

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
                public Vec2d planPointForWorldColumn(int worldX, int worldZ) {
                    if (projection == null || !projection.isValid()) {
                        return new Vec2d(worldX, worldZ);
                    }
                    return projection.toCanvas(new Vec2d(worldX, worldZ));
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
