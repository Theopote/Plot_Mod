package com.plot.plugin.road.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.ManualElevationOrigin;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkManualElevationPreservationTest {

    private RoadNetworkManager manager;
    private RoadPreviewManager previewManager;
    private RoadNetwork network;
    private RoadNode junction;

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("test"), new RoadProjectStatus());
        previewManager = new RoadPreviewManager(new RoadProjectStatus(), testHost());
        manager.setOnNetworkChanged(previewManager::handleNetworkChanged);
        network = manager.getNetwork();
        Road road = network.createRoad("main");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        junction = network.createNode(new Vec2d(40, 0));
        network.createEdge(
            start.getId(),
            junction.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(40, 0)),
            road.getId());
    }

    @Test
    void userManualElevationSurvivesPropertyEditAndPreviewInvalidate() {
        junction.setManualElevation(72.0, ManualElevationOrigin.USER);

        manager.mutateNetwork(() -> network.getRoads().values().iterator().next().setName("renamed"));
        previewManager.invalidatePreview();

        assertEquals(72.0, junction.getManualElevation());
        assertEquals(ManualElevationOrigin.USER, junction.getManualElevationOrigin());
    }

    @Test
    void userManualElevationSurvivesUndoRedo() {
        junction.setManualElevation(72.0, ManualElevationOrigin.USER);
        manager.pushHistory();
        junction.setManualElevation(80.0, ManualElevationOrigin.USER);

        manager.undo();
        junction = manager.getNetwork().getNode(junction.getId());
        assertEquals(72.0, junction.getManualElevation());

        manager.redo();
        junction = manager.getNetwork().getNode(junction.getId());
        assertEquals(80.0, junction.getManualElevation());
    }

    @Test
    void propagatedManualElevationClearedOnGeometryCommitOnly() {
        junction.setManualElevation(88.0, ManualElevationOrigin.PROFILE_PROPAGATED);

        previewManager.invalidatePreview();
        assertEquals(88.0, junction.getManualElevation());

        manager.pushHistory(RoadChangeKind.GEOMETRY);
        manager.commitNetworkChange(RoadChangeKind.GEOMETRY);
        assertNull(junction.getManualElevation());
    }

    @Test
    void failedMutationDoesNotCommitPartialChanges() {
        Road road = network.getRoads().values().iterator().next();
        String originalName = road.getName();
        long revisionBefore = manager.getNetworkRevision();

        try {
            manager.mutateNetwork(() -> {
                road.setName("partial");
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException ignored) {
            // expected
        }

        assertEquals(originalName, manager.getNetwork().getRoad(road.getId()).getName());
        assertEquals(revisionBefore, manager.getNetworkRevision());
        assertFalse(manager.canUndo());
    }

    private static PluginContext testHost() {
        return new PluginContext(
            ApplicationContext.getInstance().getAppState(),
            ApplicationContext.getInstance().getCommandService(),
            ApplicationContext.getInstance().getEventBus(),
            ApplicationContext.getInstance().getToolManager(),
            IdentityCoordinateService.INSTANCE,
            null,
            null,
            null);
    }
}
