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

        List<BridgeSegment> bridges = new ArrayList<>();
        List<TunnelSegment> tunnels = new ArrayList<>();
        List<RoadConstructionType> resolvedTypes = new ArrayList<>(constructionTypes);
        boolean chainForward = true;

        for (int i = 0; i < resolvedTypes.size() && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            RoadConstructionType type = resolvedTypes.get(i);
            if (type == RoadConstructionType.BRIDGE) {
                int heightDifference = Math.max(
                    info.targetStart - info.groundStart,
                    info.targetEnd - info.groundEnd);
                bridges.add(new BridgeSegment(info.segment, Math.max(0, heightDifference)));
            } else if (type == RoadConstructionType.TUNNEL) {
                if (isSubmerged(info) || !validateTunnelSegment(
                    info, terrain, canvasToBlockPos, gradingEnvelopeWidth, tunnelStyle, columnResolver, chainForward)) {
                    resolvedTypes.set(i, RoadConstructionType.CUT);
                } else {
                    int heightDifference = Math.max(
                        info.groundStart - info.targetStart,
                        info.groundEnd - info.targetEnd);
                    tunnels.add(new TunnelSegment(info.segment, Math.max(0, heightDifference)));
                }
            }
        }

        markPortalAndAbutmentSegments(resolvedTypes);
        return new ConstructionDetection(
            bridges,
            tunnels,
            resolvedTypes,
            segmentDistances,
            buildRuns(resolvedTypes, segmentDistances, groundHeights, targetHeights));
    }

    static ConstructionDetection finalizeDetection(
            List<RoadConstructionType> resolvedTypes,
            List<Double> segmentDistances,
            List<SegmentHeightInfo> heightInfos,
            CanvasBlockPosResolver canvasToBlockPos,
            TerrainSampler terrain) {
        List<BridgeSegment> bridges = new ArrayList<>();
        List<TunnelSegment> tunnels = new ArrayList<>();
        List<Integer> groundHeights = new ArrayList<>();
        List<Integer> targetHeights = new ArrayList<>();
        for (int i = 0; i < resolvedTypes.size() && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            groundHeights.add(averageHeight(info.groundStart, info.groundEnd));
            targetHeights.add(averageHeight(info.targetStart, info.targetEnd));
            RoadConstructionType type = resolvedTypes.get(i);
            if (type == RoadConstructionType.BRIDGE) {
                int heightDifference = Math.max(
                    info.targetStart - info.groundStart,
                    info.targetEnd - info.groundEnd);
                bridges.add(new BridgeSegment(info.segment, Math.max(0, heightDifference)));
            } else if (type == RoadConstructionType.TUNNEL && terrain != null && canvasToBlockPos != null) {
                if (isSubmerged(info) || !validateTunnelSegment(
                    info, terrain, canvasToBlockPos, 0, ResolvedTunnelStyle.defaults(), null, true)) {
                    resolvedTypes.set(i, RoadConstructionType.CUT);
                } else {
                    int heightDifference = Math.max(
                        info.groundStart - info.targetStart,
                        info.groundEnd - info.targetEnd);
                    tunnels.add(new TunnelSegment(info.segment, Math.max(0, heightDifference)));
                }
            }
        }
        markPortalAndAbutmentSegments(resolvedTypes);
        return new ConstructionDetection(
            bridges,
            tunnels,
            List.copyOf(resolvedTypes),
            segmentDistances,
            buildRuns(resolvedTypes, segmentDistances, groundHeights, targetHeights));
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

    private static boolean isSubmerged(SegmentHeightInfo info) {
        return info.waterStart != null || info.waterEnd != null;
    }

    private static boolean validateTunnelSegment(
            SegmentHeightInfo info,
            TerrainSampler terrain,
            CanvasBlockPosResolver canvasToBlockPos,
            int gradingEnvelopeWidth,
            ResolvedTunnelStyle tunnelStyle,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            boolean chainForward) {
        if (terrain == null || canvasToBlockPos == null || tunnelStyle == null) {
            return false;
        }
        Vec2d mid = info.segment.start.lerp(info.segment.end, 0.5);
        int targetY = Math.round((info.targetStart + info.targetEnd) / 2.0f);
        var pos = canvasToBlockPos.resolve(mid).withY(targetY);
        if (!terrain.isSolidBlock(pos.getX(), pos.getY(), pos.getZ())) {
            return false;
        }
        if (gradingEnvelopeWidth <= 0 || columnResolver == null) {
            return true;
        }
        Vec2d leftNormal = PathSegmentGeometry.chainLeftNormal(info.segment, chainForward);
        TunnelFeasibility feasibility = TunnelFeasibilityChecker.check(
            terrain,
            mid,
            leftNormal,
            targetY,
            gradingEnvelopeWidth,
            tunnelStyle,
            columnResolver,
            1.0);
        return feasibility.valid();
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
