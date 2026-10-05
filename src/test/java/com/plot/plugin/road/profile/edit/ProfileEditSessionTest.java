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
    void pviDragDoesNotMutateRoadUntilCommit() {
        double originalMid = road.getVerticalAlignment().getPvis().get(1).getElevation();
        session.beginPviEdit(road);
        ProfileControlPoint midPoint = new ProfileControlPoint(
            1, 50.0, 68.0, ProfilePointRole.INTERIOR_PVI, null, null, false, true);
        session.updatePviDrag(network, road, midPoint, 1, 50.0, 72.0);

        assertEquals(originalMid, road.getVerticalAlignment().getPvis().get(1).getElevation());
        assertTrue(session.isDirty());
        assertEquals(72.0, session.effectiveAlignment(road).getPvis().get(1).getElevation());

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
        assertEquals(319.0, session.effectiveAlignment(road).getPvis().get(1).getElevation());
    }

    @Test
    void numericElevationEditClampsBeforeCommit() {
        session.setElevationBounds(RoadWorldElevationBounds.fallback());
        session.beginNumericEdit(road);
        session.updatePviElevation(network, road, 1, 400.0);
        assertEquals(319.0, session.effectiveAlignment(road).getPvis().get(1).getElevation());
    }

    @Test
    void curveDragUpdatesDraftAlignmentOnly() {
        session.beginCurveEdit(road, 1);
        session.updateCurveLength(24.0);

        assertNotEquals(
            road.getVerticalAlignment().getPvis().get(1).getCurveLength(),
            session.effectiveAlignment(road).getPvis().get(1).getCurveLength());
        session.cancelEdit();
        assertFalse(session.isDirty());
    }
}
