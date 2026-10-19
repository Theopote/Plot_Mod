package com.plot.core.geometry.shapes;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import org.junit.jupiter.api.Test;

import java.awt.geom.GeneralPath;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextShapeTest {

    @Test
    void convertAwtShapeSplitsIndependentSubpaths() throws Exception {
        GeneralPath path = new GeneralPath();
        path.moveTo(0, 0);
        path.lineTo(10, 0);
        path.moveTo(20, 0);
        path.lineTo(30, 0);

        @SuppressWarnings("unchecked")
        List<PolylineShape> shapes = (List<PolylineShape>) invokeConvert(path);

        assertEquals(2, shapes.size());
        assertFalse(shapes.get(0).isClosed());
        assertFalse(shapes.get(1).isClosed());
        assertEquals(0.0, shapes.get(0).getPoints().getFirst().x, 1e-6);
        assertEquals(20.0, shapes.get(1).getPoints().getFirst().x, 1e-6);
    }

    @Test
    void convertAwtShapeCloseSegmentEndsSubpath() throws Exception {
        GeneralPath path = new GeneralPath();
        path.moveTo(0, 0);
        path.lineTo(5, 0);
        path.lineTo(5, 5);
        path.closePath();
        path.moveTo(10, 0);
        path.lineTo(15, 0);

        @SuppressWarnings("unchecked")
        List<PolylineShape> shapes = (List<PolylineShape>) invokeConvert(path);

        assertEquals(2, shapes.size());
    }

    @Test
    void convertToGraphicsProducesStrokePathsForText() {
        TextShape textShape = new TextShape(new Vec2d(0, 0), "A");
        textShape.setFontSize(24);

        List<Shape> graphics = textShape.convertToGraphics();

        assertFalse(graphics.isEmpty());
        assertTrue(graphics.stream().allMatch(PolylineShape.class::isInstance));
    }

    @Test
    void emptyTextReturnsNoGraphics() {
        TextShape textShape = new TextShape(new Vec2d(0, 0), "");

        assertTrue(textShape.convertToGraphics().isEmpty());
    }

    private static Object invokeConvert(java.awt.Shape awtShape) throws Exception {
        Method method = TextShape.class.getDeclaredMethod("convertAwtShapeToPolylineShapes", java.awt.Shape.class);
        method.setAccessible(true);
        TextShape holder = new TextShape(new Vec2d(0, 0), "x");
        return method.invoke(holder, awtShape);
    }
}
