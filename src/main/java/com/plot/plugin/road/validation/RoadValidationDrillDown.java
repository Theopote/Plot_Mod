package com.plot.plugin.road.validation;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineConsistency;
import com.plot.plugin.road.alignment.HorizontalAlignmentJunctionConsistency;
import com.plot.plugin.road.alignment.HorizontalAlignmentTopologyValidator;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadTopologyInvariantValidator;
import com.plot.plugin.road.model.RoadTopologyViolation;
import com.plot.plugin.road.model.RoadTopologyViolationKind;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.plugin.road.vertical.VerticalAlignmentValidator;
import com.plot.plugin.road.vertical.VerticalAlignmentViolationKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 将聚合校验项解析为受影响的道路 ID，供 UI drill-down 使用。
 */
public final class RoadValidationDrillDown {

    private static final double VERTICAL_ALIGNMENT_LENGTH_TOLERANCE = 1.0;
    private static final double VERTICAL_ALIGNMENT_GRADE_SAMPLE_SPACING = 5.0;
    private static final float GRADE_TOLERANCE_PERCENT = 0.05f;

    private RoadValidationDrillDown() {
    }

    public static boolean supports(String issueId) {
        return issueId != null && switch (issueId) {
            case "road_disconnected",
                 "road_branching",
                 "road_cycle",
                 "road_order_mismatch",
                 "topology_issues",
                 "vertical_grade_exceeds",
                 "vertical_length_mismatch",
                 "vertical_curve_overlap",
                 "vertical_curve_out_of_range",
                 "vertical_pvi_order_invalid",
                 "horizontal_length_mismatch",
                 "horizontal_centerline_deviation",
                 "junction_endpoint_conflict",
                 "alignment_topology_mismatch",
                 "slope_override_overlap" -> true;
            default -> false;
        };
    }

    public static List<String> affectedRoadIds(
            String issueId,
            RoadNetwork network,
            RoadSystemConfig config) {
        if (issueId == null || network == null) {
            return List.of();
        }
        return switch (issueId) {
            case "road_disconnected" -> roadsWithTopologyKind(network, RoadTopologyViolationKind.ROAD_DISCONNECTED);
            case "road_branching" -> roadsWithTopologyKind(network, RoadTopologyViolationKind.ROAD_BRANCHING);
            case "road_cycle" -> roadsWithTopologyKind(network, RoadTopologyViolationKind.ROAD_CYCLE);
            case "road_order_mismatch" -> roadsWithTopologyKind(network, RoadTopologyViolationKind.ROAD_ORDER_MISMATCH);
            case "topology_issues" -> allTopologyRoadIds(network);
            case "vertical_grade_exceeds" -> roadsExceedingGrade(network, config);
            case "vertical_length_mismatch" -> roadsWithVerticalLengthMismatch(network);
            case "vertical_curve_overlap" -> roadsWithVerticalViolation(
                network, VerticalAlignmentViolationKind.VERTICAL_CURVE_OVERLAP);
            case "vertical_curve_out_of_range" -> roadsWithVerticalViolation(
                network, VerticalAlignmentViolationKind.VERTICAL_CURVE_OUT_OF_RANGE);
            case "vertical_pvi_order_invalid" -> roadsWithVerticalStationOrderIssues(network);
            case "horizontal_length_mismatch" -> roadsWithHorizontalLengthMismatch(network);
            case "horizontal_centerline_deviation" -> roadsWithHorizontalCenterlineDeviation(network);
            case "junction_endpoint_conflict" -> roadsWithHorizontalJunctionConflicts(network);
            case "alignment_topology_mismatch" -> roadsWithHorizontalTopologyMismatch(network);
            case "slope_override_overlap" -> roadsWithSlopeOverrideOverlap(network);
            default -> List.of();
        };
    }

    private static List<String> roadsWithTopologyKind(RoadNetwork network, RoadTopologyViolationKind kind) {
        LinkedHashSet<String> roadIds = new LinkedHashSet<>();
        for (RoadTopologyViolation violation : RoadTopologyInvariantValidator.validate(network)) {
            if (violation.kind() == kind) {
                roadIds.add(violation.roadId());
            }
        }
        return sortedRoadIds(network, roadIds);
    }

    private static List<String> allTopologyRoadIds(RoadNetwork network) {
        LinkedHashSet<String> roadIds = new LinkedHashSet<>();
        for (RoadTopologyViolation violation : RoadTopologyInvariantValidator.validate(network)) {
            roadIds.add(violation.roadId());
        }
        return sortedRoadIds(network, roadIds);
    }

