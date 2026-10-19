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
import com.plot.ui.tools.impl.modify.strategy.MoveWithSelectionStrategy.StrategyMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveWithSelectionStrategyTest {

    private static final int MOUSE_LEFT = 0;
    private static final int MOUSE_RIGHT = 1;
    private static final int ESC_KEY = 27;
    private static final int SHIFT_KEY = 16;

    private AppState appState;
    private MoveWithSelectionStrategy strategy;
    private ModifyStrategyTestContext context;
    private LineShape sourceLine;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        strategy = new MoveWithSelectionStrategy();
        context = new ModifyStrategyTestContext(appState);
        sourceLine = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        appState.addShape(sourceLine);
    }

    @Test
    void startsInSelectionMode() {
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
    }

    @Test
    void escapeResetsToSelectionMode() throws Exception {
        injectActiveMoveState(sourceLine, new Vec2d(0, 0));

        ModifyResult result = strategy.onKeyDown(ESC_KEY, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
        assertNull(strategy.getModifyCommand());
    }

    @Test
    void rightClickWithSelectionSwitchesToMoveMode() throws Exception {
        injectSelection(sourceLine);

        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.CONTINUE, result);
        assertEquals(StrategyMode.MOVE, strategy.getCurrentMode());
    }

    @Test
    void rightClickWithoutSelectionNeedsSelection() {
        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.NEED_SELECTION, result);
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
    }

    @Test
    void rightClickDuringMoveReturnsToSelectionMode() throws Exception {
        injectActiveMoveState(sourceLine, new Vec2d(0, 0));

        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
        assertNull(strategy.getModifyCommand());
    }

    @Test
    void completeMoveCreatesModifyCommandWithTranslation() throws Exception {
        injectActiveMoveState(sourceLine, new Vec2d(0, 0));

        ModifyResult result = invokeCompleteMove(new Vec2d(5, 0));

        assertEquals(ModifyResult.COMPLETE, result);
        ModifyCommand command = strategy.getModifyCommand();
        assertInstanceOf(ModifyCommand.class, command);
        assertFalse(command instanceof CopyMoveCommand);

        LineShape moved = (LineShape) command.getNewShapes().getFirst();
        assertEquals(5.0, moved.getStart().x, 1e-6);
        assertEquals(0.0, moved.getStart().y, 1e-6);
        assertEquals(15.0, moved.getEnd().x, 1e-6);
        assertEquals(0.0, moved.getEnd().y, 1e-6);
    }

    @Test
    void completeMoveIgnoresTinyDisplacement() throws Exception {
        injectActiveMoveState(sourceLine, new Vec2d(0, 0));

        ModifyResult result = invokeCompleteMove(new Vec2d(0.05, 0));

        assertEquals(ModifyResult.COMPLETE, result);
        assertNull(strategy.getModifyCommand());
    }

    @Test
    void shiftKeyConstrainsPreviewToDominantAxis() throws Exception {
        injectActiveMoveState(sourceLine, new Vec2d(0, 0));

        strategy.onKeyDown(SHIFT_KEY, context);
        strategy.onMouseMove(new Vec2d(10, 3), context);

        Vec2d constrainedEnd = ModifyStrategyReflections.getField(strategy, "constrainedEndPoint");
        assertEquals(10.0, constrainedEnd.x, 1e-6);
        assertEquals(0.0, constrainedEnd.y, 1e-6);
        assertTrue(context.isPreviewEnabled());
    }

    @Test
    void ctrlCopyMoveCreatesCopyMoveCommand() throws Exception {
        injectActiveMoveState(sourceLine, new Vec2d(0, 0));
        ModifyStrategyReflections.setField(strategy, "isCtrlPressed", true);

        ModifyResult result = invokeCompleteMove(new Vec2d(5, 0));

        assertEquals(ModifyResult.COMPLETE, result);
        assertInstanceOf(CopyMoveCommand.class, strategy.getModifyCommand());
        assertSame(sourceLine, appState.getShapes().stream()
                .filter(shape -> shape.getId().equals(sourceLine.getId()))
                .findFirst()
                .orElse(null));
    }

    private ModifyResult invokeCompleteMove(Vec2d target) throws Exception {
        return (ModifyResult) ModifyStrategyReflections.invoke(
                strategy,
                "completeMove",
                new Class<?>[] {Vec2d.class, IModifyStrategy.ModifyToolContext.class},
                target,
                context);
    }

    private void injectSelection(Shape shape) throws Exception {
        @SuppressWarnings("unchecked")
        Set<String> selectedIds = ModifyStrategyReflections.getField(strategy, "selectedShapeIds");
        selectedIds.clear();
        selectedIds.add(shape.getId());
    }

    private void injectActiveMoveState(Shape shape, Vec2d basePoint) throws Exception {
        ModifyStrategyReflections.setField(strategy, "currentMode",
                ModifyStrategyReflections.enumValue(
                        "com.plot.ui.tools.impl.modify.strategy.MoveWithSelectionStrategy$StrategyMode",
                        "MOVE"));
        ModifyStrategyReflections.setField(strategy, "selectedShapes", List.of(shape));
        ModifyStrategyReflections.setField(strategy, "hasBasePoint", true);
        ModifyStrategyReflections.setField(strategy, "moveStartPoint", basePoint);
        ModifyStrategyReflections.setField(strategy, "moveCurrentPoint", basePoint);
        ModifyStrategyReflections.setField(strategy, "moveHandler", new MoveHandler(appState));
        ModifyStrategyReflections.setField(strategy, "moveParameters",
                new com.plot.ui.tools.impl.modify.dto.ModifyParameters());

        injectSelection(shape);
    }
}
