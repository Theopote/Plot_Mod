package com.plot.plugin.powerline.path;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.ArcShape;
import com.plot.core.geometry.shapes.SineCurveShape;
import com.plot.core.model.Shape;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
