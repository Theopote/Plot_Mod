package com.plot.plugin.road.vertical;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadNetworkEngineeringValidator;
import com.plot.plugin.road.RoadNetworkValidationReport;
import com.plot.plugin.road.RoadTestFixtures;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.model.NetworkElevationMode;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkElevationAuthorityTest {

    @Test
    void defaultsToPerRoadWithoutUniformAuthority() {
        RoadNetwork network = new RoadNetwork();
        assertEquals(NetworkElevationMode.PER_ROAD, network.getElevationMode());
        assertFalse(NetworkElevationAuthority.isUniformFlat(network));
        assertEquals(64, NetworkElevationAuthority.effectiveUniformY(network));
    }

    @Test
    void resolveReturnsFlatWhenUniformModeEnabled() {
        RoadNetwork network = threeRoadNetwork();
        Road edited = network.getRoad("edited");
        assertEquals(ResolvedProfileAuthority.Kind.EDITED,
            ResolvedProfileAuthority.resolve(network, edited).kind());

        network.setElevationMode(NetworkElevationMode.UNIFORM_FLAT);
        network.setUniformElevationY(72);
        assertEquals(ResolvedProfileAuthority.Kind.FLAT,
            ResolvedProfileAuthority.resolve(network, edited).kind());
        assertEquals(ResolvedProfileAuthority.Kind.FLAT,
            ResolvedProfileAuthority.resolve(network, network.getRoad("auto")).kind());
        assertEquals(ResolvedProfileAuthority.Kind.FLAT,
            ResolvedProfileAuthority.resolve(network, network.getRoad("flat")).kind());
    }

    @Test
    void snapshotOverrideForcesUniformNodesAndFlatIntent() {
        RoadNetwork live = threeRoadNetwork();
        RoadNode mid = findNode(live, 40, 0);
        mid.setManualElevation(80.0);
        live.setElevationMode(NetworkElevationMode.UNIFORM_FLAT);
        live.setUniformElevationY(72);

        RoadNetwork snapshot = live.snapshot();
        NetworkElevationAuthority.applyUniformFlatToGenerationSnapshot(snapshot);

        for (RoadNode node : snapshot.getNodes().values()) {
            assertEquals(72.0, node.getManualElevation(), 1e-6);
        }
        for (Road road : snapshot.getRoads().values()) {
            assertEquals(RoadVerticalMode.FLAT, road.getVerticalMode());
            assertNotNull(road.getFlatVerticalIntent());
            assertEquals(72.0, road.getFlatVerticalIntent().getBaseElevation(), 1e-6);
            assertFalse(road.getFlatVerticalIntent().hasIntersectionOverrides());
        }

        assertEquals(80.0, mid.getManualElevation(), 1e-6);
        assertEquals(RoadVerticalMode.FIT_TERRAIN, live.getRoad("auto").getVerticalMode());
        assertEquals(ProfileSource.EDITED, live.getRoad("edited").getEffectiveProfileSource());
        assertNotNull(live.getRoad("edited").getVerticalAlignment());
    }

    @Test
    void gradeSeparationConflictBlocksPreGeneration() {
        RoadNetwork network = new RoadNetwork();
        Road roadA = network.createRoad("a");
        Road roadB = network.createRoad("b");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(40, 0));
        RoadNode c = network.createNode(new Vec2d(20, -20));
        RoadNode d = network.createNode(new Vec2d(20, 20));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), roadA.getId());
        network.createEdge(c.getId(), d.getId(), List.of(c.getPosition(), d.getPosition()), roadB.getId());
        network.registerCrossing(RoadCrossing.gradeSeparated(
            roadA.getId(), 20.0, roadB.getId(), 20.0, new Vec2d(20, 0), roadA.getId(), 4.0));

        network.setElevationMode(NetworkElevationMode.UNIFORM_FLAT);
        network.setUniformElevationY(70);

        assertTrue(NetworkElevationAuthority.hasGradeSeparationConflict(network));
        RoadNetworkValidationReport report =
            RoadNetworkEngineeringValidator.analyzePreGeneration(network);
        assertTrue(report.blocksBuild());
        assertTrue(report.items().stream().anyMatch(item ->
            "plugin.road.uniform_flat_grade_sep_conflict".equals(item.messageKey())));
    }

    @Test
    void schemaV4RoundTripsElevationMode() throws Exception {
        RoadNetwork network = threeRoadNetwork();
        network.setElevationMode(NetworkElevationMode.UNIFORM_FLAT);
        network.setUniformElevationY(75);

        RoadNetwork restored = RoadNetwork.fromJson(network.toJson());
        assertEquals(NetworkElevationMode.UNIFORM_FLAT, restored.getElevationMode());
        assertEquals(75, restored.getUniformElevationY());
        assertTrue(restored.toJson().contains("\"schemaVersion\": 4"));
    }

    @Test
    void currentSchemaDefaultsElevationModeWhenOmitted() throws Exception {
        String json = """
            {
              "schemaVersion": 4,
              "nodes": [],
              "edges": [],
              "roads": []
            }
            """;
        RoadNetwork loaded = RoadNetwork.fromJson(json);
        assertEquals(NetworkElevationMode.PER_ROAD, loaded.getElevationMode());
        assertEquals(64, loaded.getUniformElevationY());
    }

    private static RoadNode findNode(RoadNetwork network, double x, double y) {
        return network.getNodes().values().stream()
            .filter(n -> Math.hypot(n.getPosition().x - x, n.getPosition().y - y) < 1e-6)
            .findFirst()
            .orElseThrow();
    }

    private static RoadNetwork threeRoadNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road flat = network.createRoad("flat");
        flat.setVerticalMode(RoadVerticalMode.FLAT);
        flat.setFlatVerticalIntent(new FlatVerticalIntent(66.0));

        Road auto = network.createRoad("auto");
        auto.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);

        Road edited = network.createRoad("edited");
        edited.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        edited.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            new PointOfVerticalIntersection(0.0, 80.0),
            new PointOfVerticalIntersection(40.0, 90.0))));
        edited.setProfileSource(ProfileSource.EDITED);

        RoadNode n0 = network.createNode(new Vec2d(0, 0));
        RoadNode nMid = network.createNode(new Vec2d(40, 0));
        RoadNode n1 = network.createNode(new Vec2d(80, 0));
        RoadNode n2 = network.createNode(new Vec2d(40, 40));

        network.createEdge(n0.getId(), nMid.getId(), List.of(n0.getPosition(), nMid.getPosition()), flat.getId());
        network.createEdge(nMid.getId(), n1.getId(), List.of(nMid.getPosition(), n1.getPosition()), auto.getId());
        network.createEdge(nMid.getId(), n2.getId(), List.of(nMid.getPosition(), n2.getPosition()), edited.getId());
        RoadTestFixtures.confirmEditedProfileGeometry(network, edited);
        return network;
    }
}