    private static List<String> roadsExceedingGrade(RoadNetwork network, RoadSystemConfig config) {
        if (config == null) {
            return List.of();
        }
        LinkedHashSet<String> roadIds = new LinkedHashSet<>();
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
                roadIds.add(road.getId());
            }
        }
        return sortedRoadIds(network, roadIds);
    }

    private static List<String> roadsWithVerticalLengthMismatch(RoadNetwork network) {
        LinkedHashSet<String> roadIds = new LinkedHashSet<>();
        for (Road road : network.getRoads().values()) {
            RoadVerticalAlignment alignment = road.getVerticalAlignment();
            if (!VerticalAlignmentGeometry.isEvaluable(alignment)
                || !RoadStationing.isStationable(network, road)) {
                continue;
            }
            double totalLength = RoadStationing.canonicalLength(network, road);
            if (Math.abs(alignment.startStation()) > VERTICAL_ALIGNMENT_LENGTH_TOLERANCE
                || Math.abs(alignment.endStation() - totalLength) > VERTICAL_ALIGNMENT_LENGTH_TOLERANCE) {
                roadIds.add(road.getId());
            }
        }
        return sortedRoadIds(network, roadIds);
    }

    private static List<String> roadsWithVerticalViolation(
            RoadNetwork network,
            VerticalAlignmentViolationKind kind) {
        LinkedHashSet<String> roadIds = new LinkedHashSet<>();
        for (Road road : network.getRoads().values()) {
            RoadVerticalAlignment alignment = road.getVerticalAlignment();
            if (alignment == null || alignment.isEmpty() || !RoadStationing.isStationable(network, road)) {
                continue;
            }
            double totalLength = RoadStationing.canonicalLength(network, road);
            boolean hasKind = VerticalAlignmentValidator.validate(alignment, totalLength).stream()
                .anyMatch(violation -> violation.kind() == kind);
            if (hasKind) {
                roadIds.add(road.getId());
            }
        }
        return sortedRoadIds(network, roadIds);
    }

    private static List<String> roadsWithVerticalStationOrderIssues(RoadNetwork network) {
        LinkedHashSet<String> roadIds = new LinkedHashSet<>();
        roadIds.addAll(roadsWithVerticalViolation(network, VerticalAlignmentViolationKind.PVI_STATION_NOT_INCREASING));
        roadIds.addAll(roadsWithVerticalViolation(network, VerticalAlignmentViolationKind.PVI_STATION_DUPLICATE));
        return sortedRoadIds(network, roadIds);
    }

    private static List<String> roadsWithHorizontalLengthMismatch(RoadNetwork network) {
        LinkedHashSet<String> roadIds = new LinkedHashSet<>();
        for (Road road : network.getRoads().values()) {
            if (!HorizontalAlignmentCenterlineConsistency.isEvaluable(network, road)) {
                continue;
            }
            HorizontalAlignmentCenterlineConsistency.Report report =
                HorizontalAlignmentCenterlineConsistency.evaluate(network, road);
            if (!report.lengthMatches()) {
                roadIds.add(road.getId());
            }
        }
        return sortedRoadIds(network, roadIds);
    }

    private static List<String> roadsWithHorizontalCenterlineDeviation(RoadNetwork network) {
        LinkedHashSet<String> roadIds = new LinkedHashSet<>();
        for (Road road : network.getRoads().values()) {
            if (!HorizontalAlignmentCenterlineConsistency.isEvaluable(network, road)) {
                continue;
            }
            HorizontalAlignmentCenterlineConsistency.Report report =
                HorizontalAlignmentCenterlineConsistency.evaluate(network, road);
            if (!report.isConsistent(1.0, 1.0)) {
                roadIds.add(road.getId());
            }
        }
        return sortedRoadIds(network, roadIds);
    }

    private static List<String> roadsWithHorizontalJunctionConflicts(RoadNetwork network) {
        LinkedHashSet<String> roadIds = new LinkedHashSet<>();
        for (Road road : network.getRoads().values()) {
            if (HorizontalAlignmentJunctionConsistency.hasConflicts(network, road)) {
                roadIds.add(road.getId());
            }
        }
        return sortedRoadIds(network, roadIds);
    }

    private static List<String> roadsWithHorizontalTopologyMismatch(RoadNetwork network) {
        LinkedHashSet<String> roadIds = new LinkedHashSet<>();
        for (Road road : network.getRoads().values()) {
            if (!HorizontalAlignmentTopologyValidator.validate(network, road).isEmpty()) {
                roadIds.add(road.getId());
            }
        }
        return sortedRoadIds(network, roadIds);
    }

    private static List<String> roadsWithSlopeOverrideOverlap(RoadNetwork network) {
        LinkedHashSet<String> roadIds = new LinkedHashSet<>();
        for (RoadEdge edge : network.getEdges().values()) {
            List<RoadEdge.SlopeOverride> overrides = edge.getSlopeOverrides();
            for (int i = 0; i < overrides.size(); i++) {
                if (RoadNetworkManager.hasOverlappingOverride(overrides, i)) {
                    if (edge.getRoadId() != null && !edge.getRoadId().isBlank()) {
                        roadIds.add(edge.getRoadId());
                    }
                    break;
                }
            }
        }
        return sortedRoadIds(network, roadIds);
    }

    private static List<String> sortedRoadIds(RoadNetwork network, Set<String> roadIds) {
        List<String> sorted = new ArrayList<>(roadIds);
        sorted.sort(Comparator.comparing(id -> {
            Road road = network.getRoad(id);
            if (road == null) {
                return id;
            }
            String name = road.getName();
            if (name != null && !name.isBlank()) {
                return name;
            }
            return road.getId();
        }));
        return List.copyOf(sorted);
    }
}
