package com.plot.plugin.road.repair;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.IntersectionProbeResult;
import com.plot.plugin.road.IntersectionResult;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.alignment.CenterlineHorizontalAlignmentSync;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineConsistency;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineMaterializer;
import com.plot.plugin.road.alignment.HorizontalAlignmentJunctionConsistency;
import com.plot.plugin.road.alignment.HorizontalAlignmentTopologyValidator;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadTopologyInvariantValidator;
import com.plot.plugin.road.model.RoadTopologyRoadSplitter;
import com.plot.plugin.road.model.RoadTopologyViolation;
import com.plot.plugin.road.model.RoadTopologyViolationKind;
import com.plot.plugin.road.station.RoadStationDataTransforms;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.plugin.road.vertical.VerticalAlignmentGradeSmoother;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条道路一键自动修复：组合 reconcile / topology repair / order sync /
 * HA 物化 / 纵断面重算与坡度平缓。
 */
public final class RoadAutoRepair {

    private static final double VERTICAL_ALIGNMENT_LENGTH_TOLERANCE = 1.0;
    private static final double HORIZONTAL_ALIGNMENT_LENGTH_TOLERANCE = 1.0;
    private static final double HORIZONTAL_ALIGNMENT_POINT_TOLERANCE = 1.0;

    private RoadAutoRepair() {
    }

    public record Result(
            String roadId,
            List<RoadRepairIssue> issuesBefore,
            List<RoadRepairIssue> issuesAfter,
            int stepsApplied) {

        public boolean changed() {
            return stepsApplied > 0;
        }

        public boolean fullyRepaired() {
            return issuesBefore.isEmpty() ? false : issuesAfter.isEmpty();
        }
    }

    public static List<RoadRepairIssue> diagnose(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            IntersectionProbeResult intersectionProbe,
            boolean adoptIntersectionRepairPending) {
        if (network == null || road == null) {
            return List.of();
        }
        List<RoadRepairIssue> issues = new ArrayList<>();
        addIntersectionIssues(issues, intersectionProbe, adoptIntersectionRepairPending);
        addTopologyIssues(issues, network, road);
        addHorizontalAlignmentIssues(issues, network, road);
        addVerticalIssues(issues, network, road, config);
        return List.copyOf(issues);
    }

    /**
     * 执行修复流水线。调用方负责 {@code pushHistory} 与 {@code notifyNetworkChanged}。
     */
    public static Result fix(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            RoadNetworkBuilder networkBuilder,
            Runnable onIntersectionReconciled) {
        if (network == null || road == null || config == null) {
            return new Result("", List.of(), List.of(), 0);
        }

        IntersectionProbeResult probe = networkBuilder != null
            ? networkBuilder.probeIntersectionCompleteness(network)
            : IntersectionProbeResult.resolved();
        List<RoadRepairIssue> before = diagnose(network, road, config, probe, false);
        if (before.isEmpty()) {
            return new Result(road.getId(), List.of(), List.of(), 0);
        }

        String roadId = road.getId();
        int steps = 0;

        if (before.contains(RoadRepairIssue.INTERSECTION_INCOMPLETE)
                || before.contains(RoadRepairIssue.INTERSECTION_PENDING)) {
            if (networkBuilder != null) {
                IntersectionResult reconcile = networkBuilder.detectAndSplitIntersections(network);
                if (reconcile != IntersectionResult.INCOMPLETE) {
                    steps++;
                    if (onIntersectionReconciled != null) {
                        onIntersectionReconciled.run();
                    }
                }
            }
        }

        road = network.getRoad(roadId);
        if (road == null) {
            return new Result(roadId, before, List.of(), steps);
        }

        if (hasTopologyShapeIssue(before)) {
            RoadTopologyRoadSplitter.RepairResult repair = RoadTopologyRoadSplitter.repairRoad(network, road);
            if (repair.sourceRoadsRepaired() > 0
                    || repair.newRoadsCreated() > 0
                    || repair.loopsPromoted() > 0) {
                steps++;
            }
        }

        road = network.getRoad(roadId);
        if (road != null && before.contains(RoadRepairIssue.SEGMENT_ORDER_MISMATCH)) {
            if (RoadTopologyInvariantValidator.syncStorageOrderIfMaintainable(network, road)) {
                steps++;
            }
        }

        road = network.getRoad(roadId);
        if (road != null && hasHorizontalRepairTarget(before)) {
            steps += repairHorizontalAlignment(network, road);
        }

        road = network.getRoad(roadId);
        if (road != null && hasVerticalRepairTarget(before)) {
            steps += repairVerticalProfile(network, road, config);
        }

        road = network.getRoad(roadId);
        List<RoadRepairIssue> after = road != null
            ? diagnose(
                network,
                road,
                config,
                networkBuilder != null
                    ? networkBuilder.probeIntersectionCompleteness(network)
                    : IntersectionProbeResult.resolved(),
                false)
            : List.of();

        return new Result(roadId, before, after, steps);
    }

