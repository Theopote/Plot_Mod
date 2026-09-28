package com.plot.plugin.road.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.VerticalAlignmentJunctionSynchronizer;
import com.plot.plugin.road.vertical.VerticalProfileControlPoints;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** 纵剖面编辑器内拖动交叉点标记时，写回目标道路 / 节点标高。 */
public final class RoadProfileIntersectionDragEditor {

    private static final double STATION_TOLERANCE = 0.26;

    public enum DragTarget {
        CURRENT,
        OTHER
    }

    private RoadProfileIntersectionDragEditor() {
    }

    public static boolean applyDraggedElevation(
            RoadNetwork network,
            Road currentRoad,
            RoadProfileIntersection intersection,
            DragTarget target,
            double requestedElevation,
            RoadSystemConfig config) {
        if (network == null || currentRoad == null || intersection == null || config == null) {
            return false;
        }
        double elevation = RoadParameterLimits.clampManualElevation(requestedElevation);
        if (target == DragTarget.OTHER) {
            return applyOtherRoadElevation(network, currentRoad, intersection, elevation, config);
        }
        return applyCurrentRoadElevation(network, currentRoad, intersection, elevation);
    }

    private static boolean applyOtherRoadElevation(
            RoadNetwork network,
            Road currentRoad,
            RoadProfileIntersection intersection,
            double elevation,
            RoadSystemConfig config) {
        Road otherRoad = network.getRoad(intersection.otherRoadId());
        RoadNode node = network.getNode(intersection.nodeId());
        if (otherRoad == null || node == null) {
            return false;
        }
        if (intersection.gradeSeparated()) {
            elevation = clampOtherGradeSeparatedElevation(intersection, elevation, config);
            OptionalInt pviIndex = junctionPviIndex(network, otherRoad, intersection.nodeId());
            if (pviIndex.isEmpty() || otherRoad.getVerticalAlignment() == null) {
                return false;
            }
            otherRoad.setVerticalAlignment(VerticalProfileControlPoints.withElevation(
                otherRoad.getVerticalAlignment(), pviIndex.getAsInt(), elevation));
            otherRoad.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
            updateCrossingClearance(node, intersection, elevation);
            return true;
        }
        node.setManualElevation(elevation);
        syncAtGradeJunction(network, otherRoad, intersection.nodeId(), elevation);
        syncAtGradeJunction(network, currentRoad, intersection.nodeId(), elevation);
        return true;
    }

    private static boolean applyCurrentRoadElevation(
            RoadNetwork network,
            Road currentRoad,
            RoadProfileIntersection intersection,
            double elevation) {
        RoadNode node = network.getNode(intersection.nodeId());
        if (node == null) {
            return false;
        }
        if (intersection.gradeSeparated()) {
            OptionalInt pviIndex = junctionPviIndex(network, currentRoad, intersection.nodeId());
            if (pviIndex.isEmpty() || currentRoad.getVerticalAlignment() == null) {
                return false;
            }
            currentRoad.setVerticalAlignment(VerticalProfileControlPoints.withElevation(
                currentRoad.getVerticalAlignment(), pviIndex.getAsInt(), elevation));
            currentRoad.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
            return true;
        }
        node.setManualElevation(elevation);
        return syncAtGradeJunction(network, currentRoad, intersection.nodeId(), elevation);
    }

    static double clampOtherGradeSeparatedElevation(
            RoadProfileIntersection intersection,
            double requested,
            RoadSystemConfig config) {
        double minClearance = intersection.clearance() > 0
            ? intersection.clearance()
            : config.getDefaultCrossingClearance();
        double current = intersection.currentRoadElevation();
        if (intersection.currentRoadElevated()) {
            double maxOther = current - minClearance;
            return Math.min(requested, maxOther);
        }
        double minOther = current + minClearance;
        return Math.max(requested, minOther);
    }

    private static void updateCrossingClearance(
            RoadNode node,
            RoadProfileIntersection intersection,
            double otherElevation) {
        double gap = Math.abs(intersection.currentRoadElevation() - otherElevation);
        node.setCrossingClearance(Math.max(1.0, Math.round(gap)));
    }

    private static boolean syncAtGradeJunction(
            RoadNetwork network,
            Road road,
            String nodeId,
            double elevation) {
        OptionalInt pviIndex = junctionPviIndex(network, road, nodeId);
        if (pviIndex.isEmpty() || road.getVerticalAlignment() == null) {
            return false;
        }
        road.setVerticalAlignment(VerticalProfileControlPoints.withElevation(
            road.getVerticalAlignment(), pviIndex.getAsInt(), elevation));
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        VerticalAlignmentJunctionSynchronizer.applySharedJunctionConstraints(network, road);
        return true;
    }

    static OptionalInt junctionPviIndex(RoadNetwork network, Road road, String nodeId) {
        if (network == null || road == null || nodeId == null || road.getVerticalAlignment() == null) {
            return OptionalInt.empty();
        }
        OptionalDouble station = stationAtNode(network, road, nodeId);
        if (station.isEmpty()) {
            return OptionalInt.empty();
        }
        int index = matchingPviIndex(road.getVerticalAlignment().getPvis(), station.getAsDouble());
        return index >= 0 ? OptionalInt.of(index) : OptionalInt.empty();
    }

    private static int matchingPviIndex(List<PointOfVerticalIntersection> pvis, double station) {
        for (int i = 0; i < pvis.size(); i++) {
            if (Math.abs(pvis.get(i).getStation() - station) <= STATION_TOLERANCE) {
                return i;
            }
        }
        return -1;
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
