package com.plot.plugin.road.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.vertical.FlatVerticalIntent;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AUTO / EDITED / FLAT 混合路口：生成态与设计态高程读取必须遵循权威隔离。
 */
class MixedCrossingElevationAuthorityTest {

    private final RoadSystemConfig config = new RoadSystemConfig("mixed-crossing");

    @Test
    void generationUsesEdgeResultForAutoEvenWhenDormantPviExistsBesideEditedPartner() {
        RoadNetwork network = new RoadNetwork();
        Road auto = createHorizontal(network, "auto", 100.0);
        Road edited = createVertical(network, "edited", 50.0);

        auto.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        auto.setProfileSource(ProfileSource.AUTO);
        auto.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 40.0),
            PointOfVerticalIntersection.of(100.0, 40.0))));

        edited.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        edited.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 80.0),
            PointOfVerticalIntersection.of(50.0, 80.0))));
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, edited);

        network.registerCrossing(new RoadCrossing(
            "mix-at-grade",
            auto.getId(),
            50.0,
            edited.getId(),
            0.0,
            new Vec2d(50, 0),
            CrossingType.AT_GRADE,
            null,
            null,
            72.0));

        RoadEdge autoEdge = network.getEdge(auto.getOrderedSegmentIds().getFirst());
        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        edgeResults.put(autoEdge.getId(), profileSeries(
            List.of(0.0, 50.0, 100.0),
            List.of(70, 70, 70),
            List.of(71, 71, 71),
            List.of(72, 72, 72)));

        var autoMarkers = RoadProfileIntersectionResolver.forGeneration(
            network, auto, config, edgeResults);
        assertEquals(1, autoMarkers.size());
        // GENERATION must use build heights (72), not dormant AUTO PVI (40).
        assertEquals(72.0, autoMarkers.getFirst().currentRoadElevation(), 1e-6);

        var designMarkers = RoadProfileIntersectionResolver.forDesignOverlay(
            network, auto, config);
        assertEquals(1, designMarkers.size());
        // DESIGN_WORKING_COPY may read stored evaluable curve for editor feedback.
        assertEquals(40.0, designMarkers.getFirst().currentRoadElevation(), 1e-6);
    }

    @Test
    void gradeSeparatedEditedOverFlatUsesAuthoritativeHeightsInGeneration() {
        RoadNetwork network = new RoadNetwork();
        Road flat = createHorizontal(network, "flat", 100.0);
        Road edited = createVertical(network, "edited-over", 50.0);

        flat.setVerticalMode(RoadVerticalMode.FLAT);
        flat.setFlatVerticalIntent(new FlatVerticalIntent(64.0));

        edited.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        edited.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 78.0),
            PointOfVerticalIntersection.of(50.0, 78.0))));
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, edited);

        network.registerCrossing(new RoadCrossing(
            "mix-gs",
            flat.getId(),
            50.0,
            edited.getId(),
            0.0,
            new Vec2d(50, 0),
            CrossingType.GRADE_SEPARATED,
            edited.getId(),
            6.0,
            null));

        RoadEdge flatEdge = network.getEdge(flat.getOrderedSegmentIds().getFirst());
        RoadEdge editedEdge = network.getEdge(edited.getOrderedSegmentIds().getFirst());
        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        edgeResults.put(flatEdge.getId(), profileSeries(
            List.of(0.0, 50.0, 100.0),
            List.of(64, 64, 64),
            List.of(64, 64, 64),
            List.of(64, 64, 64)));
        edgeResults.put(editedEdge.getId(), profileSeries(
            List.of(0.0, 50.0),
            List.of(70, 70),
            List.of(78, 78),
            List.of(78, 78)));

        var flatMarkers = RoadProfileIntersectionResolver.forGeneration(
            network, flat, config, edgeResults);
        var editedMarkers = RoadProfileIntersectionResolver.forGeneration(
            network, edited, config, edgeResults);

        assertEquals(1, flatMarkers.size());
        assertEquals(1, editedMarkers.size());
        assertEquals(64.0, flatMarkers.getFirst().currentRoadElevation(), 1e-6);
        assertEquals(78.0, editedMarkers.getFirst().currentRoadElevation(), 1e-6);
        assertTrue(editedMarkers.getFirst().gradeSeparated());
        assertTrue(editedMarkers.getFirst().currentRoadElevated());
        assertFalse(flatMarkers.getFirst().currentRoadElevated());
        assertEquals(14.0, editedMarkers.getFirst().clearanceGap(), 1e-6);
    }

    @Test
    void designOverlaySeesEditedWorkingCurveBeforeAuthorityWhileGenerationNeedsEdgeResults() {
        RoadNetwork network = new RoadNetwork();
        Road main = createHorizontal(network, "main", 100.0);
        Road side = createVertical(network, "side", 50.0);

        main.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        main.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 76.0),
            PointOfVerticalIntersection.of(100.0, 76.0))));
        // Side needs an evaluable design curve so design overlay can resolve both elevations.
        side.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        side.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(50.0, 64.0))));
        // Not confirmed → generation authority is not EDITED.
        assertFalse(ResolvedProfileAuthority.usesEditedProfileForGeneration(network, main));

        network.registerCrossing(new RoadCrossing(
            "pending-edit",
            main.getId(),
            50.0,
            side.getId(),
            0.0,
            new Vec2d(50, 0),
            CrossingType.GRADE_SEPARATED,
            main.getId(),
            5.0,
            null));

        var design = RoadProfileIntersectionResolver.forDesignOverlay(network, main, config);
        assertEquals(1, design.size());
        assertEquals(76.0, design.getFirst().currentRoadElevation(), 1e-6);

        RoadEdge mainEdge = network.getEdge(main.getOrderedSegmentIds().getFirst());
        RoadEdge sideEdge = network.getEdge(side.getOrderedSegmentIds().getFirst());
        Map<String, RoadGenerationResult> edgeResults = new LinkedHashMap<>();
        edgeResults.put(mainEdge.getId(), profileSeries(
            List.of(0.0, 50.0, 100.0),
            List.of(70, 70, 70),
            List.of(71, 71, 71),
            List.of(72, 72, 72)));
        // Side has stored VA but not EDITED authority — generation must use edgeResults, not dormant PVI.
        edgeResults.put(sideEdge.getId(), profileSeries(
            List.of(0.0, 50.0),
            List.of(64, 64),
            List.of(64, 64),
            List.of(65, 65)));
        var generation = RoadProfileIntersectionResolver.forGeneration(
            network, main, config, edgeResults);
        assertEquals(1, generation.size());
        assertEquals(72.0, generation.getFirst().currentRoadElevation(), 1e-6);
        assertEquals(65.0, generation.getFirst().otherRoadElevation(), 1e-6);
    }

    private static Road createHorizontal(RoadNetwork network, String id, double length) {
        Road road = network.createRoad(id);
        var west = network.createNode(new Vec2d(0, 0));
        var east = network.createNode(new Vec2d(length, 0));
        network.createEdge(
            west.getId(), east.getId(), List.of(west.getPosition(), east.getPosition()), id);
        return road;
    }

    private static Road createVertical(RoadNetwork network, String id, double length) {
        Road road = network.createRoad(id);
        var south = network.createNode(new Vec2d(50, 0));
        var north = network.createNode(new Vec2d(50, length));
        network.createEdge(
            south.getId(), north.getId(), List.of(south.getPosition(), north.getPosition()), id);
        return road;
    }

    private static RoadGenerationResult profileSeries(
            List<Double> distances,
            List<Integer> ground,
            List<Integer> guide,
            List<Integer> build) {
        RoadGenerationResult profile = new RoadGenerationResult(100);
        profile.profileDistances = distances;
        profile.profileGroundHeights = ground;
        profile.profileGuideLine = guide;
        profile.profileBuildHeights = build;
        profile.profileDesignElevations = build.stream().map(Integer::doubleValue).toList();
        return profile;
    }
}
