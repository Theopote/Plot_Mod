package com.plot.plugin.road.manager;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.centerline.CenterlineEditStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkMutationTransactionTest {

    private RoadNetworkManager manager;
    private final AtomicInteger changeNotifications = new AtomicInteger();

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("test"), new RoadProjectStatus());
        manager.setOnNetworkChanged(changeNotifications::incrementAndGet);
    }

    @Test
    void pushHistoryIncrementsRevisionOnce() {
        long before = manager.getNetworkRevision();
        manager.pushHistory();
        assertEquals(before + 1, manager.getNetworkRevision());
        assertEquals(1, changeNotifications.get());
    }

    @Test
    void mutateNetworkIncrementsRevisionOnce() {
        RoadNetwork network = manager.getNetwork();
        Road road = network.createRoad("main");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());

        long before = manager.getNetworkRevision();
        manager.mutateNetwork(() -> network.getRoad(road.getId()).setName("renamed"));
        assertEquals(before + 1, manager.getNetworkRevision());
        assertEquals("renamed", network.getRoad(road.getId()).getName());
        assertEquals(1, changeNotifications.get());
    }

    @Test
    void splitUndoSnapshotAndCommitIncrementsRevisionOnce() {
        long before = manager.getNetworkRevision();
        manager.pushUndoSnapshot();
        assertEquals(before, manager.getNetworkRevision());
        manager.commitNetworkChange();
        assertEquals(before + 1, manager.getNetworkRevision());
        assertEquals(1, changeNotifications.get());
    }

    @Test
    void failedCenterlineEditDoesNotCommitRevision() {
        long before = manager.getNetworkRevision();
        CenterlineEditResult result = manager.insertPiAtLocalDistance("missing-edge", 1.0);
        assertFalse(result.isSuccess());
        assertEquals(CenterlineEditStatus.EDGE_NOT_FOUND, result.status());
        assertEquals(before, manager.getNetworkRevision());
        assertEquals(0, changeNotifications.get());
        assertTrue(manager.canUndo());
    }

    @Test
    void reconcileIntersectionsIncrementsRevisionOnce() {
        RoadNetwork network = manager.getNetwork();
        Road road = network.createRoad("cross");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        RoadNode c = network.createNode(new Vec2d(5, -5));
        RoadNode d = network.createNode(new Vec2d(5, 5));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        network.createEdge(c.getId(), d.getId(), List.of(new Vec2d(5, -5), new Vec2d(5, 5)), road.getId());

        long before = manager.getNetworkRevision();
        manager.reconcileIntersections();
        assertEquals(before + 1, manager.getNetworkRevision());
        assertEquals(1, changeNotifications.get());
    }
}
