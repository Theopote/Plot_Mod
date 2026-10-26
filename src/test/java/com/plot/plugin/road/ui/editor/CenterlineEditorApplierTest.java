package com.plot.plugin.road.ui.editor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.centerline.CenterlineEditResult;
import com.plot.plugin.road.centerline.RoadCenterlineEditor;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class CenterlineEditorApplierTest {

    private RoadNetwork live;
    private String roadId;
    private String edgeId;

    @BeforeEach
    void setUp() {
        live = new RoadNetwork();
        Road road = live.createRoad("main");
        roadId = road.getId();
        RoadNode start = live.createNode(new Vec2d(0, 0));
        RoadNode end = live.createNode(new Vec2d(100, 0));
        RoadEdge edge = live.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)),
            roadId);
        edgeId = edge.getId();
    }

    @Test
    void hasChangesReturnsFalseWhenWorkingMatchesOpenSnapshot() {
        RoadNetwork openSnapshot = live.snapshot();
        RoadNetwork working = openSnapshot.snapshot();

        assertFalse(CenterlineEditorApplier.hasChanges(openSnapshot, working, roadId));
    }

    @Test
    void hasChangesDetectsInsertedPiOnWorkingCopy() {
        RoadNetwork openSnapshot = live.snapshot();
        RoadNetwork working = openSnapshot.snapshot();

        CenterlineEditResult result =
            RoadCenterlineEditor.insertPiAtLocalDistance(working, edgeId, 40.0);
        assertTrue(result.isSuccess());

        assertTrue(CenterlineEditorApplier.hasChanges(openSnapshot, working, roadId));
    }

    @Test
    void applyToLiveCopiesInsertedPiFromWorkingCopy() {
        RoadNetwork openSnapshot = live.snapshot();
        RoadNetwork working = openSnapshot.snapshot();
        assertTrue(RoadCenterlineEditor.insertPiAtLocalDistance(working, edgeId, 40.0).isSuccess());

        assertEquals(2, live.getEdge(edgeId).getCenterlinePoints().size());

        CenterlineEditorApplier.applyToLive(live, working, roadId);

        assertEquals(3, live.getEdge(edgeId).getCenterlinePoints().size());
        Vec2d inserted = live.getEdge(edgeId).getCenterlinePoints().get(1);
        assertEquals(40.0, inserted.x, 1e-6);
        assertEquals(0.0, inserted.y, 1e-6);
    }

    @Test
    void applyToLiveSyncsSplitTopologyFromWorkingCopy() {
        RoadNetwork openSnapshot = live.snapshot();
        RoadNetwork working = openSnapshot.snapshot();

        CenterlineEditResult split =
            RoadCenterlineEditor.splitAtLocalDistance(working, edgeId, 40.0);
        assertTrue(split.isSuccess());
        assertEquals(2, working.getRoad(roadId).getOrderedSegmentIds().size());

        CenterlineEditorApplier.applyToLive(live, working, roadId);

        assertEquals(2, live.getRoad(roadId).getOrderedSegmentIds().size());
        assertNotNull(live.getEdge(split.firstEdgeId()));
        assertNotNull(live.getEdge(split.secondEdgeId()));
        assertEquals(40.0, live.getEdge(split.firstEdgeId()).getLength(), 1e-6);
        assertEquals(60.0, live.getEdge(split.secondEdgeId()).getLength(), 1e-6);
        assertTrue(live.validateInvariants().isValid(), live.validateInvariants().violations().toString());
        assertNotNull(live.getNode(split.nodeId()));
    }

    @Test
    void applyToLiveSyncsMergeTopologyAndPreservesInvariants() {
        RoadNetwork mergeLive = new RoadNetwork();
        Road mergeRoad = mergeLive.createRoad("merge");
        String mergeRoadId = mergeRoad.getId();
        RoadNode n1 = mergeLive.createNode(new Vec2d(0, 0));
        RoadNode n2 = mergeLive.createNode(new Vec2d(50, 0));
        RoadNode n3 = mergeLive.createNode(new Vec2d(100, 0));
        mergeLive.createEdge(
            n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(50, 0)), mergeRoadId);
        mergeLive.createEdge(
            n2.getId(), n3.getId(), List.of(new Vec2d(50, 0), new Vec2d(100, 0)), mergeRoadId);

        RoadNetwork openSnapshot = mergeLive.snapshot();
        RoadNetwork working = openSnapshot.snapshot();

        CenterlineEditResult merge = RoadCenterlineEditor.mergeThroughNode(working, n2.getId());
        assertTrue(merge.isSuccess());
        assertEquals(1, working.getRoad(mergeRoadId).getOrderedSegmentIds().size());

        CenterlineEditorApplier.applyToLive(mergeLive, working, mergeRoadId);

        assertEquals(1, mergeLive.getRoad(mergeRoadId).getOrderedSegmentIds().size());
        assertEquals(
            100.0,
            mergeLive.getEdge(mergeLive.getRoad(mergeRoadId).getOrderedSegmentIds().getFirst()).getLength(),
            1e-6);
        assertTrue(mergeLive.validateInvariants().isValid(), mergeLive.validateInvariants().violations().toString());
        assertDoesNotThrow(() -> mergeLive.getNode(n2.getId()));
    }

    @Test
    void sessionConfirmAppliesCenterlineWhenRevisionMatches() {
        RoadNetworkManager manager = new RoadNetworkManager(new RoadSystemConfig("test"), new RoadProjectStatus());
        live = manager.getNetwork();
        Road road = live.createRoad("session");
        roadId = road.getId();
        RoadNode start = live.createNode(new Vec2d(0, 0));
        RoadNode end = live.createNode(new Vec2d(80, 0));
        RoadEdge edge = live.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(80, 0)),
            roadId);
        edgeId = edge.getId();

        RoadNetwork openSnapshot = live.snapshot();
        long revision = manager.getNetworkRevision();
        RoadNetworkEditorSession session = RoadNetworkEditorSession.open(
            live,
            revision,
            working -> CenterlineEditorApplier.hasChanges(openSnapshot, working, roadId));

        assertTrue(RoadCenterlineEditor.insertPiAtLocalDistance(
            session.workingNetwork(), edgeId, 30.0).isSuccess());

        AtomicBoolean applied = new AtomicBoolean(false);
        RoadNetworkEditorSession.ConfirmResult result = session.confirm(
            revision,
            (open, working) -> {
                CenterlineEditorApplier.applyToLive(live, working, roadId);
                applied.set(true);
            });

        assertEquals(RoadNetworkEditorSession.ConfirmResult.APPLIED, result);
        assertTrue(applied.get());
        assertEquals(3, live.getEdge(edgeId).getCenterlinePoints().size());
    }

    @Test
    void applyToLivePreservesEditedProfileAfterPiInsert() {
        RoadNetworkManager manager = new RoadNetworkManager(new RoadSystemConfig("test"), new RoadProjectStatus());
        live = manager.getNetwork();
        Road road = live.createRoad("profile");
        roadId = road.getId();
        RoadNode start = live.createNode(new Vec2d(0, 0));
        RoadNode end = live.createNode(new Vec2d(100, 0));
        RoadEdge edge = live.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)),
            roadId);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(100.0, 66.0))));
        ResolvedProfileAuthority.markEditedProfileConfirmed(live, road);

        RoadNetwork working = live.snapshot().snapshot();
        assertTrue(RoadCenterlineEditor.insertPiAtLocalDistance(working, edge.getId(), 40.0).isSuccess());

        ctxCommitCenterline(manager, working, roadId);
        manager.invalidateEditedProfilesIfGeometryChanged();

        assertEquals(ProfileSource.EDITED, road.getEffectiveProfileSource());
        assertEquals(2, road.getVerticalAlignment().pviCount());
        assertEquals(0.0, road.getVerticalAlignment().getPvis().getFirst().getStation(), 1e-6);
        assertEquals(100.0, road.getVerticalAlignment().getPvis().get(1).getStation(), 1e-6);
    }

    @Test
    void applyToLiveInvalidatesEditedProfileAfterSplit() {
        RoadNetworkManager manager = new RoadNetworkManager(new RoadSystemConfig("test"), new RoadProjectStatus());
        live = manager.getNetwork();
        Road road = live.createRoad("split-profile");
        roadId = road.getId();
        RoadNode start = live.createNode(new Vec2d(0, 0));
        RoadNode end = live.createNode(new Vec2d(100, 0));
        RoadEdge edge = live.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(100, 0)),
            roadId);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0.0, 64.0),
            PointOfVerticalIntersection.of(100.0, 66.0))));
        ResolvedProfileAuthority.markEditedProfileConfirmed(live, road);

        RoadNetwork working = live.snapshot().snapshot();
        assertTrue(RoadCenterlineEditor.splitAtLocalDistance(working, edge.getId(), 40.0).isSuccess());

        ctxCommitCenterline(manager, working, roadId);
        manager.invalidateEditedProfilesIfGeometryChanged();

        assertEquals(ProfileSource.AUTO, road.getEffectiveProfileSource());
        assertEquals(2, road.getOrderedSegmentIds().size());
    }

    private static void ctxCommitCenterline(RoadNetworkManager manager, RoadNetwork working, String roadId) {
        manager.beginNetworkEdit(com.plot.plugin.road.manager.RoadChangeKind.GEOMETRY);
        try {
            CenterlineEditorApplier.applyToLive(manager.getNetwork(), working, roadId);
            manager.finishNetworkEdit();
        } catch (RuntimeException ex) {
            manager.cancelPendingNetworkEdit();
            throw ex;
        }
    }

    @Test
    void sessionConfirmDetectsRevisionConflictForCenterlineEdits() {
        RoadNetworkManager manager = new RoadNetworkManager(new RoadSystemConfig("test"), new RoadProjectStatus());
        live = manager.getNetwork();
        Road road = live.createRoad("conflict");
        roadId = road.getId();
        RoadNode start = live.createNode(new Vec2d(0, 0));
        RoadNode end = live.createNode(new Vec2d(50, 0));
        RoadEdge edge = live.createEdge(
            start.getId(),
            end.getId(),
            List.of(new Vec2d(0, 0), new Vec2d(50, 0)),
            roadId);

        RoadNetwork openSnapshot = live.snapshot();
        long revision = manager.getNetworkRevision();
        RoadNetworkEditorSession session = RoadNetworkEditorSession.open(
            live,
            revision,
            working -> CenterlineEditorApplier.hasChanges(openSnapshot, working, roadId));

        assertTrue(RoadCenterlineEditor.insertPiAtLocalDistance(
            session.workingNetwork(), edge.getId(), 20.0).isSuccess());
        manager.pushHistory();

        AtomicBoolean applied = new AtomicBoolean(false);
        RoadNetworkEditorSession.ConfirmResult result = session.confirm(
            manager.getNetworkRevision(),
            (open, working) -> applied.set(true));

        assertEquals(RoadNetworkEditorSession.ConfirmResult.CONFLICT, result);
        assertFalse(applied.get());
    }
}
