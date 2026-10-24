package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.vertical.FlatVerticalIntent;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProfileContinuityRegressionTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void registeredCrossingSplitPreservesConfirmedEditedProfile(boolean separated) {
        var network = new RoadNetwork();
        var edited = network.createRoad("edited");
        var automatic = network.createRoad("automatic");
        var west = network.createNode(new Vec2d(-80, 0));
        var east = network.createNode(new Vec2d(80, 0));
        var south = network.createNode(new Vec2d(0, -80));
        var north = network.createNode(new Vec2d(0, 80));
        network.createEdge(west.getId(), east.getId(), List.of(west.getPosition(), east.getPosition()), edited.getId());
        network.createEdge(south.getId(), north.getId(), List.of(south.getPosition(), north.getPosition()), automatic.getId());
        edited.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 82), PointOfVerticalIntersection.of(160, 82))));
        com.plot.plugin.road.vertical.ResolvedProfileAuthority.markEditedProfileConfirmed(network, edited);
        int savedSignature = edited.getEditedGeometrySignature();
        network.registerCrossing(separated
            ? com.plot.plugin.road.crossing.RoadCrossing.gradeSeparated(edited.getId(), 80, automatic.getId(), 80,
                new Vec2d(0, 0), edited.getId(), 6)
            : com.plot.plugin.road.crossing.RoadCrossing.atGrade(edited.getId(), 80, automatic.getId(), 80, new Vec2d(0, 0)));
        var config = new RoadSystemConfig("registered-edited-crossing");
        var generator = new RoadNetworkGenerator(new RoadGenerator(config, IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()));
        var preview = generator.calculateProfileSampling(network, new FlatTerrainSampler(76));
        var generated = preview.profileNetwork();
        assertTrue(com.plot.plugin.road.vertical.ResolvedProfileAuthority.usesEditedProfileForGeneration(
            generated, generated.getRoad(edited.getId())), "generation-only splits must not select AUTO");
        assertEquals(savedSignature, edited.getEditedGeometrySignature());
        assertEquals(2, network.getEdges().size());
        assertEquals(4, generated.getEdges().size());
        generated.getEdges().values().stream().filter(e -> edited.getId().equals(e.getRoadId())).forEach(edge ->
            assertTrue(preview.edgeResults().get(edge.getId()).profileBuildHeights.stream().allMatch(y -> y == 82)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void automaticCrossingMustNotPullAnAuthoritativeFlatDesignOffItsPlane(boolean flat) {
        var network = new RoadNetwork();
        var designed = network.createRoad("designed");
        var automatic = network.createRoad("automatic");
        var center = network.createNode(new Vec2d(0, 0));
        var west = network.createNode(new Vec2d(-80, 0));
        var east = network.createNode(new Vec2d(80, 0));
        var south = network.createNode(new Vec2d(0, -80));
        var north = network.createNode(new Vec2d(0, 80));
        var first = network.createEdge(west.getId(), center.getId(), List.of(west.getPosition(), center.getPosition()), designed.getId());
        var second = network.createEdge(center.getId(), east.getId(), List.of(center.getPosition(), east.getPosition()), designed.getId());
        network.createEdge(south.getId(), center.getId(), List.of(south.getPosition(), center.getPosition()), automatic.getId());
        network.createEdge(center.getId(), north.getId(), List.of(center.getPosition(), north.getPosition()), automatic.getId());
        designed.setVerticalMode(flat ? RoadVerticalMode.FLAT : RoadVerticalMode.FIT_TERRAIN);
        designed.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 82), PointOfVerticalIntersection.of(160, 82))));
        if (flat) designed.setFlatVerticalIntent(new FlatVerticalIntent(82));
        else com.plot.plugin.road.vertical.ResolvedProfileAuthority.markEditedProfileConfirmed(network, designed);
        var config = new RoadSystemConfig("authoritative-crossing");
        var generator = new RoadNetworkGenerator(new RoadGenerator(config, IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()));
        var preview = generator.calculateProfileSampling(network, new FlatTerrainSampler(76));
        for (var edge : List.of(first, second)) {
            var result = preview.edgeResults().get(edge.getId());
            assertNotNull(result);
            assertTrue(result.profileBuildHeights.stream().allMatch(y -> y == 82),
                "An AUTO neighbor must adapt to the saved design, not pull it into a local dip");
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {64.25, 64.5, 64.75})
    void fractionalFlatDesignNeverGeneratesRepeatedCrestsAndSags(double elevation) {
        var result = RoadHeightRasterizer.rasterize(List.of(elevation, elevation),
            List.of(600.0), List.of(1f), null);
        int expected = (int) Math.round(elevation);
        assertTrue(result.samples().stream().allMatch(s -> s.buildY() == expected),
            "A constant design must produce a constant integer road, not alternate between floor and ceil");
    }

    @Test
    void automaticRoadDoesNotRestartAtUnreachableSoftNodeHeights() {
        var network = new RoadNetwork();
        var road = network.createRoad("automatic");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        var previous = network.createNode(new Vec2d(0, 0));
        for (int x = 20; x <= 100; x += 20) {
            var next = network.createNode(new Vec2d(x, 0));
            network.createEdge(previous.getId(), next.getId(), List.of(previous.getPosition(), next.getPosition()), road.getId());
            previous = next;
        }
        var config = new RoadSystemConfig("automatic-continuity");
        config.setMaxSlope(5);
        var terrain = new com.plot.core.terrain.TerrainSampler() {
            public int sampleSurfaceY(Vec2d p) { return p.x < 50 ? 80 : 40; }
            public boolean isSolidBlock(int x, int y, int z) { return y <= sampleSurfaceY(new Vec2d(x, z)); }
        };
        var generator = new RoadNetworkGenerator(new RoadGenerator(config, IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()));
        var preview = generator.calculateProfileSampling(network, terrain);
        Integer last = null;
        for (var segment : com.plot.plugin.road.station.RoadStationing.orientedSegments(network, road)) {
            var result = preview.edgeResults().get(segment.edgeId());
            assertNotNull(result);
            int start = segment.forward() ? result.profileBuildHeights.getFirst() : result.profileBuildHeights.getLast();
            if (last != null) assertTrue(Math.abs(last - start) <= 1, "same road has a cliff: " + last + " -> " + start);
            last = segment.forward() ? result.profileBuildHeights.getLast() : result.profileBuildHeights.getFirst();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-20, 0, 82})
    void flatIntentWithoutDerivedAlignmentNeverReportsAnArtificialZeroHeight(int elevation) {
        var network = new RoadNetwork();
        var road = network.createRoad("flat");
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(100, 0));
        var edge = network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        road.setVerticalMode(RoadVerticalMode.FLAT);
        road.setFlatVerticalIntent(new FlatVerticalIntent(elevation));
        road.setVerticalAlignment(null);
        assertEquals(elevation, VerticalAlignmentEndpointHeight.atNode(network, edge, a).orElseThrow());
        assertEquals(elevation, VerticalAlignmentEndpointHeight.atNode(network, edge, b).orElseThrow());
    }

    @Test
    void legacyFlatElevationSurvivesGenerationPolicyBeforeAnyEdgeIsSolved() {
        var network = new RoadNetwork();
        var road = network.createRoad("legacy-flat");
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(100, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        road.setVerticalMode(RoadVerticalMode.FLAT);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 82), PointOfVerticalIntersection.of(100, 82))));
        VerticalProfileGenerationPolicy.applyGenerationProfileAuthority(network);
        assertEquals(82, road.getFlatVerticalIntent().getBaseElevation());
    }

    @Test
    void flatRoadKeepsOneHeightAcrossSplitAndReversedPhysicalEdges() {
        var network = new RoadNetwork();
        var road = network.createRoad("flat");
        var a = network.createNode(new Vec2d(0, 0));
        var b = network.createNode(new Vec2d(80, 0));
        var c = network.createNode(new Vec2d(160, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        network.createEdge(c.getId(), b.getId(), List.of(c.getPosition(), b.getPosition()), road.getId());
        road.setVerticalMode(RoadVerticalMode.FLAT);
        road.setFlatVerticalIntent(new FlatVerticalIntent(82));
        var config = new RoadSystemConfig("continuity");
        var generator = new RoadNetworkGenerator(new RoadGenerator(config, IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()));
        var preview = generator.generatePreview(network, new FlatTerrainSampler(76));
        assertEquals(2, preview.edgeResults().size());
        preview.edgeResults().values().forEach(result -> {
            assertTrue(result.profileBuildHeights.stream().allMatch(y -> y == 82), "every edge must honor the same flat design");
            assertTrue(result.manualEndpointConstraintFeasible);
        });
    }
}
