package com.plot.plugin.powerline.path;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.ArcShape;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.geometry.shapes.SineCurveShape;
import com.plot.core.model.Shape;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLineSourceSyncTest {

    @Test
    void arcPathCanBeAdopted() {
        ArcShape arc = new ArcShape(new Vec2d(0, 0), 40.0, 0.0, Math.PI);
        PowerLineFootprint footprint = PowerLinePathLayout.adopt(arc, IdentityCoordinateService.INSTANCE);

        assertTrue(footprint.hasSourcePath());
        assertTrue(footprint.getPathPoints().size() >= 2);
    }

    @Test
    void detectsStaleSourceAfterShapeEdit() {
        SineCurveShape sine = new SineCurveShape(
            new Vec2d(0, 0),
            new Vec2d(100, 0),
            10.0,
            40.0,
            0.0);
        PowerLineFootprint footprint = PowerLinePathLayout.adopt(sine, IdentityCoordinateService.INSTANCE);
        List<Shape> shapes = List.of(sine);

        assertFalse(PowerLineSourceSync.isSourceStale(footprint, sine));

        sine.setEndPoint(new Vec2d(120, 0));
        assertTrue(PowerLineSourceSync.isSourceStale(footprint, sine));

        PowerLineSourceSync.relayout(footprint, sine, IdentityCoordinateService.INSTANCE);
        assertFalse(PowerLineSourceSync.isSourceStale(footprint, sine));
    }

    @Test
    void detectsMissingLinkedShape() {
        ArcShape arc = new ArcShape(new Vec2d(0, 0), 30.0, 0.0, Math.PI / 2.0);
        PowerLineFootprint footprint = PowerLinePathLayout.adopt(arc, IdentityCoordinateService.INSTANCE);

        assertTrue(PowerLineSourceSync.isSourceMissing(footprint, List.of()));
        assertFalse(PowerLineSourceSync.isSourceMissing(footprint, List.of(arc)));
    }

    @Test
    void resolvesUnsupportedDegenerateCircle() {
        CircleShape circle = new CircleShape(new Vec2d(0, 0), 30.0);
        PowerLineFootprint footprint = PowerLinePathLayout.adopt(circle, IdentityCoordinateService.INSTANCE);

        circle.setRadius(0.0);
        assertEquals(SourceSyncStatus.UNSUPPORTED, PowerLineSourceSync.resolveStatus(footprint, circle));
    }

    @Test
    void relayoutPreservesFootprintWhenGeometryBecomesInvalid() {
        PolylineShape triangle = new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(40, 0), new Vec2d(20, 30)),
            true);
        PowerLineFootprint footprint = PowerLinePathLayout.adopt(triangle, IdentityCoordinateService.INSTANCE);
        int fingerprint = footprint.getSourceDescriptor().fingerprint();
        int towerCount = footprint.getPathPoints().size();
        Vec2d firstTower = footprint.getPathPoints().getFirst().copy();

        triangle.setPoints(List.of(new Vec2d(0, 0), new Vec2d(20, 0), new Vec2d(40, 0)));
        assertThrows(
            ClosedLoopLayoutException.class,
            () -> PowerLineSourceSync.relayout(
                footprint,
                triangle,
                IdentityCoordinateService.INSTANCE));

        assertEquals(fingerprint, footprint.getSourceDescriptor().fingerprint());
        assertEquals(towerCount, footprint.getPathPoints().size());
        assertEquals(firstTower, footprint.getPathPoints().getFirst());
    }

    @Test
    void relinkChangesSourceWhilePreservingLineIdentity() {
        ArcShape original = new ArcShape(new Vec2d(0, 0), 40.0, 0.0, Math.PI);
        ArcShape replacement = new ArcShape(new Vec2d(80, 0), 25.0, 0.0, Math.PI / 2.0);
        PowerLineFootprint footprint = PowerLinePathLayout.adopt(original, IdentityCoordinateService.INSTANCE);
        String lineId = footprint.getId();
        String lineName = footprint.getName();
        footprint.setName("Test Line");
        footprint.addLayoutConstraint(
            new com.plot.plugin.powerline.model.PoleLayoutConstraint(10.0, "test"));

        PowerLineSourceSync.relink(footprint, replacement, IdentityCoordinateService.INSTANCE);

        assertEquals(lineId, footprint.getId());
        assertEquals("Test Line", footprint.getName());
        assertEquals(replacement.getId(), footprint.getSourceShapeId());
        assertTrue(footprint.getPathPoints().size() >= 2);
    }

    @Test
    void relinkPreservesFootprintWhenNewGeometryIsInvalid() {
        PolylineShape triangle = new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(40, 0), new Vec2d(20, 30)),
            true);
        PolylineShape degenerate = new PolylineShape(
            List.of(new Vec2d(0, 0), new Vec2d(20, 0), new Vec2d(40, 0)),
            true);
        PowerLineFootprint footprint = PowerLinePathLayout.adopt(triangle, IdentityCoordinateService.INSTANCE);
        int fingerprint = footprint.getSourceDescriptor().fingerprint();
        int towerCount = footprint.getPathPoints().size();

        assertThrows(
            ClosedLoopLayoutException.class,
            () -> PowerLineSourceSync.relink(
                footprint,
                degenerate,
                IdentityCoordinateService.INSTANCE));

        assertEquals(fingerprint, footprint.getSourceDescriptor().fingerprint());
        assertEquals(towerCount, footprint.getPathPoints().size());
        assertEquals(triangle.getId(), footprint.getSourceShapeId());
    }

    @Test
    void closedLoopConstraintDoesNotDuplicateTowerAcrossSeam() {
        PolylineShape square = new PolylineShape(
            List.of(
                new Vec2d(0, 0),
                new Vec2d(100, 0),
                new Vec2d(100, 100),
                new Vec2d(0, 100)),
            true);
        PowerLineFootprint footprint = PowerLinePathLayout.adopt(square, IdentityCoordinateService.INSTANCE);
        footprint.setMaxPoleSpacing(120.0);

        List<com.plot.plugin.powerline.model.PowerPoleSite> baseline =
            com.plot.plugin.powerline.PowerPoleLayoutUtils.computePoleSites(
                footprint,
                IdentityCoordinateService.INSTANCE);
        double perimeter = footprint.resolveSourcePath().worldLength(IdentityCoordinateService.INSTANCE);
        double seamStation = ClosedPathGeometry.normalizeStation(
            footprint.resolveSourcePath(),
            baseline.getFirst().getStationing() + perimeter - 1.0,
            perimeter);
        footprint.addLayoutConstraint(
            new com.plot.plugin.powerline.model.PoleLayoutConstraint(seamStation, "seam"));

        List<com.plot.plugin.powerline.model.PowerPoleSite> withConstraint =
            com.plot.plugin.powerline.PowerPoleLayoutUtils.computePoleSites(
                footprint,
                IdentityCoordinateService.INSTANCE);
        assertEquals(baseline.size(), withConstraint.size());
    }
}
