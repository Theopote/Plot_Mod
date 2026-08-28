package com.plot.plugin.road;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.graph.RoadGraphQueries;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNetworkInvariantValidator;
import com.plot.plugin.road.model.RoadNetworkValidationResult;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;

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
