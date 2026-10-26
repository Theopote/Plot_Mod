package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.ManualElevationOrigin;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.core.terrain.FlatTerrainSampler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
