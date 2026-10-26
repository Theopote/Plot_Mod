package com.plot.plugin.road.ui.editor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadChangeKind;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingEditorCommitTest {

    private RoadNetworkManager manager;
    private RoadUiContext ctx;
    private String roadId;
    private int widthBeforeFailure;

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("test"), new RoadProjectStatus());
        RoadNetwork network = manager.getNetwork();
        Road road = network.createRoad("main");
        roadId = road.getId();
        road.setWidth(10);
        widthBeforeFailure = road.getWidth();
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), roadId);

        ApplicationContext applicationContext = ApplicationContext.getInstance();
        PluginContext host = new PluginContext(
            applicationContext.getAppState(),
            applicationContext.getCommandService(),
            applicationContext.getEventBus(),
            applicationContext.getToolManager(),
            IdentityCoordinateService.INSTANCE,
            null,
            null,
            null);
        ctx = new RoadUiContext(
            manager,
            null,
            null,
            null,
            new RoadProjectStatus(),
            host);
    }

    @Test
    void commitNetworkEditRollsBackWhenMutationFails() {
        long revisionBefore = manager.getNetworkRevision();
        AtomicInteger revisionNotifications = new AtomicInteger();
        manager.setOnNetworkChanged(kind -> revisionNotifications.incrementAndGet());

        assertThrows(IllegalStateException.class, () -> ctx.commitNetworkEdit(
            RoadChangeKind.CROSS_SECTION,
            () -> {
                manager.getNetwork().getRoad(roadId).setWidth(99);
                throw new IllegalStateException("apply failed");
            }));

        assertEquals(widthBeforeFailure, manager.getNetwork().getRoad(roadId).getWidth());
        assertEquals(revisionBefore, manager.getNetworkRevision());
        assertFalse(manager.hasPendingNetworkEdit());
        assertEquals(0, revisionNotifications.get());
        assertTrue(manager.getNetwork().validateInvariants().isValid());
    }

    @Test
    void commitNetworkEditRollsBackWhenFinishFails() {
        long revisionBefore = manager.getNetworkRevision();
        AtomicInteger revisionNotifications = new AtomicInteger();
        manager.setOnNetworkChanged(kind -> revisionNotifications.incrementAndGet());

        assertThrows(IllegalStateException.class, () -> commitNetworkEditSimulatingFinishFailure(
            ctx,
            RoadChangeKind.CROSS_SECTION,
            () -> manager.getNetwork().getRoad(roadId).setWidth(99)));

        assertEquals(widthBeforeFailure, manager.getNetwork().getRoad(roadId).getWidth());
        assertEquals(revisionBefore, manager.getNetworkRevision());
        assertFalse(manager.hasPendingNetworkEdit());
        assertEquals(0, revisionNotifications.get());
        assertTrue(manager.getNetwork().validateInvariants().isValid());
    }

    @Test
    void commitNetworkEditCommitsOnSuccess() {
        long revisionBefore = manager.getNetworkRevision();

        ctx.commitNetworkEdit(
            RoadChangeKind.CROSS_SECTION,
            () -> manager.getNetwork().getRoad(roadId).setWidth(14));

        assertEquals(14, manager.getNetwork().getRoad(roadId).getWidth());
        assertEquals(revisionBefore + 1, manager.getNetworkRevision());
        assertFalse(manager.hasPendingNetworkEdit());
    }

    /** Mirrors {@link RoadUiContext#commitNetworkEdit} when {@code finishNetworkEdit()} throws. */
    private static void commitNetworkEditSimulatingFinishFailure(
            RoadUiContext ctx,
            RoadChangeKind kind,
            Runnable mutation) {
        ctx.networkManager().beginNetworkEdit(kind);
        try {
            mutation.run();
            throw new IllegalStateException("finish failed");
        } catch (RuntimeException ex) {
            ctx.networkManager().cancelPendingNetworkEdit();
            throw ex;
        }
    }
}
