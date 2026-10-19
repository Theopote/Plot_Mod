package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.model.Shape;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplodeJoinHandlerTest {

    @Test
    void explodeRectangleProducesFourSegments() {
        RectangleShape rectangle = new RectangleShape(new Vec2d(0, 0), 10, 5, 0);
        List<Shape> result = ExplodeJoinHandler.explodeShapes(List.of(rectangle));

        assertEquals(4, result.size());
        assertTrue(result.stream().allMatch(LineShape.class::isInstance));
        assertTrue(ExplodeJoinHandler.hasExplodeEffect(List.of(rectangle), result));
    }

    @Test
    void joinThreeConnectedLinesIntoOnePolyline() {
        LineShape line1 = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        LineShape line2 = new LineShape(new Vec2d(10, 0), new Vec2d(20, 0));
        LineShape line3 = new LineShape(new Vec2d(20, 0), new Vec2d(30, 0));
        List<Shape> original = List.of(line1, line2, line3);

        List<Shape> result = ExplodeJoinHandler.joinShapes(original);

        assertEquals(1, result.size());
        Shape joined = result.getFirst();
        assertInstanceOf(PolylineShape.class, joined);
        PolylineShape polyline = (PolylineShape) joined;
        assertEquals(4, polyline.getPoints().size());
        assertFalse(polyline.isClosed());
        assertTrue(ExplodeJoinHandler.hasJoinEffect(original, result));
    }

    @Test
    void joinLeavesUnconnectedSegmentsAsLines() {
        LineShape connectedA = new LineShape(new Vec2d(0, 0), new Vec2d(5, 0));
        LineShape connectedB = new LineShape(new Vec2d(5, 0), new Vec2d(10, 0));
        LineShape isolated = new LineShape(new Vec2d(0, 10), new Vec2d(5, 10));
        List<Shape> original = List.of(connectedA, connectedB, isolated);

        List<Shape> result = ExplodeJoinHandler.joinShapes(original);

        assertEquals(2, result.size());
        assertEquals(1, result.stream().filter(PolylineShape.class::isInstance).count());
        assertEquals(1, result.stream().filter(LineShape.class::isInstance).count());
    }
}
