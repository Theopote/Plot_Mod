package com.plot.ui.tools.impl.drawing.helper;

import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawingGeometrySnapshotTest {

    @Test
    void polylineSnapshotsCompareByPointGeometry() {
        DrawingGeometrySnapshot left = DrawingGeometrySnapshot.polyline(
                List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        DrawingGeometrySnapshot right = DrawingGeometrySnapshot.polyline(
                List.of(new Vec2d(0, 0), new Vec2d(10, 0)));
        DrawingGeometrySnapshot different = DrawingGeometrySnapshot.polyline(
                List.of(new Vec2d(0, 0), new Vec2d(20, 0)));

        assertTrue(left.sameGeometryAs(right));
        assertFalse(left.sameGeometryAs(different));
        assertEquals(left, right);
    }

    @Test
    void getPointsReturnsDefensiveCopy() {
        List<Vec2d> source = List.of(new Vec2d(0, 0), new Vec2d(10, 0));
        DrawingGeometrySnapshot snapshot = DrawingGeometrySnapshot.polyline(source);

        List<Vec2d> firstRead = snapshot.getPoints();
        List<Vec2d> secondRead = snapshot.getPoints();

        assertNotSame(firstRead, secondRead);
        assertEquals(10.0, secondRead.get(1).x, 1e-6);
        assertNotSame(source, firstRead);
    }

    @Test
    void bezierEditSnapshotIncludesCurveSegmentFlags() {
        DrawingGeometrySnapshot snapshot = DrawingGeometrySnapshot.bezierEdit(
                List.of(new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10)),
                List.of(true, false));

        assertEquals(DrawingGeometrySnapshot.Kind.BEZIER_EDIT, snapshot.getKind());
        assertEquals(List.of(true, false), snapshot.getCurveSegments());
        assertTrue(snapshot.sameGeometryAs(DrawingGeometrySnapshot.bezierEdit(
                List.of(new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10)),
                List.of(true, false))));
        assertFalse(snapshot.sameGeometryAs(DrawingGeometrySnapshot.bezierEdit(
                List.of(new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10)),
                List.of(false, false))));
    }
}
