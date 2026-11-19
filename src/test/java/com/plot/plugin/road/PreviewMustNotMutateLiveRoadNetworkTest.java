package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.model.ManualElevationOrigin;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewMustNotMutateLiveRoadNetworkTest {

    private RoadNetworkGenerator generator;
    private RoadNetwork live;

    @BeforeEach
    void setUp() {
        RoadSystemConfig config = new RoadSystemConfig("test");
        generator = new RoadNetworkGenerator(new RoadGenerator(
            config,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance()));
        live = buildNetworkWithStaleEditedState();
    }

    @Test
    void preparePreviewSessionDoesNotMutateLiveNetworkJson() {
        String before = live.toJson();

        var session = generator.beginPreviewSession(live, new FlatTerrainSampler(64));
        generator.preparePreviewSession(session);
        while (session.hasMoreEdges()) {
            generator.tickPreviewSessionEdges(session, Integer.MAX_VALUE);
        }
        while (session.hasMoreJunctions()) {
            generator.tickPreviewSessionJunctions(session, Integer.MAX_VALUE);
        }
        generator.completePreviewSession(session);

        assertEquals(before, live.toJson(), "preview pipeline must not mutate live RoadNetwork");
    }

    @Test
    void beginPreviewSessionDoesNotMutateLiveCrossingsForMissingCrossing() {
        RoadNetwork network = crossRoadsWithoutCrossing();
        String before = network.toJson();

        var session = generator.beginPreviewSession(network, new FlatTerrainSampler(64));
        assertTrue(session.isValid());

        assertEquals(before, network.toJson(),
            "reconcile during preview must not register crossings onto live network");
        assertTrue(network.getCrossings().isEmpty());
    }

    @Test
    void beginPreviewSessionDoesNotMutateConfiguredCrossing() {
        RoadNetwork network = crossRoadsWithConfiguredCrossing(3.5);
        String before = network.toJson();

        var session = generator.beginPreviewSession(network, new FlatTerrainSampler(64));
        assertTrue(session.isValid());

        assertEquals(before, network.toJson(),
            "preview must preserve live crossing design (cornerRadius) unchanged");
        RoadCrossing liveCrossing = network.getCrossings().values().iterator().next();
        assertEquals(3.5, liveCrossing.cornerRadius(), 1e-9);
    }

    @Test
    void beginPreviewSessionDoesNotMutateStaleCrossingGeometry() {
        RoadNetwork network = crossRoadsWithConfiguredCrossing(null);
        // Stale position away from true intersection; preview reconcile should refresh only the copy.
        RoadCrossing stale = network.getCrossings().values().iterator().next()
            .withRefreshedGeometry(40, 40, new Vec2d(2, 2));
        network.registerCrossing(stale);
        String before = network.toJson();

        var session = generator.beginPreviewSession(network, new FlatTerrainSampler(64));
        assertTrue(session.isValid());

        assertEquals(before, network.toJson(),
            "preview reconcile must not rewrite live stale crossing geometry");
        assertEquals(2.0, network.getCrossings().values().iterator().next().position().x, 1e-9);
    }

    private static RoadNetwork buildNetworkWithStaleEditedState() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("main");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setProfileSource(ProfileSource.EDITED);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(80.0, 68.0))));
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(80, 0));
        end.setManualElevation(72.0, ManualElevationOrigin.PROFILE_PROPAGATED);
        network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(80, 0)),
            road.getId());
        return network;
    }

    private static RoadNetwork crossRoadsWithoutCrossing() {
        RoadSystemConfig config = new RoadSystemConfig("preview-immutability");
        config.setRoadWidth(6);
        RoadNetwork network = new RoadNetwork();
        RoadNetworkBuilder builder = new RoadNetworkBuilder();
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(-40, 0), new Vec2d(40, 0)), false), config);
        builder.adoptShape(network, new PolylineShape(
            List.of(new Vec2d(0, -40), new Vec2d(0, 40)), false), config);
        return network;
    }

    private static RoadNetwork crossRoadsWithConfiguredCrossing(Double cornerRadius) {
        RoadNetwork network = crossRoadsWithoutCrossing();
        List<String> roadIds = network.getRoads().values().stream().map(Road::getId).toList();
        RoadCrossing crossing = RoadCrossing.atGrade(
            roadIds.get(0), 40, roadIds.get(1), 40, new Vec2d(0, 0));
        if (cornerRadius != null) {
            crossing = crossing.withCornerRadius(cornerRadius);
        }
        network.registerCrossing(crossing);
        return network;
    }
}
