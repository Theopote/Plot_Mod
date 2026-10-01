package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

/** 道路级垂直意图在拆分 / 拓扑修复时的复制与裁剪。 */
public final class RoadVerticalIntentTransforms {

    private static final double EPSILON = 1e-6;

    private RoadVerticalIntentTransforms() {
    }

    /**
     * 复制父路的垂直策略与水平意图；不复制沿桩号工程数据（由 {@link com.plot.plugin.road.station.RoadStationDataTransforms} 处理）。
     */
    public static void copyIntentFrom(Road target, Road source) {
        if (target == null || source == null) {
            return;
        }
        RoadVerticalMode stored = source.getStoredVerticalMode();
        if (stored != null) {
            target.setVerticalMode(stored);
        } else {
            RoadVerticalMode effective = source.getVerticalMode();
            if (effective != RoadVerticalMode.AUTO_SMOOTH) {
                target.setVerticalMode(effective);
            }
        }
        if (source.getFlatVerticalIntent() != null) {
            target.setFlatVerticalIntent(source.getFlatVerticalIntent());
        }
    }

    /**
     * 在 {@code splitStation} 处拆分水平意图的交叉口 override（base elevation 两侧继承）。
     */
    public static void partitionFlatIntentOnRoadSplit(
            RoadNetwork network,
            Road head,
            Road tail,
            double splitStation) {
        if (network == null || head == null || tail == null) {
            return;
        }
        if (head.getFlatVerticalIntent() == null && tail.getFlatVerticalIntent() == null) {
            return;
        }
        retainFlatOverridesForSplitSide(network, head, head, splitStation, true);
        retainFlatOverridesForSplitSide(network, head, tail, splitStation, false);
    }

    /** 从原始 intent 复制并仅保留属于 {@code edgeIds} 分量的节点 override。 */
    public static FlatVerticalIntent copyFlatIntentForEdges(
            FlatVerticalIntent source,
            RoadNetwork network,
            Set<String> edgeIds) {
        if (source == null || network == null || edgeIds == null) {
            return null;
        }
        FlatVerticalIntent copy = source.copy();
        Set<String> nodeIds = collectNodeIds(network, edgeIds);
        for (String nodeId : List.copyOf(copy.getIntersectionOverrides().keySet())) {
            if (!nodeIds.contains(nodeId)) {
                copy.removeOverride(nodeId);
            }
        }
        return copy;
    }

    /**
     * 将捕获的垂直意图应用到道路，并按分量边集裁剪 flat overrides。
     *
     * @see VerticalIntentSnapshot
     */
    public static void applyCapturedIntent(
            Road target,
            RoadVerticalMode capturedMode,
            FlatVerticalIntent capturedFlatIntent,
            RoadNetwork network,
            Set<String> edgeIds) {
        if (target == null) {
            return;
        }
        if (capturedMode != null) {
            target.setVerticalMode(capturedMode);
        }
        if (capturedFlatIntent == null) {
            target.setFlatVerticalIntent(null);
            return;
        }
        target.setFlatVerticalIntent(copyFlatIntentForEdges(capturedFlatIntent, network, edgeIds));
    }

    private static void retainFlatOverridesForSplitSide(
            RoadNetwork network,
            Road stationRoad,
            Road intentRoad,
            double splitStation,
            boolean headSide) {
        FlatVerticalIntent intent = intentRoad.getFlatVerticalIntent();
        if (intent == null) {
            return;
        }
        for (String nodeId : List.copyOf(intent.getIntersectionOverrides().keySet())) {
            OptionalDouble station = stationAtNode(network, stationRoad, nodeId);
            if (station.isEmpty()) {
                intent.removeOverride(nodeId);
                continue;
            }
            double nodeStation = station.getAsDouble();
            boolean keep;
            if (Math.abs(nodeStation - splitStation) <= EPSILON) {
                keep = true;
            } else {
                keep = headSide ? nodeStation < splitStation : nodeStation > splitStation;
            }
            if (!keep) {
                intent.removeOverride(nodeId);
            }
        }
    }

    private static Set<String> collectNodeIds(RoadNetwork network, Set<String> edgeIds) {
        Set<String> nodeIds = new HashSet<>();
        for (String edgeId : edgeIds) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge == null) {
                continue;
            }
            nodeIds.add(edge.getStartNodeId());
            nodeIds.add(edge.getEndNodeId());
        }
        return nodeIds;
    }

    private static OptionalDouble stationAtNode(RoadNetwork network, Road road, String nodeId) {
        for (OrientedRoadSegment segment : RoadStationing.orientedSegments(network, road)) {
            OptionalDouble station = segment.roadStationAtNode(nodeId);
            if (station.isPresent()) {
                return station;
            }
        }
        return OptionalDouble.empty();
    }
}
