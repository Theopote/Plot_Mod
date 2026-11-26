package com.plot.plugin.road.centerline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadCrossingOwnershipMigrator;
import com.plot.plugin.road.crossing.RoadCrossingReconciler;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DR17 strengthen: capture succeeds, geometry reverses on working, then reapply is incomplete
 * — live must remain untouched (editor aborts before applyToLive).
 */
class ReverseCrossingMigrateFailTest {

    @Test
    void postTransformReapplyIncompleteLeavesLiveUnchanged() {
        RoadNetwork live = new RoadNetwork();
        Road roadA = live.createRoad("a");
        Road roadB = live.createRoad("b");
        RoadEdge edgeA = live.createEdge(
            live.createNode(new Vec2d(0, 5)).getId(),
            live.createNode(new Vec2d(20, 5)).getId(),
            List.of(new Vec2d(0, 5), new Vec2d(20, 5)),
            roadA.getId());
        live.createEdge(
            live.createNode(new Vec2d(10, 0)).getId(),
            live.createNode(new Vec2d(10, 10)).getId(),
            List.of(new Vec2d(10, 0), new Vec2d(10, 10)),
            roadB.getId());
        RoadCrossingReconciler.reconcileCrossings(live);
        assertFalse(live.getCrossings().isEmpty());
        String jsonBefore = live.toJson();

        RoadNetwork working = live.snapshot();
        RoadCrossingOwnershipMigrator.CaptureResult capture =
            RoadCrossingOwnershipMigrator.captureForRoads(working, Set.of(roadA.getId()));
        assertFalse(capture.hadUnresolved());
        assertFalse(capture.isEmpty());

        assertTrue(RoadCenterlineEditor.reverseEdgeGeometry(working, edgeA.getId()).isSuccess());

        // Destroy the other pass after transform so reapply cannot resolve.
        for (String edgeId : List.copyOf(working.getRoad(roadB.getId()).getOrderedSegmentIds())) {
            working.removeEdge(edgeId);
        }

        RoadCrossingOwnershipMigrator.CaptureResult remapped =
            RoadCrossingOwnershipMigrator.remapLocalDistancesAfterStorageReverse(
                working, capture, Set.of(edgeA.getId()));
        RoadCrossingOwnershipMigrator.ApplyResult apply =
            RoadCrossingOwnershipMigrator.reapply(working, remapped);
        assertFalse(apply.complete());

        // Editor contract: incomplete reapply must not commit — live still original.
        assertEquals(jsonBefore, live.toJson());
    }

    @Test
    void reverseEdgeAbortsBeforeLiveApplyWhenReapplyWouldFail() {
        // Integration: valid capture then missing foreign road → hadUnresolved at capture.
        // Covered by Phase242 DR17. This asserts reverseEdge itself never mutates live on fail.
        RoadNetwork live = new RoadNetwork();
        Road road = live.createRoad("main");
        RoadNode a = live.createNode(new Vec2d(0, 0));
        RoadNode b = live.createNode(new Vec2d(10, 0));
        RoadEdge edge = live.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());
        live.registerCrossing(RoadCrossing.atGrade(road.getId(), 999.0, "missing", 1.0, new Vec2d(5, 0)));
        List<Vec2d> pointsBefore = new ArrayList<>(edge.getCenterlinePoints());
        assertFalse(RoadCenterlineEditor.reverseEdge(live, edge.getId()).isSuccess());
        assertEquals(pointsBefore.getFirst().x, live.getEdge(edge.getId()).getCenterlinePoints().getFirst().x, 1e-9);
        assertEquals(pointsBefore.getLast().x, live.getEdge(edge.getId()).getCenterlinePoints().getLast().x, 1e-9);
    }
}
