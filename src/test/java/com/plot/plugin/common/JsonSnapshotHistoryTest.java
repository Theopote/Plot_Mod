package com.plot.plugin.common;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.RoadNetwork;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSnapshotHistoryTest {

    @Test
    void pushUndoRedoRoundTripsSnapshots() {
        JsonSnapshotHistory<RoadNetwork> history = new JsonSnapshotHistory<>(
            RoadNetwork::toJson,
            RoadNetwork::fromJson
        );

        RoadNetwork v1 = new RoadNetwork();
        v1.createNode(new Vec2d(0, 0));

        RoadNetwork v2 = new RoadNetwork();
        v2.createNode(new Vec2d(10, 0));

        history.push(v1);
        assertTrue(history.canUndo());
        assertFalse(history.canRedo());

        RoadNetwork undone = history.undo(v2);
        assertEquals(1, undone.getNodes().size());
        assertEquals(0, undone.getNodes().values().iterator().next().getPosition().x, 1e-6);
        assertTrue(history.canRedo());

        RoadNetwork redone = history.redo(undone);
        assertEquals(1, redone.getNodes().size());
        assertEquals(10, redone.getNodes().values().iterator().next().getPosition().x, 1e-6);
    }

    @Test
    void pushClearsRedoStack() {
        JsonSnapshotHistory<RoadNetwork> history = new JsonSnapshotHistory<>(
            RoadNetwork::toJson,
            RoadNetwork::fromJson
        );

        RoadNetwork a = new RoadNetwork();
        a.createNode(new Vec2d(1, 1));
        RoadNetwork b = new RoadNetwork();
        b.createNode(new Vec2d(2, 2));

        history.push(a);
        history.push(b);
        history.undo(b);
        assertTrue(history.canRedo());

        RoadNetwork c = new RoadNetwork();
        c.createNode(new Vec2d(3, 3));
        history.push(c);
        assertFalse(history.canRedo());
    }

    @Test
    void clearEmptiesBothStacks() {
        JsonSnapshotHistory<RoadNetwork> history = new JsonSnapshotHistory<>(
            RoadNetwork::toJson,
            RoadNetwork::fromJson,
            3
        );

        RoadNetwork network = new RoadNetwork();
        network.createNode(new Vec2d(0, 0));
        history.push(network);
        history.push(network);
        history.clear();

        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
    }
}
