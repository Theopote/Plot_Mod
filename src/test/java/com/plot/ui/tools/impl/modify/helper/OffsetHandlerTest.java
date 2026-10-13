package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.model.Shape;
import com.plot.core.context.ApplicationContext;
import com.plot.ui.tools.impl.modify.dto.ModifyParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OffsetHandlerTest {

    private OffsetHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OffsetHandler(ApplicationContext.getInstance().getAppState());
    }

    @Test
    void offsetPolylineMovesHorizontalSegmentUpward() {
        List<Vec2d> points = List.of(new Vec2d(0, 0), new Vec2d(10, 0));

        List<Vec2d> offset = OffsetHandler.offsetPolyline(points, 5);

        assertEquals(2, offset.size());
        assertEquals(0.0, offset.get(0).x, 1e-6);
        assertEquals(5.0, offset.get(0).y, 1e-6);
        assertEquals(10.0, offset.get(1).x, 1e-6);
        assertEquals(5.0, offset.get(1).y, 1e-6);
    }

    @Test
    void offsetPolylineReturnsEmptyForInsufficientPoints() {
        assertTrue(OffsetHandler.offsetPolyline(List.of(new Vec2d(0, 0)), 5).isEmpty());
        assertTrue(OffsetHandler.offsetPolyline(null, 5).isEmpty());
    }

    @Test
    void visitOffsetsLineByConfiguredDistance() {
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        handler.setOffsetParameters(4.0, null);

        LineShape offset = (LineShape) handler.visit(line);

        assertEquals(0.0, offset.getStart().x, 1e-6);
        assertEquals(4.0, offset.getStart().y, 1e-6);
        assertEquals(10.0, offset.getEnd().x, 1e-6);
        assertEquals(4.0, offset.getEnd().y, 1e-6);
    }

    @Test
    void visitExpandsCircleRadius() {
        CircleShape circle = new CircleShape(new Vec2d(0, 0), 10);
        handler.setOffsetParameters(3.0, null);

        CircleShape offset = (CircleShape) handler.visit(circle);

        assertEquals(13.0, offset.getRadius(), 1e-6);
    }

    @Test
    void calculateModifiedShapesOffsetsSelectedLine() {
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        ModifyParameters params = new ModifyParameters();
        params.setParameter("offsetDistance", 2.0);

        List<Shape> modified = handler.calculateModifiedShapes(List.of(line), params);

        assertEquals(1, modified.size());
        LineShape result = (LineShape) modified.getFirst();
        assertEquals(2.0, result.getStart().y, 1e-6);
        assertEquals(2.0, result.getEnd().y, 1e-6);
    }

    @Test
    void validateModificationRequiresDistanceOrPoint() {
        IModifyHandler.ValidationResult result = handler.validateModification(
                List.of(new LineShape(new Vec2d(0, 0), new Vec2d(10, 0))),
                new ModifyParameters());

        assertFalse(result.isValid());
    }

    @Test
    void createOffsetExpandsRegularPolygonWhenDistancePositive() {
        Polygon hexagon = regularPolygon(6, 10.0);
        double originalRadius = hexagon.getPoints().getFirst().distance(new Vec2d(0, 0));

        Shape offset = hexagon.createOffset(2.0);

        assertInstanceOf(Polygon.class, offset);
        double offsetRadius = ((Polygon) offset).getPoints().getFirst().distance(new Vec2d(0, 0));
        assertTrue(offsetRadius > originalRadius + 1.0,
                "正距离应外扩：original=" + originalRadius + ", offset=" + offsetRadius);
    }

    @Test
    void createOffsetInsetsRegularPolygonWhenDistanceNegative() {
        Polygon hexagon = regularPolygon(6, 10.0);
        double originalRadius = hexagon.getPoints().getFirst().distance(new Vec2d(0, 0));

        Shape offset = hexagon.createOffset(-2.0);

        assertInstanceOf(Polygon.class, offset);
        double offsetRadius = ((Polygon) offset).getPoints().getFirst().distance(new Vec2d(0, 0));
        assertTrue(offsetRadius < originalRadius - 1.0,
                "负距离应内缩：original=" + originalRadius + ", offset=" + offsetRadius);
    }

    private static Polygon regularPolygon(int sides, double radius) {
        List<Vec2d> points = new ArrayList<>(sides);
        for (int i = 0; i < sides; i++) {
            double angle = i * 2.0 * Math.PI / sides;
            points.add(new Vec2d(radius * Math.cos(angle), radius * Math.sin(angle)));
        }
        return new Polygon(points, true);
    }
}
