package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;

import java.util.UUID;

/**
 * 单条道路在平面上的自交叠点：同一路径两个不同桩号段在一点相遇。
 */
public record RoadStackCrossing(
        String id,
        String roadId,
        Vec2d position,
        double stationA,
        double stationB,
        CrossingType type,
        Double lowerStation,
        Double upperStation,
        Double crossingClearance,
        Double sharedElevation) {

    private static final double STATION_ORDER_EPSILON = 1e-6;

    public RoadStackCrossing {
        if (position != null) {
            position = position.copy();
        }
        if (type == null) {
            type = CrossingType.AT_GRADE;
        }
        double[] ordered = orderStations(stationA, stationB);
        stationA = ordered[0];
        stationB = ordered[1];
    }

    public static RoadStackCrossing atGrade(
            String roadId,
            double stationA,
            double stationB,
            Vec2d position) {
        return new RoadStackCrossing(
            UUID.randomUUID().toString(),
            roadId,
            position,
            stationA,
            stationB,
            CrossingType.AT_GRADE,
            null,
            null,
            null,
            null);
    }

    public static RoadStackCrossing gradeSeparated(
            String roadId,
            double lowerStation,
            double upperStation,
            Vec2d position,
            double clearance) {
        double[] ordered = orderStations(lowerStation, upperStation);
        return new RoadStackCrossing(
            UUID.randomUUID().toString(),
            roadId,
            position,
            ordered[0],
            ordered[1],
            CrossingType.GRADE_SEPARATED,
            lowerStation,
            upperStation,
            clearance,
            null);
    }

    /** 稳定匹配键：道路 + 两桩号 + 位置。 */
    public static String stableKey(
            String roadId,
            double stationA,
            double stationB,
            Vec2d position) {
        return stableKey(roadId, stationA, stationB, position, Double.NaN, false);
    }

    /**
     * Stable key with optional LOOP 0/L normalization so the same physical pass is not
     * registered twice at station 0 and L.
     */
    public static String stableKey(
            String roadId,
            double stationA,
            double stationB,
            Vec2d position,
            double loopLength,
            boolean normalizeLoop) {
        double a = normalizeLoop ? normalizeLoopStation(stationA, loopLength) : stationA;
        double b = normalizeLoop ? normalizeLoopStation(stationB, loopLength) : stationB;
        double[] ordered = orderStations(a, b);
        String posKey = position != null
            ? Math.round(position.x * 100.0) + "," + Math.round(position.y * 100.0)
            : "0,0";
        return roadId
            + "|" + Math.round(ordered[0] * 100.0)
            + "|" + Math.round(ordered[1] * 100.0)
            + "|" + posKey;
    }

    public String stableKey() {
        return stableKey(roadId, stationA, stationB, position);
    }

    public String stableKey(double loopLength, boolean normalizeLoop) {
        return stableKey(roadId, stationA, stationB, position, loopLength, normalizeLoop);
    }

    static double normalizeLoopStation(double station, double loopLength) {
        if (!Double.isFinite(station) || !Double.isFinite(loopLength) || loopLength <= STATION_ORDER_EPSILON) {
            return station;
        }
        double wrapped = station % loopLength;
        if (wrapped < 0.0) {
            wrapped += loopLength;
        }
        if (wrapped <= STATION_ORDER_EPSILON || Math.abs(wrapped - loopLength) <= STATION_ORDER_EPSILON) {
            return 0.0;
        }
        return wrapped;
    }

    public RoadStackCrossing withRefreshedGeometry(
            double newStationA,
            double newStationB,
            Vec2d newPosition) {
        // Preserve physical lower/upper pass identity — never re-derive from station sort order.
        boolean lowerWasA = Math.abs(resolvedLowerStation() - stationA)
            <= Math.abs(resolvedLowerStation() - stationB);
        Double newLower = lowerStation != null
            ? (lowerWasA ? newStationA : newStationB)
            : null;
        Double newUpper = upperStation != null
            ? (lowerWasA ? newStationB : newStationA)
            : null;
        return new RoadStackCrossing(
            id,
            roadId,
            newPosition,
            newStationA,
            newStationB,
            type,
            newLower,
            newUpper,
            crossingClearance,
            sharedElevation);
    }

    /**
     * Seam migration: rotate every station field independently so grade roles stay tied to
     * physical passes rather than post-rotate station ordering.
     */
    public RoadStackCrossing withRotatedStations(
            double newStationA,
            double newStationB,
            Double newLowerStation,
            Double newUpperStation) {
        return new RoadStackCrossing(
            id,
            roadId,
            position,
            newStationA,
            newStationB,
            type,
            newLowerStation,
            newUpperStation,
            crossingClearance,
            sharedElevation);
    }

    public RoadStackCrossing withSharedElevation(Double newSharedElevation) {
        return new RoadStackCrossing(
            id,
            roadId,
            position,
            stationA,
            stationB,
            type,
            lowerStation,
            upperStation,
            crossingClearance,
            newSharedElevation);
    }

    public boolean isGradeSeparated() {
        return type == CrossingType.GRADE_SEPARATED;
    }

    public double resolvedLowerStation() {
        if (lowerStation != null) {
            return lowerStation;
        }
        return stationA;
    }

    public double resolvedUpperStation() {
        if (upperStation != null) {
            return upperStation;
        }
        return stationB;
    }

    private static double[] orderStations(double a, double b) {
        if (a <= b + STATION_ORDER_EPSILON) {
            return new double[] {a, b};
        }
        return new double[] {b, a};
    }
}
