package com.plot.plugin.road.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.FlatRoadJunctionConflictResolver;
import com.plot.plugin.road.vertical.FlatVerticalIntentSupport;
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
        return applyCurrentRoadElevation(network, currentRoad, intersection, elevation, config);
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
            double requiredClearance = requiredClearance(node, config);
            elevation = clampOtherGradeSeparatedElevation(
                intersection.currentRoadElevation(),
                intersection.currentRoadElevated(),
                requiredClearance,
                elevation);
            return applyRoadElevationAtJunction(
                network, otherRoad, intersection.nodeId(), elevation, config);
        }
        node.setManualElevation(elevation);
        boolean changed = false;
        if (otherRoad.getVerticalMode() == RoadVerticalMode.FLAT
                || currentRoad.getVerticalMode() == RoadVerticalMode.FLAT) {
            changed |= FlatRoadJunctionConflictResolver.applySharedElevationAtJunction(
                network, intersection.nodeId(), elevation) > 0;
        }
        if (otherRoad.getVerticalMode() != RoadVerticalMode.FLAT) {
            changed |= syncAtGradeJunction(
                network, otherRoad, intersection.nodeId(), elevation, config);
        }
        if (currentRoad.getVerticalMode() != RoadVerticalMode.FLAT) {
            changed |= syncAtGradeJunction(
                network, currentRoad, intersection.nodeId(), elevation, config);
        }
        return changed;
    }

    private static boolean applyCurrentRoadElevation(
            RoadNetwork network,
            Road currentRoad,
            RoadProfileIntersection intersection,
            double elevation,
            RoadSystemConfig config) {
        RoadNode node = network.getNode(intersection.nodeId());
        if (node == null) {
            return false;
        }
        if (intersection.gradeSeparated()) {
            return applyRoadElevationAtJunction(
                network, currentRoad, intersection.nodeId(), elevation, config);
        }
        if (currentRoad.getVerticalMode() == RoadVerticalMode.FLAT) {
            return FlatRoadJunctionConflictResolver.applySharedElevationAtJunction(
                network, intersection.nodeId(), elevation) > 0;
        }
        node.setManualElevation(elevation);
        return syncAtGradeJunction(network, currentRoad, intersection.nodeId(), elevation, config);
    }

    static double clampOtherGradeSeparatedElevation(
            double currentRoadElevation,
            boolean currentRoadElevated,
            double requiredClearance,
            double requested) {
        if (currentRoadElevated) {
            double maxOther = currentRoadElevation - requiredClearance;
            return Math.min(requested, maxOther);
        }
        double minOther = currentRoadElevation + requiredClearance;
        return Math.max(requested, minOther);
    }

    public static double requiredClearance(RoadNode node, RoadSystemConfig config) {
        if (node != null && node.getCrossingClearance() != null) {
            return node.getCrossingClearance();
        }
        return config != null ? config.getDefaultCrossingClearance() : 4.0;
    }

    private static boolean applyRoadElevationAtJunction(
            RoadNetwork network,
            Road road,
            String nodeId,
            double elevation,
            RoadSystemConfig config) {
        if (road.getVerticalMode() == RoadVerticalMode.FLAT) {
            FlatVerticalIntentSupport.applyJunctionElevation(
                network,
                road,
                nodeId,
                elevation,
                road.getEffectiveMaxSlope(config));
            return true;
        }
        OptionalInt pviIndex = junctionPviIndex(network, road, nodeId);
        if (pviIndex.isEmpty() || road.getVerticalAlignment() == null) {
            return false;
        }
        road.setVerticalAlignment(VerticalProfileControlPoints.withElevation(
            road.getVerticalAlignment(), pviIndex.getAsInt(), elevation));
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        return true;
    }

    private static boolean syncAtGradeJunction(
            RoadNetwork network,
            Road road,
            String nodeId,
            double elevation,
            RoadSystemConfig config) {
        if (road.getVerticalMode() == RoadVerticalMode.FLAT) {
            FlatVerticalIntentSupport.applyJunctionElevation(
                network,
                road,
                nodeId,
                elevation,
                road.getEffectiveMaxSlope(config));
            return true;
        }
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
        if (network == null || road == null || nodeId == null) {
            return OptionalInt.empty();
        }
        if (road.getVerticalMode() == RoadVerticalMode.FLAT) {
            FlatVerticalIntentSupport.syncCompiledAlignment(
                network, road, road.getMaxSlope() != null ? road.getMaxSlope() : 8.0);
        }
        if (road.getVerticalAlignment() == null) {
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
