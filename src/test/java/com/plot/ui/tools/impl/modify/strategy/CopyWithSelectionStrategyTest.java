package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.CopyMoveCommand;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.helper.MoveHandler;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy.ModifyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopyWithSelectionStrategyTest {

    private static final int ESC_KEY = 27;

    private AppState appState;
    private CopyWithSelectionStrategy strategy;
    private ModifyStrategyTestContext context;
    private LineShape sourceLine;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        strategy = new CopyWithSelectionStrategy();
        context = new ModifyStrategyTestContext(appState);
        sourceLine = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        appState.addShape(sourceLine);
    }

    @Test
    void continuousCopyKeepsReferencePointAfterFirstCopy() throws Exception {
        injectActiveCopyState(sourceLine, new Vec2d(0, 0));

        assertEquals(ModifyResult.COMPLETE, invokeCompleteCopy(new Vec2d(10, 0)));
        assertInstanceOf(CopyMoveCommand.class, strategy.getModifyCommand());

        strategy.prepareForNextCopy(context);

        assertTrue(getBooleanField(strategy, "hasBasePoint"));
        assertEquals(new Vec2d(0, 0), getField(strategy, "copyStartPoint"));
        assertNull(strategy.getModifyCommand());
        assertEquals(ModifyResult.COMPLETE, invokeCompleteCopy(new Vec2d(20, 0)));
        assertInstanceOf(CopyMoveCommand.class, strategy.getModifyCommand());
    }

    @Test
    void escapeEndsCopySession() throws Exception {
        injectActiveCopyState(sourceLine, new Vec2d(0, 0));

        ModifyResult result = strategy.onKeyDown(ESC_KEY, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals(0, strategy.getSelectedCount());
        assertNull(strategy.getModifyCommand());
    }

    @Test
    void copyCommandPreservesOriginalShape() throws Exception {
        injectActiveCopyState(sourceLine, new Vec2d(0, 0));
        invokeCompleteCopy(new Vec2d(10, 0));

        ModifyCommand command = strategy.getModifyCommand();
        assertNotNull(command);
        assertSame(sourceLine, appState.getShapes().stream()
                .filter(shape -> shape.getId().equals(sourceLine.getId()))
                .findFirst()
                .orElse(null));
        assertInstanceOf(CopyMoveCommand.class, command);
    }

    @Test
    void boxSelectClearsTemporaryHighlightAfterMouseUp() {
        HighlightProbeStrategy probe = new HighlightProbeStrategy();
        sourceLine.setHighlighted(true);
        probe.markTemporaryHighlight(sourceLine, context);

        assertFalse(sourceLine.isHighlighted());
    }

    private ModifyResult invokeCompleteCopy(Vec2d target) throws Exception {
        Method method = CopyWithSelectionStrategy.class.getDeclaredMethod(
                "completeCopy", Vec2d.class, IModifyStrategy.ModifyToolContext.class);
        method.setAccessible(true);
        return (ModifyResult) method.invoke(strategy, target, context);
    }

    private void injectActiveCopyState(Shape shape, Vec2d referencePoint) throws Exception {
        setField(strategy, "currentMode", enumValue(
                "com.plot.ui.tools.impl.modify.strategy.CopyWithSelectionStrategy$StrategyMode", "COPY"));
        setField(strategy, "selectedShapes", List.of(shape));
        setField(strategy, "hasBasePoint", true);
        setField(strategy, "copyStartPoint", referencePoint);
        setField(strategy, "copyCurrentPoint", referencePoint);
        setField(strategy, "moveHandler", new MoveHandler(appState));

        @SuppressWarnings("unchecked")
        Set<String> selectedIds = (Set<String>) getField(strategy, "selectedShapeIds");
        selectedIds.clear();
        selectedIds.add(shape.getId());
    }

    private static boolean getBooleanField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, value);
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(String className, String constant) throws Exception {
        Class<?> enumClass = Class.forName(className);
        Class<? extends Enum> rawEnum = (Class<? extends Enum>) enumClass;
        return Enum.valueOf(rawEnum, constant);
    }

    private static final class HighlightProbeStrategy extends CopyWithSelectionStrategy {
        @SuppressWarnings("unchecked")
        void markTemporaryHighlight(Shape shape, ModifyToolContext context) {
            try {
                Field tempIdsField = BaseSelectionStrategy.class.getDeclaredField("tempSelectedShapeIds");
                tempIdsField.setAccessible(true);
                Set<String> tempIds = (Set<String>) tempIdsField.get(this);
                tempIds.clear();
                tempIds.add(shape.getId());
                clearTemporarySelection(context);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }
}