    private static void addIntersectionIssues(
            List<RoadRepairIssue> issues,
            IntersectionProbeResult probe,
            boolean adoptIntersectionRepairPending) {
        if (probe != null && probe.isIncomplete()) {
            issues.add(RoadRepairIssue.INTERSECTION_INCOMPLETE);
        } else if ((probe != null && probe.hasPendingWork()) || adoptIntersectionRepairPending) {
            issues.add(RoadRepairIssue.INTERSECTION_PENDING);
        }
    }

    private static void addTopologyIssues(List<RoadRepairIssue> issues, RoadNetwork network, Road road) {
        for (RoadTopologyViolation violation : RoadTopologyInvariantValidator.validateRoad(network, road)) {
            switch (violation.kind()) {
                case ROAD_DISCONNECTED -> issues.add(RoadRepairIssue.TOPOLOGY_DISCONNECTED);
                case ROAD_BRANCHING -> issues.add(RoadRepairIssue.TOPOLOGY_BRANCHING);
                case ROAD_CYCLE -> issues.add(RoadRepairIssue.TOPOLOGY_CYCLE);
                case ROAD_ORDER_MISMATCH -> issues.add(RoadRepairIssue.SEGMENT_ORDER_MISMATCH);
            }
        }
    }

    private static void addHorizontalAlignmentIssues(
            List<RoadRepairIssue> issues,
            RoadNetwork network,
            Road road) {
        if (HorizontalAlignmentJunctionConsistency.hasConflicts(network, road)) {
            issues.add(RoadRepairIssue.JUNCTION_ENDPOINT_MISMATCH);
        }
        if (HorizontalAlignmentCenterlineConsistency.isEvaluable(network, road)) {
            HorizontalAlignmentCenterlineConsistency.Report report =
                HorizontalAlignmentCenterlineConsistency.evaluate(network, road);
            if (!report.isConsistent(
                    HORIZONTAL_ALIGNMENT_LENGTH_TOLERANCE,
                    HORIZONTAL_ALIGNMENT_POINT_TOLERANCE)) {
                issues.add(RoadRepairIssue.HORIZONTAL_ALIGNMENT_MISMATCH);
            }
        } else if (!HorizontalAlignmentTopologyValidator.validate(network, road).isEmpty()) {
            issues.add(RoadRepairIssue.HORIZONTAL_ALIGNMENT_MISMATCH);
        }
    }

