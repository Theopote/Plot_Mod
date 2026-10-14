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
        PlanHost planHost = PlanHost.from(host, ctx.request().terrainStyle());
        return build(
            ctx.segments(),
            ctx.heightInfos(),
            ctx.request().crossSections(),
            ctx.detection().constructionTypes(),
            ctx.request().designElevation(),
            ctx.buildProfile(),
            ctx.terrain(),
            ctx.unitsPerBlock(),
            ctx.carriagewaySeedKey(),
            planHost);
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
            PlanHost host) {
        RoadCorridorPlan plan = new RoadCorridorPlan();
        List<StationSample> stations = collectStations(
            segments, heightInfos, crossSections, constructionTypes,
            designElevation, buildProfile, host::snapEndpointElevation, unitsPerBlock);
        if (stations.isEmpty()) {
            return plan;
        }
        RoadSystemConfig config = host.config();
        int tunnelThreshold = host.tunnelThreshold();
        for (int i = 0; i < stations.size(); i++) {
            StationSample sample = stations.get(i);
            ResolvedCrossSection crossSection = crossSections.resolve(sample.chainage());
            rasterizeStation(plan, sample, crossSection, terrain, unitsPerBlock,
                carriagewaySeedKey, host, config, tunnelThreshold);
            if (i > 0) {
                rasterizeSpan(plan, stations.get(i - 1), sample, crossSection, terrain,
                    unitsPerBlock, carriagewaySeedKey, host, config, tunnelThreshold);
            }
        }
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
            double unitsPerBlock) {
        List<StationSample> stations = new ArrayList<>();
        RoadPathStationSampler.forEach(
            segments,
            heightInfos,
            crossSections.samplingOriented(),
            unitsPerBlock,
            designElevation,
            buildProfile,
            snapper,
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

    private static void rasterizeStation(
            RoadCorridorPlan plan,
            StationSample sample,
            ResolvedCrossSection crossSection,
            TerrainSampler terrain,
            double unitsPerBlock,
            String seedKey,
            PlanHost host,
            RoadSystemConfig config,
            int tunnelThreshold) {
        rasterizeSurfaceBands(plan, sample.center(), sample.center(), sample.leftNormal(),
            sample.targetY(), sample.targetY(), crossSection, terrain, unitsPerBlock,
            seedKey, host, sample.constructionType(), config, tunnelThreshold);
        if (crossSection.includeSlopeBatter
                && RoadEarthworkPolicy.of(sample.constructionType()).allowsCorridorBatter()) {
            mergeSlopeWedge(plan, sample, crossSection, terrain, unitsPerBlock, host);
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
            int tunnelThreshold) {
        ResolvedCrossSection toSection = crossSection;
        RoadConstructionType fromType = from.constructionType();
        RoadConstructionType toType = to.constructionType();
        // Split type-boundary spans so PORTAL→TUNNEL / ROAD→BRIDGE does not tag the
        // transition half as STRUCTURE_ONLY (which strips slope and skips open clearance).
        if (fromType == toType) {
            rasterizeSurfaceBands(plan, from.center(), to.center(), to.leftNormal(),
                from.targetY(), to.targetY(), toSection, terrain, unitsPerBlock,
                seedKey, host, toType, config, tunnelThreshold);
        } else {
            Vec2d midCenter = from.center().lerp(to.center(), 0.5);
            int midY = (int) Math.round(from.targetY() + (to.targetY() - from.targetY()) * 0.5);
            Vec2d midNormal = normalize(from.leftNormal().lerp(to.leftNormal(), 0.5));
            rasterizeSurfaceBands(plan, from.center(), midCenter, midNormal,
                from.targetY(), midY, toSection, terrain, unitsPerBlock,
                seedKey, host, fromType, config, tunnelThreshold);
            rasterizeSurfaceBands(plan, midCenter, to.center(), to.leftNormal(),
                midY, to.targetY(), toSection, terrain, unitsPerBlock,
                seedKey, host, toType, config, tunnelThreshold);
        }
        if (toSection.includeSlopeBatter
                && (RoadEarthworkPolicy.of(fromType).allowsCorridorBatter()
                    || RoadEarthworkPolicy.of(toType).allowsCorridorBatter())) {
            mergeSlopeSpan(plan, from, to, toSection, terrain, unitsPerBlock, host);
        }
    }

    private static void rasterizeSurfaceBands(
            RoadCorridorPlan plan,
            Vec2d fromCenter,
            Vec2d toCenter,
            Vec2d leftNormal,
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
        Vec2d normal = normalize(leftNormal);

        int minOffset = RoadDimensionUtils.minLateralOffset(crossSection.carriagewayWidth);
        int maxOffset = RoadDimensionUtils.maxLateralOffset(crossSection.carriagewayWidth);
        rasterizeOffsetBand(
            plan, fromCenter, toCenter, normal, fromY, toY, scale,
            minOffset, maxOffset, 0.0, false,
            RoadSolidLayer.ROAD, crossSection.carriagewayMaterial, seedKey, host,
            terrain, constructionType, tunnelThreshold);

        if (crossSection.includeShoulder && crossSection.shoulderWidth > 0) {
            rasterizeLateralBand(plan, fromCenter, toCenter, normal, fromY, toY, scale,
                crossSection.shoulderWidth, crossSection.shoulderCenterOffset() * scale,
                RoadSolidLayer.SHOULDER, host.resolveBlockId(crossSection.shoulderMaterial),
                host, terrain, constructionType, tunnelThreshold);
        }
        if (crossSection.includeBikeLane && crossSection.bikeLaneWidth > 0) {
            rasterizeLateralBand(plan, fromCenter, toCenter, normal, fromY, toY, scale,
                crossSection.bikeLaneWidth, crossSection.bikeLaneCenterOffset() * scale,
                RoadSolidLayer.BIKE_LANE, host.resolveBlockId(crossSection.bikeLaneMaterial),
                host, terrain, constructionType, tunnelThreshold);
        }
        if (crossSection.includeSidewalk && crossSection.sidewalkWidth > 0) {
            rasterizeLateralBand(plan, fromCenter, toCenter, normal, fromY, toY, scale,
                crossSection.sidewalkWidth, crossSection.sidewalkCenterOffset() * scale,
                RoadSolidLayer.SIDEWALK, host.resolveBlockId(crossSection.sidewalkMaterial),
                host, terrain, constructionType, tunnelThreshold);
        }
        expandClearanceEnvelope(plan, fromCenter, toCenter, normal, fromY, toY, scale,
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
        int envelopeWidth = RoadCorridorWidth.decorationClearWidthBlocks(crossSection, config);
        if (envelopeWidth <= 0) {
            return;
        }
        int minOffset = RoadDimensionUtils.minLateralOffset(envelopeWidth);
        int maxOffset = RoadDimensionUtils.maxLateralOffset(envelopeWidth);
        for (int lateral = minOffset; lateral <= maxOffset; lateral++) {
            double offset = lateral * scale;
            writeClearanceSpan(plan, fromCenter, toCenter, leftNormal, offset, fromY, toY,
                terrain, constructionType, tunnelThreshold, host);
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
            PlanHost host) {
        Vec2d fromPoint = fromCenter.add(leftNormal.multiply(lateralOffset));
        Vec2d toPoint = toCenter.add(leftNormal.multiply(lateralOffset));
        List<Vec2d> span = RoadVoxelRasterizer.sampleSpanPoints(fromPoint, toPoint);
        int last = span.size() - 1;
        for (int i = 0; i < span.size(); i++) {
            Vec2d planPoint = span.get(i);
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
            int clearTopY = computeClearTopY(mode, surfaceY, planPoint, terrain, tunnelThreshold);
            plan.putClearance(key, new ClearanceCell(planPoint, surfaceY, clearTopY, mode, constructionType));
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
            Vec2d planPoint = span.get(i);
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
            Vec2d planPoint = span.get(i);
            int surfaceY = last == 0
                ? fromY
                : (int) Math.round(fromY + (toY - fromY) * ((double) i / last));
            writeSurfaceAndClearance(plan, planPoint, surfaceY, layer, materialId, host,
                terrain, constructionType, tunnelThreshold);
        }
    }

    private static void writeSurfaceAndClearance(
            RoadCorridorPlan plan,
            Vec2d planPoint,
            int surfaceY,
            RoadSolidLayer layer,
            String materialId,
            PlanHost host,
            TerrainSampler terrain,
            RoadConstructionType constructionType,
            int tunnelThreshold) {
        BlockPos worldPos = host.canvasToBlockPos(planPoint);
        int worldX = worldPos.getX();
        int worldZ = worldPos.getZ();
        if (!terrain.isChunkLoaded(worldX, worldZ)) {
            return;
        }
        ColumnKey key = new ColumnKey(worldX, worldZ);
        plan.surfaceMask().put(key, new SurfaceCell(planPoint, surfaceY, layer, materialId));

        RoadCorridorConstructionMode mode = toMode(constructionType);
        int clearTopY = computeClearTopY(mode, surfaceY, planPoint, terrain, tunnelThreshold);
        plan.putClearance(key, new ClearanceCell(planPoint, surfaceY, clearTopY, mode, constructionType));
    }

    private static void mergeSlopeWedge(
            RoadCorridorPlan plan,
            StationSample sample,
            ResolvedCrossSection crossSection,
            TerrainSampler terrain,
            double unitsPerBlock,
            PlanHost host) {
        mergeSlopeAtCenter(plan, sample.center(), sample.leftNormal(), sample.targetY(),
            crossSection, terrain, unitsPerBlock, host);
    }

    private static void mergeSlopeSpan(
            RoadCorridorPlan plan,
            StationSample from,
            StationSample to,
            ResolvedCrossSection crossSection,
            TerrainSampler terrain,
            double unitsPerBlock,
            PlanHost host) {
        List<Vec2d> span = RoadVoxelRasterizer.sampleSpanPoints(from.center(), to.center());
        int last = span.size() - 1;
        for (int i = 0; i <= last; i++) {
            double t = last == 0 ? 1.0 : (double) i / last;
            Vec2d center = span.get(i);
            int targetY = last == 0
                ? to.targetY()
                : (int) Math.round(from.targetY() + (to.targetY() - from.targetY()) * t);
            Vec2d leftNormal = last == 0
                ? normalize(to.leftNormal())
                : normalize(from.leftNormal().multiply(1.0 - t).add(to.leftNormal().multiply(t)));
            RoadConstructionType stepType = resolveStepConstructionType(from, to, t);
            if (!RoadEarthworkPolicy.of(stepType).allowsCorridorBatter()) {
                continue;
            }
            mergeSlopeAtCenter(plan, center, leftNormal, targetY, crossSection, terrain, unitsPerBlock, host);
        }
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

    private static void mergeSlopeAtCenter(
            RoadCorridorPlan plan,
            Vec2d center,
            Vec2d leftNormal,
            int targetY,
            ResolvedCrossSection crossSection,
            TerrainSampler terrain,
            double unitsPerBlock,
            PlanHost host) {
        int maxHorizontalRun = RoadCorridorWidth.MAX_SLOPE_BATTER_RUN_BLOCKS;
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        Vec2d normal = normalize(leftNormal);
        double shoulderCenterOffset = crossSection.slopeAnchorCenterOffset() * scale;
        double outerOffset = RoadDimensionUtils.halfExtentFromCenter(
            crossSection.slopeAnchorBandWidth()) * scale;
        String fillBlockId = host.resolveBlockId(crossSection.fillSlopeMaterial);
        String cutBlockId = crossSection.cutSlopeMaterial == null || crossSection.cutSlopeMaterial.isBlank()
            ? null
            : host.resolveBlockId(crossSection.cutSlopeMaterial);
        float fillRatio = crossSection.fillSlopeRatio;
        float cutRatio = crossSection.cutSlopeRatio;

        Vec2d left = center.add(normal.multiply(shoulderCenterOffset));
        Vec2d right = center.subtract(normal.multiply(shoulderCenterOffset));
        mergeSlopeAtShoulder(plan, left, targetY, outerOffset, terrain,
            fillRatio, cutRatio, fillBlockId, cutBlockId, maxHorizontalRun,
            normal, scale, host);
        mergeSlopeAtShoulder(plan, right, targetY, outerOffset, terrain,
            fillRatio, cutRatio, fillBlockId, cutBlockId, maxHorizontalRun,
            normal.multiply(-1), scale, host);
    }

    private static void mergeSlopeAtShoulder(
            RoadCorridorPlan plan,
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
            double unitsPerBlock,
            PlanHost host) {
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        Vec2d normal = normalize(outwardNormal);
        Vec2d outerEdge = shoulderCenter.add(normal.multiply(outerOffset));
        if (!terrain.isChunkLoaded(outerEdge)) {
            return;
        }
        int groundAtEdge = terrain.sampleSurfaceY(outerEdge);
        if (targetY == groundAtEdge) {
            return;
        }
        boolean isFill = targetY > groundAtEdge;
        int profileDirection = isFill ? -1 : 1;
        float slopeRatio = isFill ? fillRatio : cutRatio;
        int heightDifference = Math.abs(targetY - groundAtEdge);
        int unconstrainedRun = (int) Math.ceil(heightDifference * Math.max(0.5f, slopeRatio)) + 2;
        if (unconstrainedRun > maxHorizontalRun) {
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

        for (int step = 1; step < profile.size(); step++) {
            int[] point = profile.get(step);
            int horizontalOffset = point[0];
            int slopeHeight = point[1];
            Vec2d sample = outerEdge.add(normal.multiply(horizontalOffset * scale));
            if (!terrain.isChunkLoaded(sample)) {
                continue;
            }
            int worldX = host.worldX(sample);
            int worldZ = host.worldZ(sample);
            ColumnKey key = new ColumnKey(worldX, worldZ);
            SlopeColumnCell cell = plan.slopeCellOrCreate(key, sample);
            if (isFill) {
                cell.mergeFill(slopeHeight, fillBlockId);
            } else {
                cell.mergeCut(slopeHeight, cutBlockId);
            }
        }
        if (!slopeProfileMeetsTerrain(profile, isFill, outerEdge, normal, scale, terrain)) {
            plan.noteUnresolvedSlopeConstruction();
        }
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
        // Portals are open cut mouths: clear to terrain top like ROAD/CUT, not tunnel cavity height.
        if (type == RoadConstructionType.TUNNEL_PORTAL) {
            return RoadCorridorConstructionMode.OPEN;
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
            int tunnelThreshold) {
        return switch (mode) {
            case BRIDGE -> Math.max(
                designSurfaceY + 1,
                Math.max(terrain.sampleColumnTopY(planPoint), designSurfaceY + 1));
            case TUNNEL -> designSurfaceY + tunnelThreshold;
            case OPEN -> Math.max(terrain.sampleColumnTopY(planPoint), designSurfaceY + 1);
        };
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

        int worldX(Vec2d planPoint);

        int worldZ(Vec2d planPoint);

        int tunnelThreshold();

        int bridgeThreshold();

        static PlanHost from(RoadGenerationPipelineContext.Host host, RoadTerrainStyle terrainStyle) {
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
            return new PlanHost() {
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
            };
        }
    }
}
