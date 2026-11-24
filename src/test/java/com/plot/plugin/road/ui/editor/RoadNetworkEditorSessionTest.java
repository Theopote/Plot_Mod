package com.plot.plugin.road.ui.editor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNetworkEditorSessionTest {

    private RoadNetworkManager manager;
    private RoadNetwork live;
    private String roadId;

    @BeforeEach
    void setUp() {
        manager = new RoadNetworkManager(new RoadSystemConfig("test"), new RoadProjectStatus());
        live = manager.getNetwork();
        Road road = live.createRoad("main");
        roadId = road.getId();
        road.setWidth(10);
        RoadNode a = live.createNode(new Vec2d(0, 0));
        RoadNode b = live.createNode(new Vec2d(10, 0));
        live.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), roadId);
    }

    @Test
    void restoreRevertsWorkingCopy() {
        long revision = manager.getNetworkRevision();
        RoadNetworkEditorSession session = RoadNetworkEditorSession.open(
            live,
            revision,
            (open, working) -> CrossSectionEditorApplier.hasChanges(
                open, working, roadId, manager.getConfig()));

        session.workingNetwork().getRoad(roadId).setWidth(20);
        assertTrue(session.isDirty());

        session.restore();
        assertFalse(session.isDirty());
        assertEquals(10, session.workingNetwork().getRoad(roadId).getWidth());
    }

    @Test
    void sessionUndoRestoresPreviousWorkingCopy() {
        long revision = manager.getNetworkRevision();
        RoadNetworkEditorSession session = RoadNetworkEditorSession.open(
            live,
            revision,
            (open, working) -> working.getRoad(roadId).getWidth() != open.getRoad(roadId).getWidth());

        assertFalse(session.canUndo());
        session.pushUndoCheckpoint();
        session.workingNetwork().getRoad(roadId).setWidth(20);
        assertTrue(session.isDirty());
        assertTrue(session.canUndo());

        assertTrue(session.undo());
        assertEquals(10, session.workingNetwork().getRoad(roadId).getWidth());
        assertFalse(session.isDirty());
        assertFalse(session.canUndo());
        assertTrue(session.canRedo());

        assertTrue(session.redo());
        assertEquals(20, session.workingNetwork().getRoad(roadId).getWidth());
        assertTrue(session.isDirty());
        assertTrue(session.canUndo());
        assertFalse(session.canRedo());
    }

    @Test
    void failedMutationDoesNotPushEmptyUndoOrClearRedo() {
        long revision = manager.getNetworkRevision();
        RoadNetworkEditorSession session = RoadNetworkEditorSession.open(
            live,
            revision,
            (open, working) -> working.getRoad(roadId).getWidth() != open.getRoad(roadId).getWidth());

        session.pushUndoCheckpoint();
        session.workingNetwork().getRoad(roadId).setWidth(20);
        assertTrue(session.undo());
        assertTrue(session.canRedo());
        assertFalse(session.canUndo());

        Boolean failed = session.runWithUndoCheckpoint(
            () -> Boolean.FALSE,
            Boolean::booleanValue);
        assertFalse(failed);
        assertFalse(session.canUndo(), "failed edit must not leave empty undo step");
        assertTrue(session.canRedo(), "failed edit must keep redo stack");
        assertEquals(10, session.workingNetwork().getRoad(roadId).getWidth());
    }

    @Test
    void confirmDetectsRevisionConflict() {
        long revision = manager.getNetworkRevision();
        RoadNetworkEditorSession session = RoadNetworkEditorSession.open(
            live,
            revision,
            (open, working) -> CrossSectionEditorApplier.hasChanges(
                open, working, roadId, manager.getConfig()));
        session.workingNetwork().getRoad(roadId).setWidth(22);

        manager.pushHistory();
        AtomicBoolean applied = new AtomicBoolean(false);
        RoadNetworkEditorSession.ConfirmResult result = session.confirm(
            manager.getNetworkRevision(),
            (open, working) -> applied.set(true));

        assertEquals(RoadNetworkEditorSession.ConfirmResult.CONFLICT, result);
        assertFalse(applied.get());
    }

    @Test
    void resyncedSessionUsesCurrentLiveAsBaseline() {
        long staleRevision = manager.getNetworkRevision();
        RoadNetworkEditorSession session = RoadNetworkEditorSession.open(
            live,
            staleRevision,
            (open, working) -> CrossSectionEditorApplier.hasChanges(
                open, working, roadId, manager.getConfig()));
        session.workingNetwork().getRoad(roadId).setWidth(22);
        assertTrue(session.isDirty());

        live.getRoad(roadId).setWidth(15);
        manager.pushHistory();

        long resyncRevision = manager.getNetworkRevision();
        RoadNetworkEditorSession resynced = RoadNetworkEditorSession.open(
            live,
            resyncRevision,
            (open, working) -> CrossSectionEditorApplier.hasChanges(
                open, working, roadId, manager.getConfig()));

        assertFalse(resynced.isDirty());
        assertEquals(15, resynced.workingNetwork().getRoad(roadId).getWidth());

        resynced.workingNetwork().getRoad(roadId).setWidth(18);
        AtomicBoolean applied = new AtomicBoolean(false);
        RoadNetworkEditorSession.ConfirmResult result = resynced.confirm(
            resyncRevision,
            (open, working) -> {
                CrossSectionEditorApplier.applyToLive(live, working, roadId, manager.getConfig());
                applied.set(true);
            });

        assertEquals(RoadNetworkEditorSession.ConfirmResult.APPLIED, result);
        assertTrue(applied.get());
        assertEquals(18, live.getRoad(roadId).getWidth());
    }

    @Test
    void confirmAppliesChangesWhenRevisionMatches() {
        long revision = manager.getNetworkRevision();
        RoadSystemConfig config = manager.getConfig();
        RoadNetworkEditorSession session = RoadNetworkEditorSession.open(
            live,
            revision,
            (open, working) -> CrossSectionEditorApplier.hasChanges(
                open, working, roadId, config));
        session.workingNetwork().getRoad(roadId).setWidth(18);

        AtomicBoolean applied = new AtomicBoolean(false);
        RoadNetworkEditorSession.ConfirmResult result = session.confirm(
            revision,
            (open, working) -> {
                CrossSectionEditorApplier.applyToLive(live, working, roadId, config);
                applied.set(true);
            });

        assertEquals(RoadNetworkEditorSession.ConfirmResult.APPLIED, result);
        assertTrue(applied.get());
        assertEquals(18, live.getRoad(roadId).getWidth());
    }
}
