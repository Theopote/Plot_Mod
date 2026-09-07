package com.plot.plugin.road.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNetworkHistory;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPersistenceManagerTest {

    @Test
    void saveAndLoadNetworkFileRoundTrip(@TempDir Path dir) throws Exception {
        RoadPersistenceManager manager = new RoadPersistenceManager(
            dir.toFile(),
            new RoadProjectStatus(),
            PluginContext.from(ApplicationContext.getInstance()));
        RoadNetwork network = sampleNetwork();
        Path file = dir.resolve("networks/persist.json");
        Files.createDirectories(file.getParent());

        assertTrue(manager.saveNetworkFile(file, network));

        RoadNetworkHistory history = new RoadNetworkHistory();
        history.push(network);
        RoadNetwork loaded = manager.loadNetworkFile(file, history, () -> {});

        assertNotNull(loaded);
        assertEquals(network.getEdges().size(), loaded.getEdges().size());
        assertTrue(loaded.validateInvariants().isValid());
    }

    private static RoadNetwork sampleNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("manager-persist");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(20, 0));
        network.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), road.getId());
        return network;
    }
}
