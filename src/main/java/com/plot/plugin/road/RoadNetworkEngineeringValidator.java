package com.plot.plugin.road;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.graph.RoadGraphQueries;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNetworkInvariantValidator;
import com.plot.plugin.road.model.RoadNetworkValidationResult;
import com.plot.plugin.road.model.RoadTopologyInvariantValidator;
import com.plot.plugin.road.model.RoadTopologyViolationKind;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineConsistency;
import com.plot.plugin.road.alignment.HorizontalAlignmentJunctionConsistency;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.plugin.road.vertical.VerticalAlignmentValidator;
import com.plot.plugin.road.vertical.VerticalAlignmentViolationKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 生成前工程检查：连通性、断头路、坡度 override、路口纵坡与拓扑。
 */
public final class RoadNetworkEngineeringValidator {
    private static final float GRADE_TOLERANCE_PERCENT = 0.05f;
    private static final double VERTICAL_ALIGNMENT_LENGTH_TOLERANCE = 1.0;
    private static final double HORIZONTAL_ALIGNMENT_LENGTH_TOLERANCE = 1.0;
    private static final double HORIZONTAL_ALIGNMENT_POINT_TOLERANCE = 1.0;
    private static final double VERTICAL_ALIGNMENT_GRADE_SAMPLE_SPACING = 5.0;

    private RoadNetworkEngineeringValidator() {
    }

    public static RoadNetworkValidationReport analyze(
            RoadNetwork network,
            Map<String, RoadGenerationResult> edgeResults,
            RoadSystemConfig config) {
        List<RoadNetworkValidationReport.Item> items = new ArrayList<>();
        if (network == null || network.getEdges().isEmpty()) {
            return new RoadNetworkValidationReport(items);
        }

        RoadGraphQueries queries = RoadGraphQueries.of(network);
        int roadCount = network.getRoads().size();
        List<RoadGraphQueries.GraphComponent> components = queries.connectedComponents();

        if (components.size() <= 1) {
            items.add(RoadNetworkValidationReport.Item.ok(
                "plugin.road.validation.roads_connected",
                roadCount));
        } else {
            RoadGraphQueries.GraphComponent largest = largestComponent(components);
            int mainRoadCount = countRoadsInComponent(network, largest);
            items.add(RoadNetworkValidationReport.Item.warning(
                "plugin.road.validation.roads_partially_connected",
                mainRoadCount,
                roadCount));
            items.add(RoadNetworkValidationReport.Item.warning(
                "plugin.road.validation.disconnected_components",
                components.size() - 1));
        }

        int deadEndCount = queries.deadEndNodes().size();
        if (deadEndCount == 0) {
            items.add(RoadNetworkValidationReport.Item.ok("plugin.road.validation.no_dead_ends"));
        } else {
            items.add(RoadNetworkValidationReport.Item.warning(
                "plugin.road.validation.dead_ends",
                deadEndCount));
        }

        int overlapCount = countSlopeOverrideOverlaps(network);
        if (overlapCount == 0) {
            items.add(RoadNetworkValidationReport.Item.ok("plugin.road.validation.no_slope_overlap"));
        } else {
            items.add(RoadNetworkValidationReport.Item.warning(
                "plugin.road.validation.slope_override_overlap",
                overlapCount));
        }

        addRoadTopologyItems(items, network, true);

        addVerticalAlignmentItems(items, network, config);

        addHorizontalAlignmentItems(items, network);

        int gradeJunctionCount = countJunctionsExceedingGrade(network, edgeResults, config);
        if (hasPreviewProfiles(edgeResults)) {
            if (gradeJunctionCount == 0) {
                items.add(RoadNetworkValidationReport.Item.ok("plugin.road.validation.junctions_within_grade"));
            } else {
                items.add(RoadNetworkValidationReport.Item.warning(
                    "plugin.road.validation.junctions_exceed_grade",
                    gradeJunctionCount));
            }
        }

        RoadNetworkValidationResult invariants = RoadNetworkInvariantValidator.validate(network);
        if (!invariants.valid()) {
            items.add(RoadNetworkValidationReport.Item.warning(
                "plugin.road.validation.topology_issues",
                invariants.violations().size()));
        }

        IntersectionProbeResult intersectionProbe =
            new RoadNetworkBuilder().probeIntersectionCompleteness(network);
        if (intersectionProbe.isIncomplete()) {
            items.add(RoadNetworkValidationReport.Item.error(
                "plugin.road.validation.intersections_incomplete"));
        } else if (intersectionProbe.hasPendingWork()) {
            items.add(RoadNetworkValidationReport.Item.warning(
                "plugin.road.validation.intersections_pending"));
        } else if (invariants.valid()) {
            items.add(RoadNetworkValidationReport.Item.ok("plugin.road.validation.intersections_resolved"));
        }

        return new RoadNetworkValidationReport(items);
    }

