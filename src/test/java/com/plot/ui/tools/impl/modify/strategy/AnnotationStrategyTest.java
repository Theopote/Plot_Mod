package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.AnnotationShape;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.AnnotationTool;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy.ModifyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationStrategyTest {

    private static final int MOUSE_LEFT = 0;
    private static final int MOUSE_RIGHT = 1;

    private AppState appState;
    private AnnotationStrategy strategy;
    private ModifyStrategyTestContext context;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        strategy = new AnnotationStrategy(AnnotationTool.AnnotationMode.DISTANCE);
        context = new ModifyStrategyTestContext(appState);
    }

    @Test
    void distanceModeCreatesAnnotationAfterTwoClicks() {
        strategy.onMouseDown(new Vec2d(0, 0), MOUSE_LEFT, context);
        ModifyResult result = strategy.onMouseDown(new Vec2d(10, 0), MOUSE_LEFT, context);

        assertEquals(ModifyResult.COMPLETE, result);
        assertEquals(1, context.getExecutedCommands().size());
        ModifyCommand command = context.getExecutedCommands().getFirst();
        assertEquals(1, command.getNewShapes().size());
        assertInstanceOf(AnnotationShape.class, command.getNewShapes().getFirst());
    }

    @Test
    void distanceModeRightClickCancelsMeasurement() {
        strategy.onMouseDown(new Vec2d(0, 0), MOUSE_LEFT, context);

        ModifyResult result = strategy.onMouseDown(new Vec2d(5, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals("status.plot.select.distance_cancelled", context.getLastStatusMessage());
        assertTrue(context.getExecutedCommands().isEmpty());
    }

    @Test
    void angleModeCreatesAnnotationForTwoSelectedLines() throws Exception {
        strategy.setMode(AnnotationTool.AnnotationMode.ANGLE);
        LineShape line1 = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        LineShape line2 = new LineShape(new Vec2d(5, -5), new Vec2d(5, 5));
        appState.addShape(line1);
        appState.addShape(line2);
        injectSelection(line1, line2);

        ModifyResult result = strategy.onMouseDown(new Vec2d(5, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.COMPLETE, result);
        assertEquals(1, context.getExecutedCommands().size());
        assertInstanceOf(AnnotationShape.class, context.getExecutedCommands().getFirst().getNewShapes().getFirst());
    }

    @Test
    void radiusModeCreatesAnnotationForCircle() throws Exception {
        strategy.setMode(AnnotationTool.AnnotationMode.RADIUS);
        CircleShape circle = new CircleShape(new Vec2d(0, 0), 8);
        appState.addShape(circle);
        injectSelection(circle);

        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.COMPLETE, result);
        assertEquals(1, context.getExecutedCommands().size());
        assertInstanceOf(AnnotationShape.class, context.getExecutedCommands().getFirst().getNewShapes().getFirst());
    }

    @Test
    void requiresSelectionDependsOnMode() {
        assertFalse(strategy.requiresSelection());

        strategy.setMode(AnnotationTool.AnnotationMode.ANGLE);
        assertTrue(strategy.requiresSelection());
    }

    private void injectSelection(Shape... shapes) throws Exception {
        context.setSelectedShapes(List.of(shapes));
        @SuppressWarnings("unchecked")
        Set<String> selectedIds = (Set<String>) getField(strategy, "selectedShapeIds");
        selectedIds.clear();
        for (Shape shape : shapes) {
            selectedIds.add(shape.getId());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