    private static void addVerticalIssues(
            List<RoadRepairIssue> issues,
            RoadNetwork network,
            Road road,
            RoadSystemConfig config) {
        RoadVerticalAlignment alignment = road.getVerticalAlignment();
        if (!VerticalAlignmentGeometry.isEvaluable(alignment)
            || !RoadStationing.isStationable(network, road)) {
            return;
        }
        double totalLength = RoadStationing.canonicalLength(network, road);
        if (Math.abs(alignment.startStation()) > VERTICAL_ALIGNMENT_LENGTH_TOLERANCE
            || Math.abs(alignment.endStation() - totalLength) > VERTICAL_ALIGNMENT_LENGTH_TOLERANCE) {
            issues.add(RoadRepairIssue.VERTICAL_PROFILE_MISMATCH);
        }
        float limit = road.getEffectiveMaxSlope(config);
        if (VerticalAlignmentGradeSmoother.exceedsGradeLimit(alignment, limit)) {
            issues.add(RoadRepairIssue.STEEP_GRADE);
        }
    }

    private static boolean hasTopologyShapeIssue(List<RoadRepairIssue> issues) {
        return issues.contains(RoadRepairIssue.TOPOLOGY_DISCONNECTED)
            || issues.contains(RoadRepairIssue.TOPOLOGY_BRANCHING)
            || issues.contains(RoadRepairIssue.TOPOLOGY_CYCLE);
    }

    private static boolean hasHorizontalRepairTarget(List<RoadRepairIssue> issues) {
        return issues.contains(RoadRepairIssue.HORIZONTAL_ALIGNMENT_MISMATCH)
            || issues.contains(RoadRepairIssue.JUNCTION_ENDPOINT_MISMATCH);
    }

    private static boolean hasVerticalRepairTarget(List<RoadRepairIssue> issues) {
        return issues.contains(RoadRepairIssue.VERTICAL_PROFILE_MISMATCH)
            || issues.contains(RoadRepairIssue.STEEP_GRADE);
    }

    private static int repairHorizontalAlignment(RoadNetwork network, Road road) {
        int steps = 0;
        if (road.getHorizontalAlignment() != null
            && RoadStationing.isStationable(network, road)
            && !HorizontalAlignmentCenterlineMaterializer.canMaterialize(network, road)) {
            CenterlineHorizontalAlignmentSync.Outcome outcome =
                CenterlineHorizontalAlignmentSync.syncAfterCenterlineEdit(network, road);
            if (outcome != CenterlineHorizontalAlignmentSync.Outcome.UNCHANGED) {
                steps++;
            }
        }
        if (HorizontalAlignmentCenterlineMaterializer.canMaterialize(network, road)) {
            CenterlineEditResult result = HorizontalAlignmentCenterlineMaterializer.materialize(network, road);
            if (result.isSuccess()) {
                steps++;
            }
        }
        return steps;
    }

    private static int repairVerticalProfile(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config) {
        int steps = 0;
        if (fixVerticalProfileLength(network, road)) {
            steps++;
        }
        if (VerticalAlignmentGradeSmoother.smoothRoad(network, road, config)) {
            steps++;
        }
        return steps;
    }

    private static boolean fixVerticalProfileLength(RoadNetwork network, Road road) {
        RoadVerticalAlignment alignment = road.getVerticalAlignment();
        if (!VerticalAlignmentGeometry.isEvaluable(alignment)
            || !RoadStationing.isStationable(network, road)) {
            return false;
        }
        double totalLength = RoadStationing.canonicalLength(network, road);
        double start = alignment.startStation();
        double oldLength = alignment.endStation() - start;
        if (oldLength <= VERTICAL_ALIGNMENT_LENGTH_TOLERANCE) {
            return false;
        }
        if (Math.abs(start) <= VERTICAL_ALIGNMENT_LENGTH_TOLERANCE
            && Math.abs(alignment.endStation() - totalLength) <= VERTICAL_ALIGNMENT_LENGTH_TOLERANCE) {
            return false;
        }
        double newLength = totalLength - start;
        if (newLength <= VERTICAL_ALIGNMENT_LENGTH_TOLERANCE) {
            return false;
        }
        RoadStationDataTransforms.rescaleAfterGeometryEdit(road, start, oldLength, newLength, oldLength);
        return true;
    }
}