    /**
     * Overview 用轻量健康摘要：不做 snapshot / 求交探测，避免每帧卡死 UI。
     */
    public static RoadNetworkValidationReport analyzeOverviewHealth(
            RoadNetwork network,
            boolean intersectionRepairPending) {
        List<RoadNetworkValidationReport.Item> items = new ArrayList<>();
        if (network == null || network.getEdges().isEmpty()) {
            return new RoadNetworkValidationReport(items);
        }

        List<RoadGraphQueries.GraphComponent> components = RoadGraphQueries.of(network).connectedComponents();
        if (components.size() > 1) {
            items.add(RoadNetworkValidationReport.Item.warning(
                "plugin.road.validation.disconnected_components",
                components.size() - 1));
        }

        addRoadTopologyItems(items, network, false);

        RoadNetworkValidationResult invariants = RoadNetworkInvariantValidator.validate(network);
        if (!invariants.valid()) {
            items.add(RoadNetworkValidationReport.Item.warning(
                "plugin.road.validation.topology_issues",
                invariants.violations().size()));
        }

        if (intersectionRepairPending) {
            items.add(RoadNetworkValidationReport.Item.warning(
                "plugin.road.validation.intersections_pending"));
        }

        return new RoadNetworkValidationReport(items);
    }

    private static void addRoadTopologyItems(
            List<RoadNetworkValidationReport.Item> items,
            RoadNetwork network,
            boolean includeOkWhenClean) {
        Map<RoadTopologyViolationKind, Integer> counts = RoadTopologyInvariantValidator.countByKind(network);
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            if (includeOkWhenClean) {
                items.add(RoadNetworkValidationReport.Item.ok("plugin.road.validation.road_topology_ok"));
            }
            return;
        }
        addRoadTopologyWarningIfPositive(
            items, counts, RoadTopologyViolationKind.ROAD_DISCONNECTED,
            "plugin.road.validation.road_disconnected");
        addRoadTopologyWarningIfPositive(
            items, counts, RoadTopologyViolationKind.ROAD_BRANCHING,
            "plugin.road.validation.road_branching");
        addRoadTopologyWarningIfPositive(
            items, counts, RoadTopologyViolationKind.ROAD_CYCLE,
            "plugin.road.validation.road_cycle");
        addRoadTopologyWarningIfPositive(
            items, counts, RoadTopologyViolationKind.ROAD_ORDER_MISMATCH,
            "plugin.road.validation.road_order_mismatch");
    }

    private static void addRoadTopologyWarningIfPositive(
            List<RoadNetworkValidationReport.Item> items,
            Map<RoadTopologyViolationKind, Integer> counts,
            RoadTopologyViolationKind kind,
            String messageKey) {
        int count = counts.getOrDefault(kind, 0);
        if (count > 0) {
            items.add(RoadNetworkValidationReport.Item.warning(messageKey, count));
        }
    }

    private static void addHorizontalAlignmentItems(
            List<RoadNetworkValidationReport.Item> items,
            RoadNetwork network) {
        if (!hasHorizontalAlignmentRoads(network)) {
            return;
        }

        int lengthMismatchCount = countHorizontalAlignmentLengthMismatches(network);
        if (lengthMismatchCount == 0) {
            items.add(RoadNetworkValidationReport.Item.ok(
                "plugin.road.validation.horizontal_alignment_length_ok"));
        } else {
            items.add(RoadNetworkValidationReport.Item.warning(
                "plugin.road.validation.horizontal_alignment_length_mismatch",
                lengthMismatchCount));
        }

        int deviationCount = countHorizontalAlignmentCenterlineDeviations(network);
        if (deviationCount == 0) {
            items.add(RoadNetworkValidationReport.Item.ok(
                "plugin.road.validation.horizontal_alignment_centerline_ok"));
        } else {
            items.add(RoadNetworkValidationReport.Item.warning(
                "plugin.road.validation.horizontal_alignment_centerline_deviation",
                deviationCount));
        }

        int junctionConflictCount = countHorizontalAlignmentJunctionConflicts(network);
        if (junctionConflictCount == 0) {
            items.add(RoadNetworkValidationReport.Item.ok(
                "plugin.road.validation.horizontal_alignment_junction_ok"));
        } else {
            items.add(RoadNetworkValidationReport.Item.warning(
                "plugin.road.validation.horizontal_alignment_junction_conflict",
                junctionConflictCount));
        }
    }

    private static int countHorizontalAlignmentJunctionConflicts(RoadNetwork network) {
        int count = 0;
        for (Road road : network.getRoads().values()) {
            if (HorizontalAlignmentJunctionConsistency.hasConflicts(network, road)) {
                count++;
            }
        }
        return count;
    }

    private static int countHorizontalAlignmentLengthMismatches(RoadNetwork network) {
        int count = 0;
        for (Road road : network.getRoads().values()) {
            if (!HorizontalAlignmentCenterlineConsistency.isEvaluable(network, road)) {
                continue;
            }
            HorizontalAlignmentCenterlineConsistency.Report report =
                HorizontalAlignmentCenterlineConsistency.evaluate(network, road);
            if (!report.lengthMatches()) {
                count++;
            }
        }
        return count;
    }

    private static int countHorizontalAlignmentCenterlineDeviations(RoadNetwork network) {
        int count = 0;
        for (Road road : network.getRoads().values()) {
            if (!HorizontalAlignmentCenterlineConsistency.isEvaluable(network, road)) {
                continue;
            }
            HorizontalAlignmentCenterlineConsistency.Report report =
                HorizontalAlignmentCenterlineConsistency.evaluate(network, road);
            if (!report.isConsistent(
                    HORIZONTAL_ALIGNMENT_LENGTH_TOLERANCE,
                    HORIZONTAL_ALIGNMENT_POINT_TOLERANCE)) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasHorizontalAlignmentRoads(RoadNetwork network) {
        for (Road road : network.getRoads().values()) {
            RoadHorizontalAlignment alignment = road.getHorizontalAlignment();
            if (alignment != null && !alignment.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void addVerticalAlignmentItems(
            List<RoadNetworkValidationReport.Item> items,
            RoadNetwork network,
            RoadSystemConfig config) {
        if (!hasVerticalAlignmentRoads(network)) {
            return;
        }
        int lengthMismatchCount = countVerticalAlignmentLengthMismatches(network);
        if (lengthMismatchCount == 0) {
            items.add(RoadNetworkValidationReport.Item.ok(
                "plugin.road.validation.vertical_alignment_length_ok"));
        } else {
            items.add(RoadNetworkValidationReport.Item.warning(
                "plugin.road.validation.vertical_alignment_length_mismatch",
                lengthMismatchCount));
        }

        int gradeExceedCount = countVerticalAlignmentsExceedingGrade(network, config);
        if (gradeExceedCount == 0) {
            items.add(RoadNetworkValidationReport.Item.ok(
                "plugin.road.validation.vertical_alignment_grade_ok"));
        } else {
            items.add(RoadNetworkValidationReport.Item.warning(
                "plugin.road.validation.vertical_alignment_grade_exceeds",
                gradeExceedCount));
        }

        addVerticalAlignmentTopologyItems(items, network);
    }

    private static void addVerticalAlignmentTopologyItems(
            List<RoadNetworkValidationReport.Item> items,
            RoadNetwork network) {
        int curveOverlapCount = countVerticalAlignmentViolations(network, VerticalAlignmentViolationKind.VERTICAL_CURVE_OVERLAP);
        if (curveOverlapCount == 0) {
            items.add(RoadNetworkValidationReport.Item.ok(
                "plugin.road.validation.vertical_alignment_curve_ok"));
        } else {
            items.add(RoadNetworkValidationReport.Item.warning(
                "plugin.road.validation.vertical_alignment_curve_overlap",
                curveOverlapCount));
        }

        int curveRangeCount = countVerticalAlignmentViolations(network, VerticalAlignmentViolationKind.VERTICAL_CURVE_OUT_OF_RANGE);
        if (curveRangeCount == 0) {
            items.add(RoadNetworkValidationReport.Item.ok(
                "plugin.road.validation.vertical_alignment_curve_range_ok"));
        } else {
            items.add(RoadNetworkValidationReport.Item.warning(
                "plugin.road.validation.vertical_alignment_curve_out_of_range",
                curveRangeCount));
        }

        int stationOrderCount = countVerticalAlignmentViolations(network, VerticalAlignmentViolationKind.PVI_STATION_NOT_INCREASING)
            + countVerticalAlignmentViolations(network, VerticalAlignmentViolationKind.PVI_STATION_DUPLICATE);
        if (stationOrderCount == 0) {
            items.add(RoadNetworkValidationReport.Item.ok(
                "plugin.road.validation.vertical_alignment_station_order_ok"));
        } else {
            items.add(RoadNetworkValidationReport.Item.warning(
                "plugin.road.validation.vertical_alignment_station_order_invalid",
                stationOrderCount));
        }
    }

    private static int countVerticalAlignmentViolations(
            RoadNetwork network,
            VerticalAlignmentViolationKind kind) {
        int count = 0;
        for (Road road : network.getRoads().values()) {
            RoadVerticalAlignment alignment = road.getVerticalAlignment();
            if (alignment == null || alignment.isEmpty() || !RoadStationing.isStationable(network, road)) {
                continue;
            }
            double totalLength = RoadStationing.canonicalLength(network, road);
            boolean hasKind = VerticalAlignmentValidator.validate(alignment, totalLength).stream()
                .anyMatch(violation -> violation.kind() == kind);
            if (hasKind) {
                count++;
            }
        }
        return count;
    }

    private static int countVerticalAlignmentLengthMismatches(RoadNetwork network) {
        int count = 0;
        for (Road road : network.getRoads().values()) {
            RoadVerticalAlignment alignment = road.getVerticalAlignment();
            if (!VerticalAlignmentGeometry.isEvaluable(alignment)
                || !RoadStationing.isStationable(network, road)) {
                continue;
            }
            double totalLength = RoadStationing.canonicalLength(network, road);
            if (Math.abs(alignment.startStation()) > VERTICAL_ALIGNMENT_LENGTH_TOLERANCE
                || Math.abs(alignment.endStation() - totalLength) > VERTICAL_ALIGNMENT_LENGTH_TOLERANCE) {
                count++;
            }
        }
        return count;
    }

    private static int countVerticalAlignmentsExceedingGrade(RoadNetwork network, RoadSystemConfig config) {
        int count = 0;
        for (Road road : network.getRoads().values()) {
            RoadVerticalAlignment alignment = road.getVerticalAlignment();
            if (!VerticalAlignmentGeometry.isEvaluable(alignment)
                || !RoadStationing.isStationable(network, road)) {
                continue;
            }
            float limit = road.getEffectiveMaxSlope(config);
            double maxAbsGrade = 0.0;
            double end = alignment.endStation();
            for (double station = alignment.startStation();
                 station <= end + VERTICAL_ALIGNMENT_LENGTH_TOLERANCE;
                 station += VERTICAL_ALIGNMENT_GRADE_SAMPLE_SPACING) {
                maxAbsGrade = Math.max(
                    maxAbsGrade,
                    Math.abs(VerticalAlignmentGeometry.gradeAt(alignment, station).orElse(0.0)));
            }
            maxAbsGrade = Math.max(
                maxAbsGrade,
                Math.abs(VerticalAlignmentGeometry.gradeAt(alignment, end).orElse(0.0)));
            if (maxAbsGrade > limit + GRADE_TOLERANCE_PERCENT) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasVerticalAlignmentRoads(RoadNetwork network) {
        for (Road road : network.getRoads().values()) {
            if (VerticalAlignmentGeometry.isEvaluable(road.getVerticalAlignment())
                && RoadStationing.isStationable(network, road)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPreviewProfiles(Map<String, RoadGenerationResult> edgeResults) {
        if (edgeResults == null || edgeResults.isEmpty()) {
            return false;
        }
        for (RoadGenerationResult result : edgeResults.values()) {
            if (result != null && result.hasProfileData()) {
                return true;
            }
        }
        return false;
    }

    private static RoadGraphQueries.GraphComponent largestComponent(
            List<RoadGraphQueries.GraphComponent> components) {
        return components.stream()
            .max(Comparator.comparingInt(component -> component.edgeIds().size()))
            .orElse(new RoadGraphQueries.GraphComponent(List.of(), List.of()));
    }

    private static int countRoadsInComponent(RoadNetwork network, RoadGraphQueries.GraphComponent component) {
        Set<String> roadIds = new HashSet<>();
        for (String edgeId : component.edgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            if (edge != null && edge.getRoadId() != null && !edge.getRoadId().isBlank()) {
                roadIds.add(edge.getRoadId());
            }
        }
        return roadIds.size();
    }

    private static int countSlopeOverrideOverlaps(RoadNetwork network) {
        int count = 0;
        for (RoadEdge edge : network.getEdges().values()) {
            List<RoadEdge.SlopeOverride> overrides = edge.getSlopeOverrides();
            for (int i = 0; i < overrides.size(); i++) {
                if (RoadNetworkManager.hasOverlappingOverride(overrides, i)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private static int countJunctionsExceedingGrade(
            RoadNetwork network,
            Map<String, RoadGenerationResult> edgeResults,
            RoadSystemConfig config) {
        if (edgeResults == null || edgeResults.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (RoadNode junction : RoadGraphQueries.of(network).junctionNodes()) {
            if (junctionExceedsDesignGrade(junction, network, edgeResults, config)) {
                count++;
            }
        }
        return count;
    }

    private static boolean junctionExceedsDesignGrade(
            RoadNode junction,
            RoadNetwork network,
            Map<String, RoadGenerationResult> edgeResults,
            RoadSystemConfig config) {
        for (String edgeId : junction.getConnectedEdgeIds()) {
            RoadEdge edge = network.getEdge(edgeId);
            RoadGenerationResult result = edgeResults.get(edgeId);
            if (edge == null || result == null || !result.hasProfileData()) {
                continue;
            }
            float limit = maxSlopeForEdge(network, edge, config);
            float grade = gradePercentAtNode(edge, junction.getId(), result);
            if (grade > limit + GRADE_TOLERANCE_PERCENT) {
                return true;
            }
        }
        return false;
    }

    private static float maxSlopeForEdge(RoadNetwork network, RoadEdge edge, RoadSystemConfig config) {
        if (edge.getRoadId() != null) {
            Road road = network.getRoad(edge.getRoadId());
            if (road != null) {
                return road.getEffectiveMaxSlope(config);
            }
        }
        return config.getMaxSlope();
    }

    private static float gradePercentAtNode(RoadEdge edge, String nodeId, RoadGenerationResult result) {
        int sampleCount = result.profileDistances.size();
        if (sampleCount < 2) {
            return 0f;
        }
        boolean atStart = edge.getStartNodeId().equals(nodeId);
        int fromIndex = atStart ? 0 : sampleCount - 2;
        int toIndex = atStart ? 1 : sampleCount - 1;
        double distance = result.profileDistances.get(toIndex) - result.profileDistances.get(fromIndex);
        if (distance <= 1e-6) {
            return 0f;
        }
        int delta = result.profileTargetHeights.get(toIndex) - result.profileTargetHeights.get(fromIndex);
        return (float) (Math.abs(delta) / distance * 100.0);
    }
}
