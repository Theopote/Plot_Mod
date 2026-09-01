package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.List;

/**
 * 校验设计平面线形端点与共享路口 {@link RoadNode} 位置是否一致。
 * <p>
 * 共享路口以 {@link RoadNode#getPosition()} 为拓扑权威；HA 采样端点偏差超过容差时
 * 不得静默物化，应报告 {@link JunctionConflict}。
 */
public final class HorizontalAlignmentJunctionConsistency {

    public static final double SHARED_JUNCTION_TOLERANCE_METERS =
        HorizontalAlignmentCenterlineConsistency.DEFAULT_POINT_TOLERANCE_METERS;

    private HorizontalAlignmentJunctionConsistency() {
    }

    public record JunctionConflict(
            String roadId,
            String edgeId,
            String nodeId,
            double deviationMeters) {
    }

    public static boolean hasConflicts(RoadNetwork network, Road road) {
        return !findConflicts(network, road, HorizontalAlignmentCenterlineMaterializer.DEFAULT_SAMPLE_SPACING_METERS)
            .isEmpty();
    }

    public static List<JunctionConflict> findConflicts(
            RoadNetwork network,
            Road road,
            double sampleSpacingMeters) {
        if (!HorizontalAlignmentCenterlineConsistency.isEvaluable(network, road)) {
            return List.of();
        }

        RoadHorizontalAlignment alignment = road.getHorizontalAlignment();
        List<JunctionConflict> conflicts = new ArrayList<>();
        for (OrientedRoadSegment oriented : RoadStationing.orientedSegments(network, road)) {
            List<Vec2d> geometryPoints = HorizontalAlignmentCenterlineMaterializer.sampleGeometryPoints(
                alignment,
                oriented,
                sampleSpacingMeters);
            if (geometryPoints.size() < 2) {
                continue;
            }
            collectEndpointConflict(network, road.getId(), oriented, geometryPoints, true, conflicts);
            collectEndpointConflict(network, road.getId(), oriented, geometryPoints, false, conflicts);
        }
        return List.copyOf(conflicts);
    }

    private static void collectEndpointConflict(
            RoadNetwork network,
            String roadId,
            OrientedRoadSegment oriented,
            List<Vec2d> geometryPoints,
            boolean chainEntry,
            List<JunctionConflict> conflicts) {
        String nodeId = chainEntry ? oriented.entryNodeId() : oriented.exitNodeId();
        if (HorizontalAlignmentCenterlineMaterializer.isNodeExclusiveToRoad(network, nodeId, roadId)) {
            return;
        }

        RoadNode node = network.getNode(nodeId);
        if (node == null || node.getPosition() == null) {
            return;
        }

        Vec2d sampled = chainEndpoint(oriented, geometryPoints, chainEntry);
        double deviation = sampled.distance(node.getPosition());
        if (deviation > SHARED_JUNCTION_TOLERANCE_METERS) {
            conflicts.add(new JunctionConflict(roadId, oriented.edgeId(), nodeId, deviation));
        }
    }

    private static Vec2d chainEndpoint(OrientedRoadSegment oriented, List<Vec2d> geometryPoints, boolean entry) {
        if (oriented.forward()) {
            return entry ? geometryPoints.getFirst() : geometryPoints.getLast();
        }
        return entry ? geometryPoints.getLast() : geometryPoints.getFirst();
    }
}
