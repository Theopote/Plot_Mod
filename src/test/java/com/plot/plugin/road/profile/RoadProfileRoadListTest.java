package com.plot.plugin.road.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadProfileRoadListTest {

    @Test
    void profileWorkspaceEnumeratesAllStationableRoads() {
        RoadNetwork network = buildTwoRoadNetwork();
        List<Road> roads = RoadProfileRoadList.listStationableRoads(network);
        assertEquals(2, roads.size());
        assertEquals("road-a", roads.get(0).getId());
        assertEquals("road-b", roads.get(1).getId());
    }

    @Test
    void listStationableRoadsFiltersDisconnectedRoads() {
        RoadNetwork network = new RoadNetwork();
        Road roadZ = network.createRoad("z-road");
        roadZ.setName("Zebra");
        Road roadA = network.createRoad("a-road");
        roadA.setName("Alpha");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(10, 0));
        network.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), roadZ.getId());
        network.createRoad("broken");

        List<Road> roads = RoadProfileRoadList.listStationableRoads(network);
        assertEquals(1, roads.size());
        assertEquals("z-road", roads.getFirst().getId());
    }

    @Test
    void selectedRoadAndEditorRoadStaySynchronizedViaResolveActiveRoad() {
        RoadNetwork network = buildTwoRoadNetwork();
        RoadUiContext ctx = testContext(network);
        Road roadB = network.getRoad("road-b");
        assertNotNull(roadB);

        ctx.networkManager().selectRoad(roadB.getId(), false);
        assertEquals(roadB, RoadProfileRoadList.resolveActiveRoad(network, ctx));
    }

    @Test
    void formatProfileRoadSummaryIsNonEmptyForStationableRoad() {
        RoadNetwork network = buildTwoRoadNetwork();
        Road road = network.getRoad("road-a");
        String summary = RoadProfileRoadList.formatProfileRoadSummary(network, road);
        assertFalse(summary.isBlank());
    }

    private static RoadUiContext testContext(RoadNetwork network) {
        RoadNetworkManager networkManager = new RoadNetworkManager(new RoadSystemConfig("test"), new RoadProjectStatus());
        networkManager.setNetwork(network);
        PluginContext host = new PluginContext(
            ApplicationContext.getInstance().getAppState(),
            ApplicationContext.getInstance().getCommandService(),
            ApplicationContext.getInstance().getEventBus(),
            ApplicationContext.getInstance().getToolManager(),
            IdentityCoordinateService.INSTANCE,
            null,
            null,
            null);
        return new RoadUiContext(
            networkManager,
            null,
            null,
            null,
            new RoadProjectStatus(),
            host);
    }

    private static RoadNetwork buildTwoRoadNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("road-a");
        roadA.setName("Road A");
        Road roadB = network.createRoad("road-b");
        roadB.setName("Road B");

        RoadNode a1 = network.createNode(new Vec2d(0, 0));
        RoadNode a2 = network.createNode(new Vec2d(30, 0));
        network.createEdge(
            a1.getId(), a2.getId(), List.of(new Vec2d(0, 0), new Vec2d(30, 0)), roadA.getId());

        RoadNode b1 = network.createNode(new Vec2d(0, 10));
        RoadNode b2 = network.createNode(new Vec2d(20, 10));
        network.createEdge(
            b1.getId(), b2.getId(), List.of(new Vec2d(0, 10), new Vec2d(20, 10)), roadB.getId());
        assertTrue(RoadStationing.isStationable(network, roadA));
        assertTrue(RoadStationing.isStationable(network, roadB));
        return network;
    }
}
