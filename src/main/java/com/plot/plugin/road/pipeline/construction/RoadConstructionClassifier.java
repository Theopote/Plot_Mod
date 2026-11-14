package com.plot.plugin.road.pipeline.construction;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionEvaluator;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.pipeline.geometry.PathSegmentGeometry;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelFeasibility;
import com.plot.plugin.road.tunnel.TunnelFeasibilityChecker;
import com.plot.core.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.List;

/**
 * Classifies each segment as Normal ({@link RoadConstructionType#ROAD}),
 * Fill, Cut, Bridge, or Tunnel.
 */
public final class RoadConstructionClassifier {
    private RoadConstructionClassifier() {
    }

    public static ConstructionDetection classify(
            List<com.plot.plugin.road.pipeline.geometry.PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            TerrainSampler terrain,
            RoadSystemConfig config,
            CanvasBlockPosResolver canvasToBlockPos) {
        return classify(segments, heightInfos, terrain, config, null, canvasToBlockPos, 0,
            ResolvedTunnelStyle.defaults(), null);
    }

    public static ConstructionDetection classify(
            List<com.plot.plugin.road.pipeline.geometry.PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            TerrainSampler terrain,
            RoadSystemConfig config,
            RoadTerrainStyle terrainStyle,
            CanvasBlockPosResolver canvasToBlockPos) {
        return classify(segments, heightInfos, terrain, config, terrainStyle, canvasToBlockPos, 0,
            ResolvedTunnelStyle.defaults(), null);
    }

    public static ConstructionDetection classify(
            List<com.plot.plugin.road.pipeline.geometry.PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            TerrainSampler terrain,
            RoadSystemConfig config,
            RoadTerrainStyle terrainStyle,
            CanvasBlockPosResolver canvasToBlockPos,
            int gradingEnvelopeWidth,
            ResolvedTunnelStyle tunnelStyle,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver) {
        return classify(segments, heightInfos, terrain, config, terrainStyle, canvasToBlockPos,
            gradingEnvelopeWidth, tunnelStyle, columnResolver, 1.0);
    }

    public static ConstructionDetection classify(
            List<com.plot.plugin.road.pipeline.geometry.PathSegment> segments,
            List<SegmentHeightInfo> heightInfos, TerrainSampler terrain, RoadSystemConfig config,
            RoadTerrainStyle terrainStyle, CanvasBlockPosResolver canvasToBlockPos,
            int gradingEnvelopeWidth, ResolvedTunnelStyle tunnelStyle,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver, double unitsPerBlock) {
        List<Double> segmentDistances = new ArrayList<>();
        List<Integer> groundHeights = new ArrayList<>();
        List<Integer> targetHeights = new ArrayList<>();

        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            segmentDistances.add(info.segment.distance);
            groundHeights.add(averageHeight(info.groundStart, info.groundEnd));
            targetHeights.add(averageHeight(info.targetStart, info.targetEnd));
        }

        RoadConstructionEvaluator.RoadConstructionScoreConfig scoreConfig = terrainStyle != null
            ? RoadConstructionHeuristics.constructionConfig(terrainStyle)
            : RoadConstructionEvaluator.RoadConstructionScoreConfig.from(config);
        List<RoadConstructionType> constructionTypes = RoadConstructionEvaluator.evaluatePath(
            segmentDistances,
            groundHeights,
            targetHeights,
            scoreConfig,
            RoadConstructionHeuristics.MIN_STRUCTURE_RUN);

        List<RoadConstructionType> resolvedTypes = new ArrayList<>(constructionTypes);
        boolean chainForward = true;

