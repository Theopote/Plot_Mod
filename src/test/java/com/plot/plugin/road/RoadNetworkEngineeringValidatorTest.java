package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.alignment.HorizontalAlignmentElement;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkEngineeringValidatorTest {

    @Test
    void preGenerationBlocksShortRoadWithNonFlatActiveProfile() {
        RoadNetwork network = shortProfileNetwork(18.0, 70.0, 72.0, RoadVerticalMode.MANUAL_PROFILE);

        RoadNetworkValidationReport report =
            RoadNetworkEngineeringValidator.analyzePreGeneration(network);

        assertTrue(report.hasErrors());
        assertTrue(report.blocksBuild());
        assertTrue(report.items().stream().anyMatch(item ->
            item.level() == RoadNetworkValidationReport.Level.ERROR
                && item.messageKey().equals("plugin.road.validation.short_road_non_flat")));
    }

    @Test
    void preGenerationAllowsFlatShortRoadAndSlopeAtMinimumLength() {
        RoadNetwork flat = shortProfileNetwork(18.0, 70.0, 70.0, RoadVerticalMode.FLAT);
        RoadNetwork minimumLength =
            shortProfileNetwork(20.0, 70.0, 72.0, RoadVerticalMode.MANUAL_PROFILE);

        assertFalse(RoadNetworkEngineeringValidator.analyzePreGeneration(flat).hasErrors());
        assertFalse(RoadNetworkEngineeringValidator.analyzePreGeneration(minimumLength).hasErrors());
    }

    @Test
    void preGenerationIgnoresDormantManualProfileInAutomaticMode() {
        RoadNetwork network = shortProfileNetwork(18.0, 70.0, 72.0, RoadVerticalMode.AUTO_SMOOTH);

        assertFalse(RoadNetworkEngineeringValidator.analyzePreGeneration(network).hasErrors());
    }

    @Test
    void reportsVoxelInvisibleElevationChangeAsWarningWithoutBlocking() {
        RoadNetwork network = shortProfileNetwork(
            100.0, 70.0, 70.3, RoadVerticalMode.MANUAL_PROFILE);

        RoadNetworkValidationReport report = RoadNetworkEngineeringValidator.analyze(
            network, Map.of(), new RoadSystemConfig("test"));

        assertTrue(hasWarning(
            report, "plugin.road.validation.vertical_alignment_elevation_change_not_visible"));
        assertFalse(report.hasErrors());
        assertFalse(report.blocksBuild());
    }

    private static RoadNetwork shortProfileNetwork(
            double length,
            double startElevation,
            double endElevation,
            RoadVerticalMode mode) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("short-profile");
        road.setVerticalMode(mode);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, startElevation),
            PointOfVerticalIntersection.of(length, endElevation))));
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(length, 0));
        network.createEdge(start.getId(), end.getId(), List.of(
            new Vec2d(0, 0), new Vec2d(length, 0)), road.getId());
        return network;
    }

    @Test
    void preGenerationBlocksHaCenterlineUnresolved() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 8), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        road.setHorizontalAlignment(alignment);

        RoadNetworkValidationReport report =
            RoadNetworkEngineeringValidator.analyzePreGeneration(network);

        assertTrue(report.blocksBuild());
        assertTrue(hasError(report, "plugin.road.validation.horizontal_alignment_centerline_unresolved"));
    }

    @Test
    void preGenerationBlocksHaJunctionConflict() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("a");
        network.createRoad("b");
        RoadNode shared = network.createNode(new Vec2d(0, 0));
        RoadNode endA = network.createNode(new Vec2d(100, 0));
        RoadNode endB = network.createNode(new Vec2d(0, 100));
        network.createEdge(
            shared.getId(), endA.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)), roadA.getId());
        network.createEdge(
            shared.getId(), endB.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(0, 100)),
            network.getRoads().values().stream().filter(r -> !r.getId().equals("a")).findFirst().orElseThrow().getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 5), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(100.0));
        roadA.setHorizontalAlignment(alignment);

        RoadNetworkValidationReport report =
            RoadNetworkEngineeringValidator.analyzePreGeneration(network);

        assertTrue(report.blocksBuild());
        assertTrue(hasError(report, "plugin.road.validation.horizontal_alignment_junction_conflict"));
    }

    @Test
    void preGenerationBlocksHaTopologyMismatch() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            start.getId(), end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(new Vec2d(0, 0), 0.0, List.of());
        alignment.addElement(HorizontalAlignmentElement.tangent(90.0));
        road.setHorizontalAlignment(alignment);

        RoadNetworkValidationReport report =
            RoadNetworkEngineeringValidator.analyzePreGeneration(network);

        assertTrue(report.blocksBuild());
        assertTrue(hasError(report, "plugin.road.validation.horizontal_alignment_topology_mismatch"));
    }

    @Test
    void preGenerationDoesNotBlockRoadBranchingOrPendingIntersections() {
        RoadNetwork fork = new RoadNetwork();
        Road road = fork.createRoad();
        RoadNode a = fork.createNode(new Vec2d(0, 0));
        RoadNode b = fork.createNode(new Vec2d(10, 0));
        RoadNode c = fork.createNode(new Vec2d(20, 0));
        RoadNode d = fork.createNode(new Vec2d(10, 10));
        fork.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        fork.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());
        fork.createEdge(b.getId(), d.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());
        assertFalse(RoadNetworkEngineeringValidator.analyzePreGeneration(fork).blocksBuild());

        RoadNetwork crossing = new RoadNetwork();
        Road roadA = crossing.createRoad("road-a");
        Road roadB = crossing.createRoad("road-b");
        RoadNode aStart = crossing.createNode(new Vec2d(0, 5));
        RoadNode aEnd = crossing.createNode(new Vec2d(10, 5));
        crossing.createEdge(aStart.getId(), aEnd.getId(), List.of(
            new Vec2d(0, 5), new Vec2d(10, 5)), roadA.getId());
        RoadNode bStart = crossing.createNode(new Vec2d(5, 5));
        RoadNode bEnd = crossing.createNode(new Vec2d(5, 10));
        crossing.createEdge(bStart.getId(), bEnd.getId(), List.of(
            new Vec2d(5, 5), new Vec2d(5, 10)), roadB.getId());

        RoadNetworkValidationReport full = RoadNetworkEngineeringValidator.analyze(
            crossing, Map.of(), new RoadSystemConfig("test"));
        assertTrue(hasWarning(full, "plugin.road.validation.intersections_pending"));
        assertFalse(RoadNetworkEngineeringValidator.analyzePreGeneration(crossing).blocksBuild());
    }

    @Test
    void reportsConnectedRoadsAndDeadEnds() {
        RoadNetwork network = new RoadNetwork();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));
        Road road = network.createRoad();
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());

        RoadNetworkValidationReport report = RoadNetworkEngineeringValidator.analyze(
            network,
            Map.of(),
            new RoadSystemConfig("test"));

        assertTrue(findWarningCount(report, "plugin.road.validation.dead_ends") >= 2);
        assertTrue(hasOk(report, "plugin.road.validation.roads_connected"));
        assertTrue(hasOk(report, "plugin.road.validation.intersections_resolved"));
    }

    @Test
    void reportsDisconnectedComponents() {
        RoadNetwork network = new RoadNetwork();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(100, 0));
        RoadNode d = network.createNode(new Vec2d(110, 0));
        Road roadA = network.createRoad();
        Road roadB = network.createRoad();
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), roadA.getId());
        network.createEdge(c.getId(), d.getId(), List.of(new Vec2d(100, 0), new Vec2d(110, 0)), roadB.getId());

        RoadNetworkValidationReport report = RoadNetworkEngineeringValidator.analyze(
            network,
            Map.of(),
            new RoadSystemConfig("test"));

        assertTrue(hasWarning(report, "plugin.road.validation.disconnected_components"));
        assertEquals(1, findWarningCount(report, "plugin.road.validation.disconnected_components"));
    }

    @Test
    void reportsRoadSegmentTopologyWarningForForkWithinRoad() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));
        RoadNode d = network.createNode(new Vec2d(10, 10));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());
        network.createEdge(b.getId(), d.getId(), List.of(new Vec2d(10, 0), new Vec2d(10, 10)), road.getId());

        RoadNetworkValidationReport report = RoadNetworkEngineeringValidator.analyze(
            network,
            Map.of(),
            new RoadSystemConfig("test"));

        assertEquals(1, findWarningCount(report, "plugin.road.validation.road_branching"));
    }

    @Test
    void reportsSimpleChainTopologyOk() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());

        RoadNetworkValidationReport report = RoadNetworkEngineeringValidator.analyze(
            network,
            Map.of(),
            new RoadSystemConfig("test"));

        assertTrue(hasOk(report, "plugin.road.validation.road_topology_ok"));
    }

    @Test
    void reportsOrderMismatchWarning() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(20, 0));
        RoadEdge edge1 = network.createEdge(
            a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        RoadEdge edge2 = network.createEdge(
            b.getId(), c.getId(), List.of(new Vec2d(10, 0), new Vec2d(20, 0)), road.getId());
        road.reorderSegments(List.of(edge2.getId(), edge1.getId()));

        RoadNetworkValidationReport report = RoadNetworkEngineeringValidator.analyze(
            network,
            Map.of(),
            new RoadSystemConfig("test"));

        assertEquals(1, findWarningCount(report, "plugin.road.validation.road_order_mismatch"));
    }

    @Test
    void reportsSlopeOverrideOverlap() {
        RoadNetwork network = new RoadNetwork();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(20, 0));
        Road road = network.createRoad();
        RoadEdge edge = network.createEdge(
            a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(20, 0)), road.getId());
        edge.setSlopeOverrides(List.of(
            new RoadEdge.SlopeOverride(0, 12, 8f),
            new RoadEdge.SlopeOverride(10, 20, 6f)));

        RoadNetworkValidationReport report = RoadNetworkEngineeringValidator.analyze(
            network,
            Map.of(),
            new RoadSystemConfig("test"));

        assertEquals(1, findWarningCount(report, "plugin.road.validation.slope_override_overlap"));
    }

    @Test
    void reportsJunctionGradeExceedanceFromPreviewProfile() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setMaxSlope(5.0f);

        RoadNetwork network = new RoadNetwork();
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        RoadNode north = network.createNode(new Vec2d(0, 12));
        RoadNode south = network.createNode(new Vec2d(0, -12));
        RoadNode east = network.createNode(new Vec2d(12, 0));
        RoadNode west = network.createNode(new Vec2d(-12, 0));
        Road roadA = network.createRoad();
        roadA.setMaxSlope(5.0f);
        Road roadB = network.createRoad();
        roadB.setMaxSlope(5.0f);

        RoadEdge northEdge = network.createEdge(
            junction.getId(), north.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, 12)), roadA.getId());
        network.createEdge(junction.getId(), south.getId(), List.of(new Vec2d(0, 0), new Vec2d(0, -12)), roadA.getId());
        network.createEdge(junction.getId(), east.getId(), List.of(new Vec2d(0, 0), new Vec2d(12, 0)), roadB.getId());
        network.createEdge(junction.getId(), west.getId(), List.of(new Vec2d(0, 0), new Vec2d(-12, 0)), roadB.getId());

        RoadGenerationResult steep = profileResult(12.0, List.of(0.0, 10.0), List.of(64, 74));
        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        edgeResults.put(northEdge.getId(), steep);

        RoadNetworkValidationReport report = RoadNetworkEngineeringValidator.analyze(
            network,
            edgeResults,
            config);

        assertEquals(1, findWarningCount(report, "plugin.road.validation.junctions_exceed_grade"));
    }

    @Test
    void reportsVerticalAlignmentLengthMismatch() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("design");
        road.setVerticalAlignment(new com.plot.plugin.road.vertical.RoadVerticalAlignment(java.util.List.of(
            com.plot.plugin.road.vertical.PointOfVerticalIntersection.of(0.0, 80.0),
            com.plot.plugin.road.vertical.PointOfVerticalIntersection.of(80.0, 90.0)
        )));
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            a.getId(), b.getId(), java.util.List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadNetworkValidationReport report = RoadNetworkEngineeringValidator.analyze(
            network,
            Map.of(),
            new RoadSystemConfig("test"));

        assertEquals(
            1,
            findWarningCount(report, "plugin.road.validation.vertical_alignment_length_mismatch"));
    }

    @Test
    void reportsVerticalAlignmentGradeExceedance() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        config.setMaxSlope(5.0f);

        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("steep");
        road.setMaxSlope(5.0f);
        road.setVerticalAlignment(new com.plot.plugin.road.vertical.RoadVerticalAlignment(java.util.List.of(
            com.plot.plugin.road.vertical.PointOfVerticalIntersection.of(0.0, 0.0),
            com.plot.plugin.road.vertical.PointOfVerticalIntersection.of(100.0, 20.0)
        )));
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            a.getId(), b.getId(), java.util.List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadNetworkValidationReport report = RoadNetworkEngineeringValidator.analyze(
            network,
            Map.of(),
            config);

        assertEquals(
            1,
            findWarningCount(report, "plugin.road.validation.vertical_alignment_grade_exceeds"));
    }

    private static RoadGenerationResult profileResult(double pathLength, List<Double> distances, List<Integer> heights) {
        RoadGenerationResult result = new RoadGenerationResult(pathLength);
        result.profileDistances = distances;
        result.profileGroundHeights = heights;
        result.profileGuideLine = heights;
        result.profileTargetHeights = heights;
        return result;
    }

    private static boolean hasOk(RoadNetworkValidationReport report, String key) {
        return report.items().stream().anyMatch(item ->
            item.level() == RoadNetworkValidationReport.Level.OK && item.messageKey().equals(key));
    }

    @Test
    void overviewHealthSkipsIntersectionProbeButHonorsRepairPendingFlag() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");

        RoadNode aStart = network.createNode(new Vec2d(0, 5));
        RoadNode aEnd = network.createNode(new Vec2d(10, 5));
        network.createEdge(aStart.getId(), aEnd.getId(), List.of(
            new Vec2d(0, 5), new Vec2d(10, 5)), roadA.getId());

        RoadNode bStart = network.createNode(new Vec2d(5, 5));
        RoadNode bEnd = network.createNode(new Vec2d(5, 10));
        network.createEdge(bStart.getId(), bEnd.getId(), List.of(
            new Vec2d(5, 5), new Vec2d(5, 10)), roadB.getId());

        RoadNetworkValidationReport overview = RoadNetworkEngineeringValidator.analyzeOverviewHealth(
            network, false);
        assertFalse(hasWarning(overview, "plugin.road.validation.intersections_pending"));

        RoadNetworkValidationReport pending = RoadNetworkEngineeringValidator.analyzeOverviewHealth(
            network, true);
        assertTrue(hasWarning(pending, "plugin.road.validation.intersections_pending"));

        RoadNetworkValidationReport full = RoadNetworkEngineeringValidator.analyze(
            network,
            Map.of(),
            new RoadSystemConfig("test"));
        assertTrue(hasWarning(full, "plugin.road.validation.intersections_pending"));
    }

    @Test
    void reportsPendingIntersectionsWhenCrossingsNotSplit() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");

        RoadNode aStart = network.createNode(new Vec2d(0, 5));
        RoadNode aEnd = network.createNode(new Vec2d(10, 5));
        network.createEdge(aStart.getId(), aEnd.getId(), List.of(
            new Vec2d(0, 5), new Vec2d(10, 5)), roadA.getId());

        RoadNode bStart = network.createNode(new Vec2d(5, 5));
        RoadNode bEnd = network.createNode(new Vec2d(5, 10));
        network.createEdge(bStart.getId(), bEnd.getId(), List.of(
            new Vec2d(5, 5), new Vec2d(5, 10)), roadB.getId());

        RoadNetworkValidationReport report = RoadNetworkEngineeringValidator.analyze(
            network,
            Map.of(),
            new RoadSystemConfig("test"));

        assertTrue(hasWarning(report, "plugin.road.validation.intersections_pending"));
        assertTrue(report.hasIntersectionWork());
    }

    @Test
    void reportsResolvedIntersectionsAfterReconcile() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        Road roadB = network.createRoad("road-b");

        RoadNode aStart = network.createNode(new Vec2d(0, 5));
        RoadNode aEnd = network.createNode(new Vec2d(10, 5));
        network.createEdge(aStart.getId(), aEnd.getId(), List.of(
            new Vec2d(0, 5), new Vec2d(10, 5)), roadA.getId());

        RoadNode bStart = network.createNode(new Vec2d(5, 5));
        RoadNode bEnd = network.createNode(new Vec2d(5, 10));
        network.createEdge(bStart.getId(), bEnd.getId(), List.of(
            new Vec2d(5, 5), new Vec2d(5, 10)), roadB.getId());

        new RoadNetworkBuilder().detectAndSplitIntersections(network);

        RoadNetworkValidationReport report = RoadNetworkEngineeringValidator.analyze(
            network,
            Map.of(),
            new RoadSystemConfig("test"));

        assertTrue(hasOk(report, "plugin.road.validation.intersections_resolved"));
        assertFalse(report.hasIntersectionWork());
    }

    private static boolean hasError(RoadNetworkValidationReport report, String key) {
        return report.items().stream().anyMatch(item ->
            item.level() == RoadNetworkValidationReport.Level.ERROR && item.messageKey().equals(key));
    }

    private static boolean hasWarning(RoadNetworkValidationReport report, String key) {
        return report.items().stream().anyMatch(item ->
            item.level() == RoadNetworkValidationReport.Level.WARNING && item.messageKey().equals(key));
    }

    private static int findWarningCount(RoadNetworkValidationReport report, String key) {
        return report.items().stream()
            .filter(item -> item.level() == RoadNetworkValidationReport.Level.WARNING
                && item.messageKey().equals(key))
            .mapToInt(item -> item.args().length > 0 ? ((Number) item.args()[0]).intValue() : 1)
            .findFirst()
            .orElse(0);
    }
}
