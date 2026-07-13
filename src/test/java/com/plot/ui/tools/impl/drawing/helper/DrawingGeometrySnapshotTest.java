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

    @Test
    void penSnapshotRoundTripsPathNodes() {
        PathNode corner = new PathNode(new Vec2d(0, 0));
        PathNode smooth = new PathNode(new Vec2d(10, 0));
        smooth.setSmoothControlPoints(new Vec2d(10, 5));

        DrawingGeometrySnapshot snapshot = DrawingGeometrySnapshot.pen(List.of(corner, smooth));
        DrawingGeometrySnapshot.PathNodeSnapshot nodeSnapshot = snapshot.getPathNodes().get(1);
        PathNode restored = nodeSnapshot.toPathNode();

        assertEquals(DrawingGeometrySnapshot.Kind.PEN, snapshot.getKind());
        assertEquals(PathNode.NodeType.SMOOTH, restored.getType());
        assertEquals(10.0, restored.getControlNext().x, 1e-6);
        assertEquals(5.0, restored.getControlNext().y, 1e-6);
        assertTrue(snapshot.sameGeometryAs(DrawingGeometrySnapshot.pen(List.of(
                new PathNode(new Vec2d(0, 0)),
                restored))));
    }

    @Test
    void penSnapshotsDifferWhenNodeGeometryChanges() {
        DrawingGeometrySnapshot before = DrawingGeometrySnapshot.pen(List.of(new PathNode(new Vec2d(0, 0))));
        PathNode moved = new PathNode(new Vec2d(20, 0));
        DrawingGeometrySnapshot after = DrawingGeometrySnapshot.pen(List.of(moved));

        assertFalse(before.sameGeometryAs(after));
    }
}
