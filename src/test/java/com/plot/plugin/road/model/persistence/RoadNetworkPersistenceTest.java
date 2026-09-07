package com.plot.plugin.road.model.persistence;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkPersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void facadeRoundTripMatchesDirectJson() throws Exception {
        RoadNetwork network = sampleNetwork();
        String viaFacade = RoadNetworkPersistence.serialize(network);
        String direct = network.toJson();
        assertEquals(direct, viaFacade);

        RoadNetwork restored = RoadNetworkPersistence.deserialize(viaFacade);
        assertEquals(network.getEdges().size(), restored.getEdges().size());
        assertTrue(restored.validateInvariants().isValid());
    }

    @Test
    void facadeSnapshotIsolatedFromLiveNetwork() {
        RoadNetwork network = sampleNetwork();
        RoadNetwork snapshot = RoadNetworkPersistence.snapshot(network);

        network.getRoads().values().iterator().next().setName("mutated");
        assertNotEquals(
            network.getRoads().values().iterator().next().getName(),
            snapshot.getRoads().values().iterator().next().getName());
    }

    @Test
    void facadeSaveLoadPreservesContent() throws Exception {
        RoadNetwork network = sampleNetwork();
        Path file = tempDir.resolve("network.json");

        RoadNetworkPersistence.save(network, file);
        RoadNetwork loaded = RoadNetworkPersistence.load(file);

        assertEquals(network.getEdges().size(), loaded.getEdges().size());
        assertEquals(network.getNodes().size(), loaded.getNodes().size());
        assertTrue(loaded.validateInvariants().isValid());
    }

    private static RoadNetwork sampleNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("persist");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(20, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        return network;
    }
}
