package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;

import java.util.OptionalDouble;

/**
 * 根据已有设计纵断面判断交叉是平交还是立交。
 * 两路在交点处高差达到净空才标立交；没有纵断面或高差不足时保持平交。
 */
public final class CrossingGradeClassifier {
    /** 与 {@code RoadSystemConfig} 默认交叉净空一致。 */
    public static final double DEFAULT_CLEARANCE = 3.0;
    private static final double EPSILON = 1e-9;

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
        if (elevationA.isEmpty() || elevationB.isEmpty()) {
            return RoadCrossing.atGrade(roadAId, stationA, roadBId, stationB, position);
        }
        double delta = elevationA.getAsDouble() - elevationB.getAsDouble();
        if (Math.abs(delta) + EPSILON < DEFAULT_CLEARANCE) {
            return RoadCrossing.atGrade(roadAId, stationA, roadBId, stationB, position);
        }
        String elevatedRoadId = delta > 0.0 ? roadAId : roadBId;
        return RoadCrossing.gradeSeparated(
            roadAId, stationA, roadBId, stationB, position, elevatedRoadId, DEFAULT_CLEARANCE);
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
        if (Math.abs(delta) + EPSILON < DEFAULT_CLEARANCE) {
            return RoadStackCrossing.atGrade(roadId, stationA, stationB, position);
        }
        double lowerStation = delta < 0.0 ? stationA : stationB;
        double upperStation = delta < 0.0 ? stationB : stationA;
        return RoadStackCrossing.gradeSeparated(
            roadId, lowerStation, upperStation, position, DEFAULT_CLEARANCE);
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
