package com.plot.plugin.road.profile.edit;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.profile.ProfileControlPoint;
import com.plot.plugin.road.profile.ProfilePointRole;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadElevationBounds;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.plugin.road.vertical.RoadWorldElevationBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileEditSessionTest {

    private RoadNetwork network;
    private Road road;
    private ProfileEditSession session;

    @BeforeEach
    void setUp() {
        network = new RoadNetwork();
        road = network.createRoad("road-a");
        RoadNode n1 = network.createNode(new Vec2d(0, 0));
        RoadNode n2 = network.createNode(new Vec2d(100, 0));
        network.createEdge(n1.getId(), n2.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        RoadVerticalAlignment alignment = new RoadVerticalAlignment();
        alignment.addPvi(new PointOfVerticalIntersection(0.0, 64.0));
        alignment.addPvi(PointOfVerticalIntersection.withCurve(50.0, 68.0, 8.0));
        alignment.addPvi(new PointOfVerticalIntersection(100.0, 66.0));
        road.setVerticalAlignment(alignment);
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);

        session = new ProfileEditSession();
    }

    @Test
    void lockedPviCannotBeDraggedOrChangedNumerically() {
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64).withConstraint(
                com.plot.plugin.road.vertical.VerticalControlPointConstraint.USER_LOCKED),
            PointOfVerticalIntersection.of(100, 66))));
        var point = com.plot.plugin.road.vertical.VerticalProfileControlPoints.forRoad(network, road).getFirst();
        assertFalse(point.elevationEditable());
        session.beginPviEdit(road);
        session.updatePviDrag(network, road, point, 0, 0, 80);
        session.updatePviElevation(network, road, 0, 90);
        assertEquals(64, session.effectiveAlignment(road).getPvis().getFirst().getElevation());
        assertFalse(session.isDirty());
    }

    @Test
    void numericEditKeepsShortRoadFlatAndPreservesLoopSeam() {
        var shortNetwork = new RoadNetwork();
        var shortRoad = shortNetwork.createRoad("short");
        var a = shortNetwork.createNode(new Vec2d(0, 0));
        var b = shortNetwork.createNode(new Vec2d(3, 0));
        shortNetwork.createEdge(a.getId(), b.getId(), List.of(a.getPosition(), b.getPosition()), shortRoad.getId());
        shortRoad.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64), PointOfVerticalIntersection.of(3, 64))));
        shortRoad.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        session.beginNumericEdit(shortRoad);
        session.updatePviElevation(shortNetwork, shortRoad, 0, 70);
        assertTrue(session.effectiveAlignment(shortRoad).getPvis().stream().allMatch(p -> p.getElevation() == 70));
        session.cancelEdit();
        road.setTopologyMode(com.plot.plugin.road.model.RoadTopologyMode.LOOP);
        session.beginNumericEdit(road);
        session.updatePviElevation(network, road, 0, 72);
        assertEquals(72, session.effectiveAlignment(road).getPvis().getFirst().getElevation());
        assertEquals(72, session.effectiveAlignment(road).getPvis().getLast().getElevation());
    }

    @Test
    void pviDragDoesNotMutateRoadUntilCommit() {
        double originalMid = road.getVerticalAlignment().getPvis().get(1).getElevation();
        session.beginPviEdit(road);
        ProfileControlPoint midPoint = new ProfileControlPoint(
            1, 50.0, 68.0, ProfilePointRole.INTERIOR_PVI, null, null, false, true);
        session.updatePviDrag(network, road, midPoint, 1, 50.0, 72.0);

        assertEquals(originalMid, road.getVerticalAlignment().getPvis().get(1).getElevation());
        assertTrue(session.isDirty());
        assertEquals(72.0, Objects.requireNonNull(session.effectiveAlignment(road)).getPvis().get(1).getElevation());

        session.cancelEdit();
        assertFalse(session.isActive());
        assertEquals(originalMid, road.getVerticalAlignment().getPvis().get(1).getElevation());
    }

    @Test
    void pviDragClampsElevationToWorldBounds() {
        session.setElevationBounds(new RoadElevationBounds(-64, 319));
        session.beginPviEdit(road);
        ProfileControlPoint midPoint = new ProfileControlPoint(
            1, 50.0, 68.0, ProfilePointRole.INTERIOR_PVI, null, null, false, true);
        session.updatePviDrag(network, road, midPoint, 1, 50.0, 500.0);
        assertEquals(319.0, Objects.requireNonNull(session.effectiveAlignment(road)).getPvis().get(1).getElevation());
    }

    @Test
    void numericElevationEditClampsBeforeCommit() {
        session.setElevationBounds(RoadWorldElevationBounds.fallback());
        session.beginNumericEdit(road);
        session.updatePviElevation(network, road, 1, 400.0);
        assertEquals(319.0, Objects.requireNonNull(session.effectiveAlignment(road)).getPvis().get(1).getElevation());
    }

    @Test
    void commitEditWritesWorkingCopyWithoutMutatingLiveRoad() {
        ProfileEditorWindowSession windowSession = ProfileEditorWindowSession.open(network, 0L);
        RoadNetwork working = windowSession.editingNetwork();
        Road workingRoad = working.getRoad(road.getId());
        RoadSystemConfig config = new RoadSystemConfig("test");
        double originalMid = road.getVerticalAlignment().getPvis().get(1).getElevation();

        session.beginPviEdit(workingRoad);
        ProfileControlPoint midPoint = new ProfileControlPoint(
            1, 50.0, 68.0, ProfilePointRole.INTERIOR_PVI, null, null, false, true);
        session.updatePviDrag(working, workingRoad, midPoint, 1, 50.0, 73.0);
        session.commitEdit(working, workingRoad, config, windowSession, null, null);

        assertEquals(originalMid, road.getVerticalAlignment().getPvis().get(1).getElevation());
        assertEquals(73.0, workingRoad.getVerticalAlignment().getPvis().get(1).getElevation());
        assertTrue(windowSession.isDirty());
    }

    @Test
    void curveDragUpdatesDraftAlignmentOnly() {
        session.beginCurveEdit(road, 1);
        session.updateCurveLength(24.0);

        assertNotEquals(
            road.getVerticalAlignment().getPvis().get(1).getCurveLength(),
            Objects.requireNonNull(session.effectiveAlignment(road)).getPvis().get(1).getCurveLength());
        session.cancelEdit();
        assertFalse(session.isDirty());
    }
}
