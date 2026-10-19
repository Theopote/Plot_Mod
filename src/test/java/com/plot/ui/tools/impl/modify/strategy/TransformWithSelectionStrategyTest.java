package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.command.commands.TransformCommand;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.ui.tools.impl.modify.dto.TransformParams;
import com.plot.ui.tools.impl.modify.enums.TransformState;
import com.plot.ui.tools.impl.modify.helper.BoundingBoxControlManager;
import com.plot.ui.tools.impl.modify.helper.BoundingBoxControlManager.ControlPointType;
import com.plot.ui.tools.impl.modify.helper.DragSession;
import com.plot.ui.tools.impl.modify.helper.TransformHandler;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy.ModifyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformWithSelectionStrategyTest {

    private static final int MOUSE_LEFT = 0;
    private static final int MOUSE_RIGHT = 1;
    private static final int ESC_KEY = 27;

    private AppState appState;
    private TransformHandler transformHandler;
    private BoundingBoxControlManager controlManager;
    private TransformWithSelectionStrategy strategy;
    private ModifyStrategyTestContext context;
    private LineShape sourceLine;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        transformHandler = new TransformHandler(appState);
        controlManager = new BoundingBoxControlManager();
        strategy = new TransformWithSelectionStrategy(
                transformHandler,
                controlManager,
                EventBus.getInstance());
        context = new ModifyStrategyTestContext(appState);
        // 非零高度包围盒，避免多个控制点叠在同一点
        sourceLine = new LineShape(new Vec2d(50, 50), new Vec2d(100, 80));
        appState.addShape(sourceLine);
    }

    @Test
    void startsInSelectionMode() throws Exception {
        assertEquals("SELECTING", currentModeName());
    }

    @Test
    void escapeInSelectingModeClearsSelection() throws Exception {
        injectSelection(sourceLine);
        context.setSelectedShapes(List.of(sourceLine));

        ModifyResult result = strategy.onKeyDown(ESC_KEY, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals("SELECTING", currentModeName());
        assertTrue(context.getSelectedShapes().isEmpty());
    }

    @Test
    void escapeInTransformingModeReturnsToSelecting() throws Exception {
        injectTransformingState(sourceLine);

        ModifyResult result = strategy.onKeyDown(ESC_KEY, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals("SELECTING", currentModeName());
        assertEquals(TransformState.IDLE, getTransformState());
    }

    @Test
    void rightClickWithSelectionEntersTransformMode() throws Exception {
        injectSelection(sourceLine);

        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.CONTINUE, result);
        assertEquals("TRANSFORMING", currentModeName());
        assertEquals(TransformState.SHOWING_BOUNDING_BOX, getTransformState());
        assertEquals(1, strategy.getSelectedShapes().size());
    }

    @Test
    void rightClickWithoutSelectionIsIgnored() throws Exception {
        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.IGNORED, result);
        assertEquals("SELECTING", currentModeName());
    }

    @Test
    void rightClickDuringTransformReturnsToSelecting() throws Exception {
        injectTransformingState(sourceLine);

        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals("SELECTING", currentModeName());
        assertNull(strategy.getModifyCommand());
    }

    @Test
    void moveDragPreviewTranslatesSelectedShapes() throws Exception {
        injectTransformingState(sourceLine);
        injectMoveDragSession(new Vec2d(75, 65), new Vec2d(80, 65));

        TransformParams params = invokeCreateTransformParameters(new Vec2d(80, 65));

        assertNull(params.getControlPointType());
        assertEquals(5.0, params.getDragVector().x, 1e-6);
        assertEquals(0.0, params.getDragVector().y, 1e-6);

        LineShape moved = (LineShape) transformHandler.calculateModifiedShapes(List.of(sourceLine), params).getFirst();
        assertEquals(55.0, moved.getStart().x, 1e-6);
        assertEquals(50.0, moved.getStart().y, 1e-6);
        assertEquals(105.0, moved.getEnd().x, 1e-6);
        assertEquals(80.0, moved.getEnd().y, 1e-6);
    }

    @Test
    void clickingSelectedShapeStartsMoveDrag() throws Exception {
        injectTransformingState(sourceLine);

        ModifyResult down = strategy.onMouseDown(new Vec2d(75, 65), MOUSE_LEFT, context);

        assertEquals(ModifyResult.CONTINUE, down);
        assertTrue(ModifyStrategyReflections.getBooleanField(strategy, "isDragging"));
    }

    @Test
    void controlPointDragScalesLineHorizontally() throws Exception {
        injectTransformingState(sourceLine);
        Vec2d rightHandle = controlManager.getControlPoints()[ControlPointType.CENTER_RIGHT.getIndex()];

        Vec2d dragTarget = rightHandle.add(new Vec2d(50, 0));

        assertEquals(ModifyResult.CONTINUE, strategy.onMouseDown(rightHandle, MOUSE_LEFT, context));
        strategy.onMouseMove(dragTarget, context);
        ModifyResult result = strategy.onMouseUp(dragTarget, MOUSE_LEFT, context);

        assertEquals(ModifyResult.COMPLETE, result);
        assertEquals(1, context.getExecutedCommands().size());

        TransformParams params = getTransformParams(context.getExecutedCommands().getFirst());
        assertEquals(ControlPointType.CENTER_RIGHT, params.getControlPointType());
        assertEquals(50.0, params.getDragVector().x, 1e-6);

        LineShape scaled = (LineShape) transformHandler.calculateModifiedShapes(List.of(sourceLine), params).getFirst();
        assertEquals(50.0, scaled.getStart().x, 1e-6);
        assertEquals(50.0, scaled.getStart().y, 1e-6);
        assertEquals(150.0, scaled.getEnd().x, 1e-6);
        assertEquals(80.0, scaled.getEnd().y, 1e-6);
    }

    @Test
    void updateSelectedShapeIdsAfterTransformReplacesOldIds() throws Exception {
        injectTransformingState(sourceLine);
        LineShape replacement = new LineShape(new Vec2d(1, 1), new Vec2d(11, 1));

        strategy.updateSelectedShapeIdsAfterTransform(List.of(sourceLine), List.of(replacement));

        @SuppressWarnings("unchecked")
        Set<String> selectedIds = ModifyStrategyReflections.getField(strategy, "selectedShapeIds");
        assertEquals(1, selectedIds.size());
        assertTrue(selectedIds.contains(replacement.getId()));
        assertFalse(selectedIds.contains(sourceLine.getId()));
    }

    private void injectSelection(Shape shape) throws Exception {
        @SuppressWarnings("unchecked")
        Set<String> selectedIds = ModifyStrategyReflections.getField(strategy, "selectedShapeIds");
        selectedIds.clear();
        selectedIds.add(shape.getId());
    }

    private void injectTransformingState(Shape shape) throws Exception {
        injectSelection(shape);
        ModifyStrategyReflections.setField(strategy, "selectedShapes", List.of(shape));
        ModifyStrategyReflections.setField(strategy, "currentMode",
                ModifyStrategyReflections.enumValue(
                        "com.plot.ui.tools.impl.modify.strategy.TransformWithSelectionStrategy$InteractionMode",
                        "TRANSFORMING"));
        ModifyStrategyReflections.setField(strategy, "transformState", TransformState.SHOWING_BOUNDING_BOX);
        controlManager.calculateBoundingBox(List.of(shape));
    }

    private void injectMoveDragSession(Vec2d start, Vec2d end) throws Exception {
        ModifyStrategyReflections.setField(strategy, "isDragging", true);
        ModifyStrategyReflections.setField(strategy, "transformState", TransformState.DRAGGING_CONTROL_POINT);
        ModifyStrategyReflections.setField(strategy, "primaryControlPoint", null);
        ModifyStrategyReflections.setField(strategy, "previewEnabled", true);

        DragSession session = new DragSession(start, null, 4.0);
        session.activate();
        session.updateCurrentPoint(end);
        ModifyStrategyReflections.setField(strategy, "currentDragSession", session);
    }

    private TransformParams invokeCreateTransformParameters(Vec2d currentPoint) throws Exception {
        return (TransformParams) ModifyStrategyReflections.invoke(
                strategy,
                "createTransformParameters",
                new Class<?>[] {Vec2d.class, IModifyStrategy.ModifyToolContext.class},
                currentPoint,
                context);
    }

    private TransformState getTransformState() throws Exception {
        return ModifyStrategyReflections.getField(strategy, "transformState");
    }

    private String currentModeName() throws Exception {
        Object mode = ModifyStrategyReflections.getField(strategy, "currentMode");
        return (String) mode.getClass().getMethod("name").invoke(mode);
    }

    private static TransformParams getTransformParams(ModifyCommand command) throws Exception {
        assertInstanceOf(TransformCommand.class, command);
        return ModifyStrategyReflections.getField(command, "transformParams");
    }
}
