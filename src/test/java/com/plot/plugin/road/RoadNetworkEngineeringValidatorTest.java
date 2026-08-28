package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkEngineeringValidatorTest {

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
