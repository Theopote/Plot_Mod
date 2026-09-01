package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Projects road-level PVIs into a selected edge's unfolded longitudinal profile. */
public final class VerticalProfileControlPoints {
    private static final double EPSILON = 1e-6;

    public record ControlPoint(
            int pviIndex,
            double roadStation,
            double localDistance,
            double elevation,
            Double leftGradePercent,
            Double rightGradePercent,
            boolean endpoint,
            boolean sharedJunction) { }

    private VerticalProfileControlPoints() { }

    public static List<ControlPoint> forEdge(RoadNetwork network, Road road, RoadEdge edge) {
        if (network == null || road == null || edge == null || road.getVerticalAlignment() == null) {
            return List.of();
        }
        Optional<OrientedRoadSegment> oriented = RoadStationing.orientedSegment(network, road, edge.getId());
        if (oriented.isEmpty()) {
            return List.of();
        }
        List<PointOfVerticalIntersection> pvis = road.getVerticalAlignment().getPvis();
        List<ControlPoint> result = new ArrayList<>();
        for (int i = 0; i < pvis.size(); i++) {
            PointOfVerticalIntersection pvi = pvis.get(i);
            var local = oriented.get().geometryLocalAtRoadStation(pvi.getStation());
            if (local.isEmpty()) {
                continue;
            }
            Double left = i > 0
                ? VerticalAlignmentGeometry.tangentGradePercent(pvis.get(i - 1), pvi)
                : null;
            Double right = i + 1 < pvis.size()
                ? VerticalAlignmentGeometry.tangentGradePercent(pvi, pvis.get(i + 1))
                : null;
            result.add(new ControlPoint(
                i, pvi.getStation(), local.getAsDouble(), pvi.getElevation(), left, right,
                i == 0 || i == pvis.size() - 1,
                VerticalAlignmentJunctionSynchronizer.isSharedJunctionAtStation(
                    network, road, pvi.getStation())));
        }
        return List.copyOf(result);
    }

    /** Returns a copy with one PVI elevation changed, preserving station and curve length. */
    public static RoadVerticalAlignment withElevation(
            RoadVerticalAlignment source,
            int pviIndex,
            double elevation) {
        if (source == null || pviIndex < 0 || pviIndex >= source.pviCount()
                || !Double.isFinite(elevation)) {
            throw new IllegalArgumentException("invalid PVI edit");
        }
        List<PointOfVerticalIntersection> edited = new ArrayList<>();
        for (int i = 0; i < source.pviCount(); i++) {
            PointOfVerticalIntersection pvi = source.getPvis().get(i);
            edited.add(i == pviIndex
                ? new PointOfVerticalIntersection(
                    pvi.getStation(), elevation, pvi.getCurveLength(), pvi.getConstraint())
                : pvi.copy());
        }
        return new RoadVerticalAlignment(edited);
    }

    /** Moves one control point while preserving endpoint stations and minimum neighbor spacing. */
    public static RoadVerticalAlignment move(
            RoadVerticalAlignment source,
            int pviIndex,
            double requestedStation,
            double elevation,
            double roadLength) {
        if (source == null || pviIndex < 0 || pviIndex >= source.pviCount()
                || !Double.isFinite(requestedStation) || !Double.isFinite(elevation)) {
            throw new IllegalArgumentException("invalid PVI move");
        }
        if (!VerticalProfileDesignRules.slopeAllowed(roadLength)) {
            return VerticalProfileDesignRules.flatAlignment(roadLength, elevation);
        }
        List<PointOfVerticalIntersection> pvis = source.getPvis();
        double station;
        if (pviIndex == 0 || pviIndex == pvis.size() - 1) {
            station = pvis.get(pviIndex).getStation();
        } else {
            double minimum = pvis.get(pviIndex - 1).getStation()
                + VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH;
            double maximum = pvis.get(pviIndex + 1).getStation()
                - VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH;
            maximum = Math.min(maximum, roadLength);
            if (minimum > maximum) {
                station = pvis.get(pviIndex).getStation();
            } else {
                station = Math.max(minimum, Math.min(maximum, requestedStation));
            }
        }
        List<PointOfVerticalIntersection> edited = new ArrayList<>();
        for (int i = 0; i < pvis.size(); i++) {
            PointOfVerticalIntersection pvi = pvis.get(i);
            edited.add(i == pviIndex
                ? new PointOfVerticalIntersection(
                    station, elevation, pvi.getCurveLength(), pvi.getConstraint())
                : pvi.copy());
        }
        return new RoadVerticalAlignment(edited);
    }

    public static boolean exceedsGradeLimit(ControlPoint point, double maxGradePercent) {
        if (point == null || maxGradePercent <= EPSILON) {
            return false;
        }
        return point.leftGradePercent() != null
                && Math.abs(point.leftGradePercent()) > maxGradePercent + EPSILON
            || point.rightGradePercent() != null
                && Math.abs(point.rightGradePercent()) > maxGradePercent + EPSILON;
    }
}
