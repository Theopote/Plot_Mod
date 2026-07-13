package com.plot.core.snap;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.BoundingBox;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnapCalculatorTest {

    private SnapSettings settings;

    @BeforeEach
    void setUp() {
        settings = new SnapSettings();
        settings.resetToDefaults();
        settings.showSnapMarkers.set(false);
        settings.setSnapRadius(20f);
    }

    @Test
    void findNearestSnapPointSnapsToLineEndpoint() {
        Shape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        BoundingBox viewBounds = new BoundingBox(new Vec2d(-1000, -1000), new Vec2d(1000, 1000));
        SnapCalculator calculator = new SnapCalculator(settings, List.of(line), viewBounds);

        Vec2d snapped = calculator.findNearestSnapPoint(new Vec2d(1.5, 0.5));

        assertEquals(0.0, snapped.x, 1e-3);
        assertEquals(0.0, snapped.y, 1e-3);
        assertEquals(SnapPriorityEvaluator.SnapType.END_POINT, calculator.getSnapType());
    }

    @Test
    void findNearestSnapPointSnapsToLineMidpoint() {
        settings.endPointSnap.set(false);
        settings.midPointSnap.set(true);
        settings.nearestPointSnap.set(false);
        settings.vertexSnap.set(false);
        settings.centerPointSnap.set(false);
        settings.centroidSnap.set(false);
        settings.quadrantSnap.set(false);
        settings.gridPointSnap.set(false);
        settings.perpendicularSnap.set(false);
        settings.intersectionSnap.set(false);
        settings.controlPointSnap.set(false);
        settings.tangentPointSnap.set(false);
        settings.horizontalSnap.set(false);
        settings.verticalSnap.set(false);
        settings.parallelSnap.set(false);
        settings.extensionSnap.set(false);

        Shape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        BoundingBox viewBounds = new BoundingBox(new Vec2d(-1000, -1000), new Vec2d(1000, 1000));
        SnapCalculator calculator = new SnapCalculator(settings, List.of(line), viewBounds);

        Vec2d snapped = calculator.findNearestSnapPoint(new Vec2d(5.0, 1.0));

        assertEquals(5.0, snapped.x, 1e-3);
        assertEquals(0.0, snapped.y, 1e-3);
        assertEquals(SnapPriorityEvaluator.SnapType.MID_POINT, calculator.getSnapType());
    }
}
