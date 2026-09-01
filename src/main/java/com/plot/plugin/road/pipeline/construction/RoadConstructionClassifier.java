package com.plot.plugin.road.pipeline.construction;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionEvaluator;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;

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
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            TerrainSampler terrain,
            RoadSystemConfig config,
            CanvasBlockPosResolver canvasToBlockPos) {
        List<Double> segmentDistances = new ArrayList<>();
        List<Integer> groundHeights = new ArrayList<>();
        List<Integer> targetHeights = new ArrayList<>();

        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            segmentDistances.add(info.segment.distance);
            groundHeights.add(averageHeight(info.groundStart, info.groundEnd));
            targetHeights.add(averageHeight(info.targetStart, info.targetEnd));
        }

        RoadConstructionEvaluator.RoadConstructionCostConfig costConfig =
            RoadConstructionEvaluator.RoadConstructionCostConfig.from(config);
        List<RoadConstructionType> constructionTypes = RoadConstructionEvaluator.evaluatePath(
            segmentDistances,
            groundHeights,
            targetHeights,
            costConfig,
            config.getMinimumConstructionRunLength());

        List<BridgeSegment> bridges = new ArrayList<>();
        List<TunnelSegment> tunnels = new ArrayList<>();
        List<RoadConstructionType> resolvedTypes = new ArrayList<>(constructionTypes);

        for (int i = 0; i < resolvedTypes.size() && i < heightInfos.size(); i++) {
            SegmentHeightInfo info = heightInfos.get(i);
            RoadConstructionType type = resolvedTypes.get(i);
            if (type == RoadConstructionType.BRIDGE) {
                int heightDifference = Math.max(
                    info.targetStart - info.groundStart,
                    info.targetEnd - info.groundEnd);
                bridges.add(new BridgeSegment(info.segment, Math.max(0, heightDifference)));
            } else if (type == RoadConstructionType.TUNNEL) {
                Vec2d mid = info.segment.start.lerp(info.segment.end, 0.5);
                int targetY = Math.round((info.targetStart + info.targetEnd) / 2.0f);
                BlockPos pos = canvasToBlockPos.resolve(mid).withY(targetY);
                if (terrain.isSolidBlock(pos.getX(), pos.getY(), pos.getZ())) {
                    int heightDifference = Math.max(
                        info.groundStart - info.targetStart,
                        info.groundEnd - info.targetEnd);
                    tunnels.add(new TunnelSegment(info.segment, Math.max(0, heightDifference)));
                } else {
                    resolvedTypes.set(i, RoadConstructionType.CUT);
                }
            }
        }

        return new ConstructionDetection(
            bridges,
            tunnels,
            resolvedTypes,
            segmentDistances,
            buildRuns(resolvedTypes, segmentDistances, groundHeights, targetHeights));
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
            RoadConstructionType type = types.get(index);
            while (index < types.size() && types.get(index) == type) {
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

    private static int averageHeight(int a, int b) {
        return (int) Math.round((a + b) / 2.0);
    }

    @FunctionalInterface
    public interface CanvasBlockPosResolver {
        BlockPos resolve(Vec2d canvasPos);
    }
}
