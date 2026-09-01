package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 校验 {@link RoadHorizontalAlignment} 与道路链拓扑节点的一致性。
 * <p>
 * 有 HA 的道路链上，每个分段界节点（链入口、内部分段界、链出口）须满足：
 * {@code node.position ≈ HA.poseAt(canonicalChainage)}。
 * <p>
 * HA 原点对应链起点桩号 0；HA 终点对应链终点桩号（设计总长）。
 */
public final class HorizontalAlignmentTopologyValidator {

    public static final double TOPOLOGY_TOLERANCE_METERS =
        HorizontalAlignmentCenterlineConsistency.DEFAULT_POINT_TOLERANCE_METERS;

    private HorizontalAlignmentTopologyValidator() {
    }

    public static boolean isAligned(RoadNetwork network, Road road) {
        return validate(network, road).isEmpty();
    }

    public static List<HorizontalAlignmentViolation> validate(RoadNetwork network, Road road) {
        return validate(network, road, TOPOLOGY_TOLERANCE_METERS);
    }

    public static List<HorizontalAlignmentViolation> validate(
            RoadNetwork network,
            Road road,
            double toleranceMeters) {
        if (!HorizontalAlignmentCenterlineConsistency.isEvaluable(network, road)) {
            return List.of();
        }

        RoadHorizontalAlignment alignment = road.getHorizontalAlignment();
        List<OrientedRoadSegment> segments = RoadStationing.orientedSegments(network, road);
        if (segments.isEmpty()) {
            return List.of();
        }

        List<HorizontalAlignmentViolation> violations = new ArrayList<>();
        Set<String> checkedNodes = new HashSet<>();

        OrientedRoadSegment first = segments.getFirst();
        checkBoundaryNode(
            network,
            road,
            alignment,
            first.entryNodeId(),
            RoadStationing.toCanonicalChainage(network, road, first.startStation()),
            toleranceMeters,
            checkedNodes,
            violations);

        for (OrientedRoadSegment segment : segments) {
            checkBoundaryNode(
                network,
                road,
                alignment,
                segment.exitNodeId(),
                RoadStationing.toCanonicalChainage(network, road, segment.endStation()),
                toleranceMeters,
                checkedNodes,
                violations);
        }

        return List.copyOf(violations);
    }

    private static void checkBoundaryNode(
            RoadNetwork network,
            Road road,
            RoadHorizontalAlignment alignment,
            String nodeId,
            double canonicalChainage,
            double toleranceMeters,
            Set<String> checkedNodes,
            List<HorizontalAlignmentViolation> violations) {
        if (nodeId == null || nodeId.isBlank() || !checkedNodes.add(nodeId)) {
            return;
        }

        RoadNode node = network.getNode(nodeId);
        if (node == null || node.getPosition() == null) {
            return;
        }

        Optional<AlignmentPose> pose = HorizontalAlignmentGeometry.poseAt(alignment, canonicalChainage);
        if (pose.isEmpty()) {
            violations.add(new HorizontalAlignmentViolation(
                road.getId(),
                HorizontalAlignmentViolationKind.ALIGNMENT_TOPOLOGY_MISMATCH,
                nodeId,
                canonicalChainage,
                Double.POSITIVE_INFINITY));
            return;
        }

        Vec2d expected = new Vec2d(pose.get().x(), pose.get().y());
        double deviation = node.getPosition().distance(expected);
        if (deviation > toleranceMeters) {
            violations.add(new HorizontalAlignmentViolation(
                road.getId(),
                HorizontalAlignmentViolationKind.ALIGNMENT_TOPOLOGY_MISMATCH,
                nodeId,
                canonicalChainage,
                deviation));
        }
    }
}
