package com.plot.plugin.road.profile;

import com.plot.plugin.road.RoadGradeSeparationAlternative;
import com.plot.plugin.road.RoadGradeSeparationEvaluation;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 为纵剖面交叉标记附加坡度超限警告（◇!）。
 */
public final class RoadProfileIntersectionWarningResolver {

    private RoadProfileIntersectionWarningResolver() {
    }

    public static List<RoadProfileIntersection> withSteepGradeWarnings(
            List<RoadProfileIntersection> intersections,
            RoadNetwork network,
            Function<String, RoadGradeSeparationEvaluation> evaluationByNodeId) {
        if (intersections == null || intersections.isEmpty() || network == null) {
            return intersections == null ? List.of() : intersections;
        }
        List<RoadProfileIntersection> enriched = new ArrayList<>(intersections.size());
        for (RoadProfileIntersection intersection : intersections) {
            RoadNode node = network.getNode(intersection.nodeId());
            RoadGradeSeparationEvaluation evaluation = evaluationByNodeId != null
                ? evaluationByNodeId.apply(intersection.nodeId())
                : null;
            enriched.add(withSteepGradeWarning(intersection, node, evaluation));
        }
        return List.copyOf(enriched);
    }

    public static boolean steepGradeWarning(RoadNode node, RoadGradeSeparationEvaluation evaluation) {
        if (node == null || evaluation == null || !node.isGradeSeparated()) {
            return false;
        }
        String elevatedRoadId = node.getElevatedRoadId();
        if (elevatedRoadId == null || elevatedRoadId.isBlank()) {
            elevatedRoadId = evaluation.recommendedElevatedRoadId();
        }
        if (elevatedRoadId == null || elevatedRoadId.isBlank()) {
            return false;
        }
        RoadGradeSeparationAlternative alternative = evaluation.alternativeFor(elevatedRoadId);
        return alternative != null && alternative.exceedsSlopeLimit();
    }

    private static RoadProfileIntersection withSteepGradeWarning(
            RoadProfileIntersection intersection,
            RoadNode node,
            RoadGradeSeparationEvaluation evaluation) {
        boolean warning = steepGradeWarning(node, evaluation);
        if (warning == intersection.steepGradeWarning()) {
            return intersection;
        }
        return new RoadProfileIntersection(
            intersection.nodeId(),
            intersection.currentRoadId(),
            intersection.otherRoadId(),
            intersection.otherRoadLabel(),
            intersection.localDistance(),
            intersection.roadStation(),
            intersection.currentRoadElevation(),
            intersection.otherRoadElevation(),
            intersection.otherCrossSection(),
            intersection.gradeSeparated(),
            intersection.currentRoadElevated(),
            intersection.clearance(),
            warning);
    }
}