        for (int i = 0; i < resolvedTypes.size() && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            RoadConstructionType type = resolvedTypes.get(i);
            if (!type.isBridgeFamily() && !isSubmerged(info)) {
                RoadConstructionType shelter = cliffShelterType(info, terrain, gradingEnvelopeWidth,
                    tunnelStyle, columnResolver, unitsPerBlock);
                if (shelter != null) {
                    resolvedTypes.set(i, shelter);
                    continue;
                }
            }
            if (type == RoadConstructionType.TUNNEL) {
                if (isSubmerged(info)) {
                    resolvedTypes.set(i, RoadConstructionType.CUT);
                } else if (!validateTunnelSegment(
                    info, terrain, canvasToBlockPos, gradingEnvelopeWidth, tunnelStyle, columnResolver, chainForward, unitsPerBlock)) {
                    // 无全断面连续盖层时：仅当洞室空腔内仍有实体、且中心线上方确有覆土时保留隧道
                    // （洞穴钟乳清腔）。开敞路堑侧壁伸入包络、中心线已见天，必须降为普通路以便放坡。
                    if (hasSolidWithinClearHeight(
                            info, terrain, canvasToBlockPos, gradingEnvelopeWidth, tunnelStyle,
                            columnResolver, chainForward, unitsPerBlock)
                            && hasRoofOverburden(info, terrain, tunnelStyle)) {
                        // keep TUNNEL
                    } else {
                        resolvedTypes.set(i, RoadConstructionType.ROAD);
                    }
                }
            }
        }

        mergeShortTunnelGaps(resolvedTypes, segmentDistances, unitsPerBlock);
        // After tunnel continuity merge: short shelter islands between open road
        // may daylight-cut instead of generating portals/lining for a few dirt blocks.
        var localDaylight = LocalDaylightCut.apply(
            resolvedTypes,
            segmentDistances,
            heightInfos,
            terrain,
            gradingEnvelopeWidth,
            tunnelStyle,
            columnResolver,
            unitsPerBlock);
        List<BridgeSegment> bridges = new ArrayList<>();
        List<TunnelSegment> tunnels = new ArrayList<>();
        collectStructureSegments(resolvedTypes, heightInfos, bridges, tunnels);

