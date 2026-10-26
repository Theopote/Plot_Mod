package com.plot.plugin.road.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadEditSlopeOverrideInvalidationTest {

    private RoadNetworkManager manager;
    private RoadPreviewManager previewManager;
    private RoadNetwork network;
    private RoadEdge edge;
    private final AtomicReference<RoadChangeKind> lastChangeKind = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("test"), new RoadProjectStatus());
        previewManager = new RoadPreviewManager(new RoadProjectStatus(), testHost());
        manager.setOnNetworkChanged(kind -> {
            lastChangeKind.set(kind);
            previewManager.handleNetworkChanged(kind);
        });
        network = manager.getNetwork();
        Road road = network.createRoad("main");
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(100, 0));
        edge = network.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)),
            road.getId());
    }

    @Test
    void updateRoadSlopeOverridesIncrementsRevisionAndNotifiesGenerationSettings() {
        long before = manager.getNetworkRevision();
        List<RoadEdge.SlopeOverride> overrides = List.of(
            new RoadEdge.SlopeOverride(0, 50, 4.0f));

        assertTrue(manager.updateRoadSlopeOverrides(edge.getId(), overrides));

        assertEquals(before + 1, manager.getNetworkRevision());
        assertEquals(RoadChangeKind.GENERATION_SETTINGS, lastChangeKind.get());
        assertEquals(1, edge.getSlopeOverrides().size());
        assertEquals(4.0f, edge.getSlopeOverrides().getFirst().maxSlope, 1e-3f);
    }

    @Test
    void dragCommitIncrementsRevisionOnce() {
        long before = manager.getNetworkRevision();
        manager.beginNetworkEdit(RoadChangeKind.GENERATION_SETTINGS);
        manager.commitRoadSlopeOverrides(
            edge.getId(),
            List.of(new RoadEdge.SlopeOverride(10, 90, 3.0f)));

        assertEquals(before + 1, manager.getNetworkRevision());
        assertEquals(RoadChangeKind.GENERATION_SETTINGS, lastChangeKind.get());
    }

    @Test
    void undoRestoresSlopeOverrides() {
        List<RoadEdge.SlopeOverride> first = List.of(
            new RoadEdge.SlopeOverride(0, 40, 5.0f));
        List<RoadEdge.SlopeOverride> second = List.of(
            new RoadEdge.SlopeOverride(0, 80, 2.0f));

        manager.updateRoadSlopeOverrides(edge.getId(), first);
        manager.updateRoadSlopeOverrides(edge.getId(), second);
        RoadEdge current = manager.getNetwork().getEdge(edge.getId());
        assertEquals(1, current.getSlopeOverrides().size());
        assertEquals(80.0, current.getSlopeOverrides().getFirst().endDistance, 1e-3);

        manager.undo();
        current = manager.getNetwork().getEdge(edge.getId());
        assertEquals(1, current.getSlopeOverrides().size());
        assertEquals(40.0, current.getSlopeOverrides().getFirst().endDistance, 1e-3);
    }

    @Test
    void cancelPendingNetworkEditDiscardsStagedUndoWithoutRevisionBump() {
        long before = manager.getNetworkRevision();
        manager.beginNetworkEdit(RoadChangeKind.GENERATION_SETTINGS);

        assertTrue(manager.hasPendingNetworkEdit());
        manager.cancelPendingNetworkEdit();

        assertFalse(manager.hasPendingNetworkEdit());
        assertEquals(before, manager.getNetworkRevision());
        assertFalse(manager.canUndo());
    }

    @Test
    void unchangedDraftCommitCancelsPendingEditWithoutRevisionBump() {
        List<RoadEdge.SlopeOverride> existing = List.of(
            new RoadEdge.SlopeOverride(0, 50, 5.0f));
        manager.updateRoadSlopeOverrides(edge.getId(), existing);
        long before = manager.getNetworkRevision();

        manager.beginNetworkEdit(RoadChangeKind.GENERATION_SETTINGS);
        manager.commitRoadSlopeOverrides(edge.getId(), existing);

        assertFalse(manager.hasPendingNetworkEdit());
        assertEquals(before, manager.getNetworkRevision());
    }

    @Test
    void editedProfileFallsBackToAutoButKeepsPvisWhenConstraintsViolated() {
        Road road = network.getRoad(edge.getRoadId());
        road.setMaxSlope(5.0f);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(100.0, 90.0))));
        ResolvedProfileAuthority.markEditedProfileConfirmed(network, road);
        assertEquals(ProfileSource.EDITED, road.getEffectiveProfileSource());
        int pviCount = road.getVerticalAlignment().pviCount();

        manager.updateRoadSlopeOverrides(
            edge.getId(),
            List.of(new RoadEdge.SlopeOverride(0, 100, 5.0f)));

        assertEquals(ProfileSource.AUTO, road.getEffectiveProfileSource());
        assertEquals(pviCount, road.getVerticalAlignment().pviCount());
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
