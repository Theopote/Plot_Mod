package com.plot.ui.tools.impl.modify.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.BoundingBox;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.model.Shape;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlignPositionStrategiesTest {

    @Test
    void leftAlignStrategyAlignsMinXToReference() {
        AlignPositionStrategy strategy = AlignPositionStrategies.getStrategy("LEFT");
        Shape shape = new RectangleShape(new Vec2d(20, 5), 10, 10, 0);
        BoundingBox reference = new BoundingBox(0, 0, 50, 50);

        Vec2d aligned = strategy.calculate(shape, reference, shape.getPosition(), List.of(shape), 10);

        assertEquals(0.0, aligned.x, 1e-6);
        assertEquals(5.0, aligned.y, 1e-6);
    }

    @Test
    void rightAlignStrategyAlignsMaxXToReference() {
        AlignPositionStrategy strategy = AlignPositionStrategies.getStrategy("RIGHT");
        Shape shape = new RectangleShape(new Vec2d(0, 0), 10, 10, 0);
        BoundingBox reference = new BoundingBox(0, 0, 50, 50);

        Vec2d aligned = strategy.calculate(shape, reference, shape.getPosition(), List.of(shape), 10);

        assertEquals(40.0, aligned.x, 1e-6);
        assertEquals(0.0, aligned.y, 1e-6);
    }

    @Test
    void centerAlignStrategyCentersWithinReference() {
        AlignPositionStrategy strategy = AlignPositionStrategies.getStrategy("CENTER");
        Shape shape = new RectangleShape(new Vec2d(0, 0), 10, 10, 0);
        BoundingBox reference = new BoundingBox(0, 0, 50, 50);

        Vec2d aligned = strategy.calculate(shape, reference, shape.getPosition(), List.of(shape), 10);

        assertEquals(20.0, aligned.x, 1e-6);
        assertEquals(0.0, aligned.y, 1e-6);
    }

    @Test
    void unknownModeFallsBackToLeftAlign() {
        AlignPositionStrategy strategy = AlignPositionStrategies.getStrategy("UNKNOWN_MODE");
        Shape shape = new RectangleShape(new Vec2d(15, 0), 10, 10, 0);
        BoundingBox reference = new BoundingBox(0, 0, 50, 50);

        Vec2d aligned = strategy.calculate(shape, reference, shape.getPosition(), List.of(shape), 10);

        assertEquals(0.0, aligned.x, 1e-6);
    }
}