        markPortalAndAbutmentSegments(resolvedTypes);
        return new ConstructionDetection(
            bridges,
            tunnels,
            resolvedTypes,
            segmentDistances,
            buildRuns(resolvedTypes, segmentDistances, groundHeights, targetHeights),
            localDaylight);
    }

    /**
     * Rebuild bridge/tunnel runs and portal/abutment marks after water strategies rewrite
     * overlapping segments only. Does <strong>not</strong> re-validate tunnels with default
     * style/envelope (that would destabilize mountain tunnels on the same road).
     */
    static ConstructionDetection rebuildAfterWaterStrategy(
            List<RoadConstructionType> resolvedTypes,
            List<Double> segmentDistances,
            List<SegmentHeightInfo> heightInfos,
            double unitsPerBlock,
            java.util.Set<Integer> preservedLocalDaylight) {
        List<Integer> groundHeights = new ArrayList<>();
        List<Integer> targetHeights = new ArrayList<>();
        for (int i = 0; i < resolvedTypes.size() && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            groundHeights.add(averageHeight(info.groundStart, info.groundEnd));
            targetHeights.add(averageHeight(info.targetStart, info.targetEnd));
        }
        mergeShortTunnelGaps(resolvedTypes, segmentDistances, unitsPerBlock);
        java.util.Set<Integer> localDaylight = new java.util.LinkedHashSet<>();
        if (preservedLocalDaylight != null) {
            for (int index : preservedLocalDaylight) {
                if (index >= 0
                        && index < resolvedTypes.size()
                        && resolvedTypes.get(index) == RoadConstructionType.CUT) {
                    localDaylight.add(index);
                }
            }
        }
        List<BridgeSegment> bridges = new ArrayList<>();
        List<TunnelSegment> tunnels = new ArrayList<>();
        collectStructureSegments(resolvedTypes, heightInfos, bridges, tunnels);
        markPortalAndAbutmentSegments(resolvedTypes);
        return new ConstructionDetection(
            bridges,
            tunnels,
            List.copyOf(resolvedTypes),
            segmentDistances,
            buildRuns(resolvedTypes, segmentDistances, groundHeights, targetHeights),
            localDaylight);
    }

    private static void collectStructureSegments(
            List<RoadConstructionType> resolvedTypes,
            List<SegmentHeightInfo> heightInfos,
            List<BridgeSegment> bridges,
            List<TunnelSegment> tunnels) {
        for (int i = 0; i < resolvedTypes.size() && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            RoadConstructionType type = resolvedTypes.get(i);
            if (type == RoadConstructionType.BRIDGE || type == RoadConstructionType.BRIDGE_ABUTMENT) {
                int heightDifference = Math.max(
                    info.targetStart - info.groundStart,
                    info.targetEnd - info.groundEnd);
                bridges.add(new BridgeSegment(info.segment, Math.max(0, heightDifference)));
            } else if (type.isTunnelFamily()) {
                int heightDifference = Math.max(
                    info.groundStart - info.targetStart,
                    info.groundEnd - info.targetEnd);
                tunnels.add(new TunnelSegment(info.segment, Math.max(0, heightDifference)));
            }
        }
    }

    /**
     * 将隧道族之间过短的非隧道开敞段并回 {@link RoadConstructionType#TUNNEL}。
     * 仅处理隧道↔隧道间隙；桥或路侧开敞不受影响。
     */
    static void mergeShortTunnelGaps(
            List<RoadConstructionType> types,
            List<Double> segmentDistances,
            double unitsPerBlock) {
        if (types == null || types.isEmpty() || segmentDistances == null) {
            return;
        }
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        double maxGapCanvas = RoadConstructionHeuristics.MAX_TUNNEL_GAP_MERGE_BLOCKS * scale;
        int index = 0;
        while (index < types.size()) {
            if (!types.get(index).isTunnelFamily()) {
                index++;
                continue;
            }
            int gapStart = index + 1;
            while (gapStart < types.size() && types.get(gapStart).isTunnelFamily()) {
                gapStart++;
            }
            if (gapStart >= types.size()) {
                break;
            }
            int gapEnd = gapStart;
            double gapLength = 0.0;
            while (gapEnd < types.size() && !types.get(gapEnd).isTunnelFamily()) {
                if (gapEnd < segmentDistances.size()) {
                    gapLength += segmentDistances.get(gapEnd);
                }
                gapEnd++;
            }
            if (gapEnd < types.size()
                    && types.get(gapEnd).isTunnelFamily()
                    && gapLength > 1e-9
                    && gapLength <= maxGapCanvas + 1e-9) {
                for (int i = gapStart; i < gapEnd; i++) {
                    // 不覆盖桥段：桥在隧道间隙中应保持结构
                    if (!types.get(i).isBridgeFamily()) {
                        types.set(i, RoadConstructionType.TUNNEL);
                    }
                }
                index = gapEnd;
            } else {
                index = gapStart;
            }
        }
    }

    /** 将桥隧成段的首尾标为桥台 / 洞门，供土方与边坡按类型处理而不再依赖 run 边界探测。 */
    static void markPortalAndAbutmentSegments(List<RoadConstructionType> types) {
        if (types == null || types.isEmpty()) {
            return;
        }
        int index = 0;
        while (index < types.size()) {
            RoadConstructionType family = types.get(index).family();
            int start = index;
            while (index < types.size() && types.get(index).family() == family) {
                index++;
            }
            int end = index - 1;
            if (family == RoadConstructionType.BRIDGE) {
                types.set(start, RoadConstructionType.BRIDGE_ABUTMENT);
                types.set(end, RoadConstructionType.BRIDGE_ABUTMENT);
            } else if (family == RoadConstructionType.TUNNEL) {
                types.set(start, RoadConstructionType.TUNNEL_PORTAL);
                types.set(end, RoadConstructionType.TUNNEL_PORTAL);
            }
        }
    }

    /** Only sustained, one-sided rock cuts qualify; ordinary banks keep their batter. */
    static RoadConstructionType cliffShelterType(
            SegmentHeightInfo info, TerrainSampler terrain, int width, ResolvedTunnelStyle style,
            RoadTerrainClearanceUtils.BlockColumnResolver columns, double unitsPerBlock) {
        if (terrain == null || columns == null || style == null || width <= 0) return null;
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        Vec2d normal = PathSegmentGeometry.chainLeftNormal(info.segment, true);
        int left = com.plot.plugin.road.RoadDimensionUtils.maxLateralOffset(width) + style.sideClearance() + 1;
        int right = com.plot.plugin.road.RoadDimensionUtils.minLateralOffset(width) - style.sideClearance() - 1;
        int high = Math.max(16, style.clearHeight() * 2 + style.liningThickness());
        RoadConstructionType candidate = null;
        for (double t : new double[] {0.25, 0.5, 0.75}) {
            Vec2d center = info.segment.start.lerp(info.segment.end, t);
            int y = (int) Math.round(info.targetStart + (info.targetEnd - info.targetStart) * t);
            Vec2d lp = center.add(normal.multiply(left * scale));
            Vec2d rp = center.add(normal.multiply(right * scale));
            if (!terrain.isChunkLoaded(columns.worldX(lp), columns.worldZ(lp))
                    || !terrain.isChunkLoaded(columns.worldX(rp), columns.worldZ(rp))) return null;
            int lh = terrain.sampleSurfaceY(lp) - y;
            int rh = terrain.sampleSurfaceY(rp) - y;
            RoadConstructionType atSample;
            // 半崖/挂壁：靠山侧覆土足够高即可；不强制洞高处有实体（洞穴或沙砾塌落后仍按棚洞，禁靠山放坡）
            if (lh >= high && rh < style.clearHeight()) {
                atSample = RoadConstructionType.ROCK_SHELTER_LEFT;
            } else if (rh >= high && lh < style.clearHeight()) {
                atSample = RoadConstructionType.ROCK_SHELTER_RIGHT;
            } else return null;
            if (candidate != null && candidate != atSample) return null;
            candidate = atSample;
        }
        return candidate;
    }

    private static boolean isSubmerged(SegmentHeightInfo info) {
        return info.waterStart != null || info.waterEnd != null;
    }

    /** Sparse along-segment samples for tunnel / daylight transitions. */
    private static final double[] TUNNEL_SAMPLE_TS = {0.0, 0.25, 0.5, 0.75, 1.0};

    private static boolean validateTunnelSegment(
            SegmentHeightInfo info,
            TerrainSampler terrain,
            CanvasBlockPosResolver canvasToBlockPos,
            int gradingEnvelopeWidth,
            ResolvedTunnelStyle tunnelStyle,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            boolean chainForward, double unitsPerBlock) {
        if (terrain == null || canvasToBlockPos == null || tunnelStyle == null || info == null
                || info.segment == null) {
            return false;
        }
        // 全部稀疏采样点均须具备连续洞顶覆土；中点成立而端部见天时不得整段锁死为隧道。
        for (double t : TUNNEL_SAMPLE_TS) {
            if (!validateTunnelAt(info, t, terrain, canvasToBlockPos, gradingEnvelopeWidth,
                    tunnelStyle, columnResolver, chainForward, unitsPerBlock)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validateTunnelAt(
            SegmentHeightInfo info,
            double t,
            TerrainSampler terrain,
            CanvasBlockPosResolver canvasToBlockPos,
            int gradingEnvelopeWidth,
            ResolvedTunnelStyle tunnelStyle,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            boolean chainForward,
            double unitsPerBlock) {
        Vec2d point = info.segment.start.lerp(info.segment.end, t);
        int targetY = interpolateTargetY(info, t);
        if (!terrain.isChunkLoaded(point)) {
            return false;
        }
        if (gradingEnvelopeWidth <= 0 || columnResolver == null) {
            int surfaceY = terrain.sampleSurfaceY(point);
            return surfaceY - targetY >= tunnelStyle.clearHeight()
                + TunnelFeasibility.MINIMUM_COVER_BLOCKS;
        }
        Vec2d leftNormal = PathSegmentGeometry.chainLeftNormal(info.segment, chainForward);
        return TunnelFeasibilityChecker.check(
            terrain,
            point,
            leftNormal,
            targetY,
            gradingEnvelopeWidth,
            tunnelStyle,
            columnResolver,
            unitsPerBlock).valid();
    }

    /**
     * 中心线稀疏采样柱在洞顶以上是否均有连续工程覆土。
     * 用 {@link TunnelFeasibilityChecker#measureContinuousCoverAbove}，不用地表高度近似，
     * 避免天然拱桥/洞穴上空独立岩层误判。
     */
    static boolean hasRoofOverburden(
            SegmentHeightInfo info,
            TerrainSampler terrain,
            ResolvedTunnelStyle tunnelStyle) {
        if (info == null || terrain == null || info.segment == null || tunnelStyle == null) {
            return false;
        }
        int coverNeed = TunnelFeasibility.MINIMUM_COVER_BLOCKS;
        int roofOffset = Math.max(1, tunnelStyle.roofTopOffset());
        for (double t : TUNNEL_SAMPLE_TS) {
            Vec2d point = info.segment.start.lerp(info.segment.end, t);
            if (!terrain.isChunkLoaded(point)) {
                return false;
            }
            int targetY = interpolateTargetY(info, t);
            int roofY = targetY + roofOffset;
            int worldX = (int) Math.round(point.x);
            int worldZ = (int) Math.round(point.y);
            if (TunnelFeasibilityChecker.measureContinuousCoverAbove(
                    terrain, worldX, worldZ, roofY) < coverNeed) {
                return false;
            }
        }
        return true;
    }

    /**
     * 洞室 AIR 格内是否仍有工程实体妨碍（洞穴钟乳/夹石）。
     * 沿段稀疏采样；须与 {@link #hasRoofOverburden} 合用。
     */
    static boolean hasSolidWithinClearHeight(
            SegmentHeightInfo info,
            TerrainSampler terrain,
            CanvasBlockPosResolver canvasToBlockPos,
            int gradingEnvelopeWidth,
            ResolvedTunnelStyle tunnelStyle,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            boolean chainForward,
            double unitsPerBlock) {
        if (info == null || terrain == null || tunnelStyle == null || info.segment == null) {
            return false;
        }
        for (double t : TUNNEL_SAMPLE_TS) {
            if (hasSolidWithinClearHeightAt(
                    info, t, terrain, canvasToBlockPos, gradingEnvelopeWidth, tunnelStyle,
                    columnResolver, chainForward, unitsPerBlock)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSolidWithinClearHeightAt(
            SegmentHeightInfo info,
            double t,
            TerrainSampler terrain,
            CanvasBlockPosResolver canvasToBlockPos,
            int gradingEnvelopeWidth,
            ResolvedTunnelStyle tunnelStyle,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            boolean chainForward,
            double unitsPerBlock) {
        Vec2d sample = info.segment.start.lerp(info.segment.end, t);
        int targetY = interpolateTargetY(info, t);
        int clear = Math.max(1, tunnelStyle.clearHeight());
        if (columnResolver == null || gradingEnvelopeWidth <= 0) {
            if (canvasToBlockPos == null) {
                return false;
            }
            var pos = canvasToBlockPos.resolve(sample);
            return hasCavityObstructionInColumn(terrain, pos.getX(), pos.getZ(), targetY, clear);
        }
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        Vec2d rawNormal = PathSegmentGeometry.chainLeftNormal(info.segment, chainForward);
        final Vec2d normal = rawNormal.lengthSquared() > 1e-12 ? rawNormal.normalize() : rawNormal;
        var mask = com.plot.plugin.road.tunnel.TunnelCrossSectionMask.build(
            tunnelStyle.shape(),
            gradingEnvelopeWidth,
            tunnelStyle.sideClearance(),
            tunnelStyle.clearHeight(),
            tunnelStyle.liningThickness(),
            targetY,
            false);
        final boolean[] found = {false};
        mask.forEach((lateral, y, kind) -> {
            if (found[0] || kind != com.plot.plugin.road.tunnel.TunnelCrossSectionMask.CellKind.AIR) {
                return;
            }
            if (y <= targetY || y > targetY + clear) {
                return;
            }
            Vec2d point = sample.add(normal.multiply(lateral * scale));
            int worldX = columnResolver.worldX(point);
            int worldZ = columnResolver.worldZ(point);
            if (terrain.isRoadClearableDecoration(worldX, y, worldZ)) {
                return;
            }
            if (terrain.isSolidBlock(worldX, y, worldZ)) {
                found[0] = true;
            }
        });
        return found[0];
    }

    private static int interpolateTargetY(SegmentHeightInfo info, double t) {
        double clamped = Math.clamp(t, 0.0, 1.0);
        return (int) Math.round(info.targetStart + (info.targetEnd - info.targetStart) * clamped);
    }

    private static boolean hasCavityObstructionInColumn(
            TerrainSampler terrain, int worldX, int worldZ, int roadY, int clearHeight) {
        for (int y = roadY + 1; y <= roadY + clearHeight; y++) {
            if (terrain.isRoadClearableDecoration(worldX, y, worldZ)) {
                continue;
            }
            if (terrain.isSolidBlock(worldX, y, worldZ)) {
                return true;
            }
        }
        return false;
    }

    private static List<ConstructionRun> buildRuns(
            List<RoadConstructionType> types,
            List<Double> distances,
            List<Integer> groundHeights,
            List<Integer> targetHeights) {
        List<ConstructionRun> runs = new ArrayList<>();
        double station = 0.0;
        int index = 0;
        while (index < types.size()) {
            int start = index;
            double startStation = station;
            int maximum = 0;
            double weightedDifference = 0.0;
            double length = 0.0;
            RoadConstructionType type = types.get(index).family();
            while (index < types.size() && types.get(index).family() == type) {
                double distance = distances.get(index);
                int difference = targetHeights.get(index) - groundHeights.get(index);
                maximum = Math.max(maximum, Math.abs(difference));
                weightedDifference += difference * distance;
                length += distance;
                station += distance;
                index++;
            }
            runs.add(new ConstructionRun(
                type, start, index, startStation, station, maximum,
                length > 1e-9 ? weightedDifference / length : 0.0));
        }
        return List.copyOf(runs);
    }

    public static RoadConstructionType constructionTypeAt(
            List<RoadConstructionType> constructionTypes,
            int segmentIndex) {
        if (constructionTypes == null || segmentIndex < 0 || segmentIndex >= constructionTypes.size()) {
            return RoadConstructionType.ROAD;
        }
        RoadConstructionType type = constructionTypes.get(segmentIndex);
        return type != null ? type : RoadConstructionType.ROAD;
    }

    public static boolean isStructureType(RoadConstructionType type) {
        return type != null && type.isStructure();
    }

    /** 桥隧构造段的起点（洞门 / 桥台）。 */
    public static boolean isStructureRunStart(List<RoadConstructionType> constructionTypes, int segmentIndex) {
        RoadConstructionType type = constructionTypeAt(constructionTypes, segmentIndex);
        if (!isStructureType(type)) {
            return false;
        }
        return segmentIndex <= 0
            || constructionTypeAt(constructionTypes, segmentIndex - 1).family() != type.family();
    }

    /** 桥隧构造段的终点（洞门 / 桥台）。 */
    public static boolean isStructureRunEnd(List<RoadConstructionType> constructionTypes, int segmentIndex) {
        RoadConstructionType type = constructionTypeAt(constructionTypes, segmentIndex);
        if (!isStructureType(type)) {
            return false;
        }
        return constructionTypes == null
            || segmentIndex >= constructionTypes.size() - 1
            || constructionTypeAt(constructionTypes, segmentIndex + 1).family() != type.family();
    }

    private static int averageHeight(int a, int b) {
        return (int) Math.round((a + b) / 2.0);
    }

    @FunctionalInterface
    public interface CanvasBlockPosResolver {
        net.minecraft.util.math.BlockPos resolve(Vec2d canvasPos);
    }
}
