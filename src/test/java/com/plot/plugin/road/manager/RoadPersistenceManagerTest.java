package com.plot.plugin.road.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.core.model.Project;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNetworkHistory;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPersistenceManagerTest {

    private Project previousProject;

    @BeforeEach
    void clearCurrentProject() {
        ApplicationContext app = ApplicationContext.getInstance();
        previousProject = app.getCurrentProject();
        // loadForCurrentProject() follows AppState's current project when set; clear so
        // this suite always exercises the default.json path regardless of prior tests.
        app.setCurrentProject(null);
    }

    @AfterEach
    void restoreCurrentProject() {
        ApplicationContext.getInstance().setCurrentProject(previousProject);
    }

    @Test
    void loadFailureBindsTargetAndClearsLiveNetwork(@TempDir Path dir) {
        RoadPersistenceManager manager = new RoadPersistenceManager(
            dir.toFile(),
            new RoadProjectStatus(),
            PluginContext.from(ApplicationContext.getInstance()));
        Path networks = dir.resolve("networks");
        try {
            Files.createDirectories(networks);
            Files.writeString(networks.resolve("default.json"), sampleNetwork().toJson());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        AtomicReference<RoadNetwork> loaded = new AtomicReference<>();
        manager.loadForCurrentProject(loaded::set, () -> {});
        assertNotNull(loaded.get(), "default.json must load when no current project is bound");
        assertTrue(
            loaded.get().getEdges().size() > 0,
            "default.json seed must have edges; got " + loaded.get().getEdges().size());

        AtomicReference<RoadNetwork> afterFail = new AtomicReference<>();
        manager.onProjectLoaded(
            "D:/projects/broken.plot",
            afterFail::set,
            () -> {});
        assertNotNull(afterFail.get());
        assertEquals(0, afterFail.get().getEdges().size(), "load failure must clear live network");
        assertEquals(
            com.plot.core.persistence.ProjectPathResolver.sidecarFileName("D:/projects/broken.plot"),
            manager.getCurrentNetworkFile());
    }

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
        history.stagePush(network);
        history.commitStagedPush();
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
