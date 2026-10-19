package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy.ModifyResult;
import com.plot.ui.tools.impl.modify.strategy.SelectionStrategy.SelectionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionStrategyTest {

    private static final int MOUSE_LEFT = 0;

    private AppState appState;
    private SelectionStrategy strategy;
    private ModifyStrategyTestContext context;
    private LineShape line;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        strategy = new SelectionStrategy();
        context = new ModifyStrategyTestContext(appState);
        line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        appState.addShape(line);
        context.setShapeFinder((pos, tolerance) ->
                pos.distance(new Vec2d(5, 0)) <= tolerance ? line : null);
        context.setAreaPredicate((shape, bounds) -> shape == line);
    }

    @Test
    void setSelectionModeResetsActiveSelectionState() throws Exception {
        setBooleanField(strategy, "isSelecting", true);
        strategy.setSelectionMode(SelectionMode.LASSO);

        assertEquals(SelectionMode.LASSO, strategy.getCurrentMode());
        assertFalse(strategy.isSelecting());
    }

    @Test
    void pointSelectSelectsShapeOnMouseUp() {
        strategy.onMouseDown(new Vec2d(5, 0), MOUSE_LEFT, context);
        ModifyResult result = strategy.onMouseUp(new Vec2d(5, 0), MOUSE_LEFT, context);

        assertEquals(ModifyResult.COMPLETE, result);
        assertEquals(1, strategy.getSelectedCount());
        assertTrue(line.isSelected());
        assertFalse(line.isHighlighted());
        assertTrue(context.getSelectedShapes().contains(line));
    }

    @Test
    void boxSelectClearsTemporaryHighlightAfterMouseUp() {
        strategy.onMouseDown(new Vec2d(0, -1), MOUSE_LEFT, context);
        strategy.onMouseMove(new Vec2d(12, 1), context);
        assertTrue(line.isHighlighted());

        strategy.onMouseUp(new Vec2d(12, 1), MOUSE_LEFT, context);

        assertFalse(line.isHighlighted());
        assertTrue(line.isSelected());
        assertEquals(1, strategy.getSelectedCount());
    }

    @Test
    void blankClickClearsExistingSelection() throws Exception {
        injectSelectedShape(line);

        strategy.onMouseDown(new Vec2d(50, 50), MOUSE_LEFT, context);
        strategy.onMouseUp(new Vec2d(50, 50), MOUSE_LEFT, context);

        assertEquals(0, strategy.getSelectedCount());
        assertFalse(line.isSelected());
        assertTrue(context.getSelectedShapes().isEmpty());
    }

    @Test
    void clearTemporarySelectionRemovesPreviewHighlight() throws Exception {
        line.setHighlighted(true);
        addTempSelectionId(line.getId());

        invokeClearTemporarySelection(context);

        assertFalse(line.isHighlighted());
    }

    private void injectSelectedShape(Shape shape) throws Exception {
        @SuppressWarnings("unchecked")
        Set<String> selectedIds = (Set<String>) getField(strategy, "selectedShapeIds");
        selectedIds.clear();
        selectedIds.add(shape.getId());
        shape.setSelected(true);
        context.setSelectedShapes(java.util.List.of(shape));
    }

    private void addTempSelectionId(String shapeId) throws Exception {
        @SuppressWarnings("unchecked")
        Set<String> tempIds = (Set<String>) getField(strategy, "tempSelectedShapeIds");
        tempIds.clear();
        tempIds.add(shapeId);
    }

    private void invokeClearTemporarySelection(ModifyStrategyTestContext context) throws Exception {
        Method method = SelectionStrategy.class.getDeclaredMethod(
                "clearTemporarySelection", IModifyStrategy.ModifyToolContext.class);
        method.setAccessible(true);
        method.invoke(strategy, context);
    }

    private static void setBooleanField(Object target, String name, boolean value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.setBoolean(target, value);
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
