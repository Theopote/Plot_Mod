package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;

import java.util.OptionalDouble;

/**
 * 根据已有设计纵断面判断交叉是平交还是立交。
 * 两路在交点处高差达到净空才标立交；没有纵断面或高差不足时保持平交。
 * <p>
 * 若交点处拓扑/UI 已标记立交，则优先保留该立交身份，避免 AT_GRADE 物化误清元数据。
 */
public final class CrossingGradeClassifier {
    private static final double EPSILON = 1e-9;
    private static final double NODE_MATCH_TOLERANCE = 0.5;

    private CrossingGradeClassifier() {
    }

    public static RoadCrossing classifyTwoRoad(
            RoadNetwork network,
            String roadAId,
            double stationA,
            String roadBId,
            double stationB,
            Vec2d position) {
        OptionalDouble elevationA = designedElevation(network, roadAId, stationA);
        OptionalDouble elevationB = designedElevation(network, roadBId, stationB);
        if (elevationA.isPresent() && elevationB.isPresent()) {
            double delta = elevationA.getAsDouble() - elevationB.getAsDouble();
            double separation = Math.abs(delta);
            String elevatedRoadId = delta > 0.0 ? roadAId : roadBId;
            String underpassRoadId = delta > 0.0 ? roadBId : roadAId;
            double required = GradeSeparationClearance.fromUnderpassRoad(
                network.getRoad(underpassRoadId), null);
            if (separation + EPSILON >= required) {
                // Use the designed vertical separation as underpass clearance so auto-detect
                // does not shrink a taller edited profile back to the tunnel clear-height default.
                return RoadCrossing.gradeSeparated(
                    roadAId, stationA, roadBId, stationB, position, elevatedRoadId, separation);
            }
        }
        RoadCrossing fromNode = classifyFromExistingGradeSeparatedNode(
            network, roadAId, stationA, roadBId, stationB, position);
        if (fromNode != null) {
            return fromNode;
        }
        return RoadCrossing.atGrade(roadAId, stationA, roadBId, stationB, position);
    }

    /**
     * Topology / UI grade-separation on a co-located junction is authoritative when profiles
     * do not already imply grade separation. Prevents AT_GRADE materialization from wiping it.
     */
    private static RoadCrossing classifyFromExistingGradeSeparatedNode(
            RoadNetwork network,
            String roadAId,
            double stationA,
            String roadBId,
            double stationB,
            Vec2d position) {
        if (network == null || position == null) {
            return null;
        }
        for (RoadNode node : network.getNodes().values()) {
            if (node == null || !node.isGradeSeparated() || node.getPosition() == null) {
                continue;
            }
            if (!RoadGeometryUtils.pointsNear(node.getPosition(), position, NODE_MATCH_TOLERANCE)) {
                continue;
            }
            String elevated = node.getElevatedRoadId();
            if (elevated != null && !elevated.isBlank()
                    && !elevated.equals(roadAId) && !elevated.equals(roadBId)) {
                continue;
            }
            Double clearance = node.getCrossingClearance();
            double resolvedClearance = clearance != null
                ? clearance
                : GradeSeparationClearance.fromUnderpassRoad(
                    network.getRoad(elevated != null && elevated.equals(roadAId) ? roadBId : roadAId),
                    null);
            return RoadCrossing.gradeSeparated(
                roadAId,
                stationA,
                roadBId,
                stationB,
                position,
                elevated,
                resolvedClearance);
        }
        return null;
    }

    public static RoadStackCrossing classifyStack(
            RoadNetwork network,
            String roadId,
            double stationA,
            double stationB,
            Vec2d position) {
        OptionalDouble elevationA = designedElevation(network, roadId, stationA);
        OptionalDouble elevationB = designedElevation(network, roadId, stationB);
        if (elevationA.isEmpty() || elevationB.isEmpty()) {
            return RoadStackCrossing.atGrade(roadId, stationA, stationB, position);
        }
        double delta = elevationA.getAsDouble() - elevationB.getAsDouble();
        double separation = Math.abs(delta);
        double required = GradeSeparationClearance.fromUnderpassRoad(
            network.getRoad(roadId), null);
        if (separation + EPSILON < required) {
            return RoadStackCrossing.atGrade(roadId, stationA, stationB, position);
        }
        double lowerStation = delta < 0.0 ? stationA : stationB;
        double upperStation = delta < 0.0 ? stationB : stationA;
        return RoadStackCrossing.gradeSeparated(
            roadId, lowerStation, upperStation, position, separation);
    }

    /** 两路在交点处设计高差是否达到下穿道路隧道净高。 */
    public static boolean profileSuggestsGradeSeparation(
            RoadNetwork network,
            String roadAId,
            double stationA,
            String roadBId,
            double stationB) {
        OptionalDouble elevationA = designedElevation(network, roadAId, stationA);
        OptionalDouble elevationB = designedElevation(network, roadBId, stationB);
        if (elevationA.isEmpty() || elevationB.isEmpty()) {
            return false;
        }
        double delta = elevationA.getAsDouble() - elevationB.getAsDouble();
        String underpassRoadId = delta > 0.0 ? roadBId : roadAId;
        double required = GradeSeparationClearance.fromUnderpassRoad(
            network.getRoad(underpassRoadId), null);
        return Math.abs(delta) + EPSILON >= required;
    }

    static OptionalDouble designedElevation(RoadNetwork network, String roadId, double station) {
        if (network == null || roadId == null) {
            return OptionalDouble.empty();
        }
        Road road = network.getRoad(roadId);
        if (road == null) {
            return OptionalDouble.empty();
        }
        return VerticalAlignmentGeometry.elevationAt(road.getVerticalAlignment(), station);
    }
}
