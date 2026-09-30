package com.plot.plugin.road.profile;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadCrossingRef;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.vertical.RoadVerticalJunctionService;

/** 纵剖面编辑器内拖动交叉点标记时，写回目标道路 / Crossing 标高。 */
public final class RoadProfileIntersectionDragEditor {

    public enum DragTarget {
        CURRENT,
        OTHER,
        SHARED
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
        if (RoadCrossingRef.isCrossingRef(intersection.nodeId())) {
            return applyRegistryCrossingDrag(network, currentRoad, intersection, target, elevation, config);
        }
        if (target == DragTarget.SHARED) {
            return applySharedElevation(network, intersection, elevation, config);
        }
        if (target == DragTarget.OTHER) {
            return applyOtherRoadElevation(network, currentRoad, intersection, elevation, config);
        }
        return applyCurrentRoadElevation(network, currentRoad, intersection, elevation, config);
    }

    private static boolean applyRegistryCrossingDrag(
            RoadNetwork network,
            Road currentRoad,
            RoadProfileIntersection intersection,
            DragTarget target,
            double elevation,
            RoadSystemConfig config) {
        RoadCrossing crossing = network.getCrossing(RoadCrossingRef.crossingIdFromRef(intersection.nodeId()));
        if (crossing == null) {
            return false;
        }
        if (target == DragTarget.SHARED) {
            return RoadVerticalJunctionService.setAtGradeSharedElevation(network, crossing, elevation, config) > 0;
        }
        if (target == DragTarget.OTHER) {
            Road otherRoad = network.getRoad(intersection.otherRoadId());
            if (otherRoad == null) {
                return false;
            }
            if (intersection.gradeSeparated()) {
                double requiredClearance = requiredClearance(crossing, config);
                elevation = clampOtherGradeSeparatedElevation(
                    intersection.currentRoadElevation(),
                    intersection.currentRoadElevated(),
                    requiredClearance,
                    elevation);
            }
            return RoadVerticalJunctionService.setRoadElevationAtRegistryCrossing(
                network, otherRoad, crossing, elevation, config);
        }
        if (intersection.gradeSeparated()) {
            return RoadVerticalJunctionService.setRoadElevationAtRegistryCrossing(
                network, currentRoad, crossing, elevation, config);
        }
        return RoadVerticalJunctionService.setAtGradeSharedElevation(network, crossing, elevation, config) > 0;
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
            return RoadVerticalJunctionService.setRoadElevationAtCrossing(
                network, otherRoad, intersection.nodeId(), elevation, config);
        }
        return RoadVerticalJunctionService.setAtGradeSharedElevation(
            network, intersection.nodeId(), elevation, config) > 0;
    }

    private static boolean applySharedElevation(
            RoadNetwork network,
            RoadProfileIntersection intersection,
            double elevation,
            RoadSystemConfig config) {
        RoadNode node = network.getNode(intersection.nodeId());
        if (node == null) {
            return false;
        }
        return RoadVerticalJunctionService.setAtGradeSharedElevation(
            network, intersection.nodeId(), elevation, config) > 0;
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
            return RoadVerticalJunctionService.setRoadElevationAtCrossing(
                network, currentRoad, intersection.nodeId(), elevation, config);
        }
        return RoadVerticalJunctionService.setAtGradeSharedElevation(
            network, intersection.nodeId(), elevation, config) > 0;
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

    public static double requiredClearance(RoadCrossing crossing, RoadSystemConfig config) {
        if (crossing != null && crossing.crossingClearance() != null) {
            return crossing.crossingClearance();
        }
        return config != null ? config.getDefaultCrossingClearance() : 4.0;
    }
}
