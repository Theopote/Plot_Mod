package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;

import java.util.UUID;

/**
 * 两条道路的平面交叉关系（几何相交，不写入拓扑节点共享）。
 */
public record RoadCrossing(
        String id,
        String roadAId,
        double stationA,
        String roadBId,
        double stationB,
        Vec2d position,
        CrossingType type,
        String elevatedRoadId,
        Double crossingClearance,
        Double sharedElevation) {

    public RoadCrossing {
        if (position != null) {
            position = position.copy();
        }
        if (type == null) {
            type = CrossingType.AT_GRADE;
        }
    }

    public static RoadCrossing atGrade(
            String roadAId,
            double stationA,
            String roadBId,
            double stationB,
            Vec2d position) {
        return new RoadCrossing(
            UUID.randomUUID().toString(),
            roadAId,
            stationA,
            roadBId,
            stationB,
            position,
            CrossingType.AT_GRADE,
            null,
            null,
            null);
    }

    public boolean involvesRoad(String roadId) {
        return roadAId.equals(roadId) || roadBId.equals(roadId);
    }

    public String otherRoadId(String roadId) {
        if (roadAId.equals(roadId)) {
            return roadBId;
        }
        if (roadBId.equals(roadId)) {
            return roadAId;
        }
        return null;
    }

    public double stationOn(String roadId) {
        if (roadAId.equals(roadId)) {
            return stationA;
        }
        if (roadBId.equals(roadId)) {
            return stationB;
        }
        throw new IllegalArgumentException("road not part of crossing: " + roadId);
    }
}
