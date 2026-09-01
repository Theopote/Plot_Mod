package com.plot.plugin.road.alignment;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

import java.util.List;

/**
 * 校验设计平面线形端点与共享路口 {@link com.plot.plugin.road.model.RoadNode} 位置是否一致。
 * <p>
 * 共享路口以 {@link com.plot.plugin.road.model.RoadNode#getPosition()} 为拓扑权威；HA 端点偏差超过容差时
 * 不得静默物化，应报告 {@link JunctionConflict}。
 */
public final class HorizontalAlignmentJunctionConsistency {

    private HorizontalAlignmentJunctionConsistency() {
    }

    public record JunctionConflict(
            String roadId,
            String edgeId,
            String nodeId,
            double deviationMeters) {
    }

    public static boolean hasConflicts(RoadNetwork network, Road road) {
        return !findConflicts(network, road).isEmpty();
    }

    public static List<JunctionConflict> findConflicts(RoadNetwork network, Road road) {
        return findConflicts(network, road, HorizontalAlignmentCenterlineMaterializer.DEFAULT_SAMPLE_SPACING_METERS);
    }

    public static List<JunctionConflict> findConflicts(
            RoadNetwork network,
            Road road,
            double sampleSpacingMeters) {
        if (!HorizontalAlignmentCenterlineConsistency.isEvaluable(network, road)) {
            return List.of();
        }

        return HorizontalAlignmentTopologyValidator.validate(network, road).stream()
            .filter(violation -> violation.kind() == HorizontalAlignmentViolationKind.ALIGNMENT_TOPOLOGY_MISMATCH)
            .filter(violation -> !HorizontalAlignmentCenterlineMaterializer.isNodeExclusiveToRoad(
                network,
                violation.nodeId(),
                road.getId()))
            .map(violation -> new JunctionConflict(
                violation.roadId(),
                edgeIdAtNode(network, road, violation.nodeId()),
                violation.nodeId(),
                violation.deviationMeters()))
            .toList();
    }

    private static String edgeIdAtNode(RoadNetwork network, Road road, String nodeId) {
        return RoadStationing.orientedSegments(network, road).stream()
            .filter(segment -> segment.entryNodeId().equals(nodeId) || segment.exitNodeId().equals(nodeId))
            .map(OrientedRoadSegment::edgeId)
            .findFirst()
            .orElse("");
    }
}
