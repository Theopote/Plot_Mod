package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.CopyRotateCommand;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.helper.RotateHandler;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy.ModifyResult;
import com.plot.ui.tools.impl.modify.strategy.RotateWithSelectionStrategy.RotateState;
import com.plot.ui.tools.impl.modify.strategy.RotateWithSelectionStrategy.StrategyMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotateWithSelectionStrategyTest {

    private static final int MOUSE_RIGHT = 1;
    private static final int ESC_KEY = 27;
    private static final int SHIFT_KEY = 16;

    private AppState appState;
    private RotateWithSelectionStrategy strategy;
    private ModifyStrategyTestContext context;
    private LineShape sourceLine;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        strategy = new RotateWithSelectionStrategy();
        context = new ModifyStrategyTestContext(appState);
        sourceLine = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        appState.addShape(sourceLine);
    }

    @Test
    void startsInSelectionMode() {
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
    }

    @Test
    void escapeResetsFromActiveRotateState() throws Exception {
        injectActiveRotateState(sourceLine, new Vec2d(0, 0), new Vec2d(10, 0));

        ModifyResult result = strategy.onKeyDown(ESC_KEY, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
        assertNull(strategy.getModifyCommand());
    }

    @Test
    void rightClickWithSelectionSwitchesToRotateMode() throws Exception {
        injectSelection(sourceLine);

        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.CONTINUE, result);
        assertEquals(StrategyMode.ROTATE, strategy.getCurrentMode());
    }

    @Test
    void rightClickWithoutSelectionNeedsSelection() {
        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.NEED_SELECTION, result);
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
    }

    @Test
    void rightClickDuringRotateReturnsToSelectionMode() throws Exception {
        injectActiveRotateState(sourceLine, new Vec2d(0, 0), new Vec2d(10, 0));

        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
        assertNull(strategy.getModifyCommand());
    }

    @Test
    void completeRotationCreatesModifyCommandWith90Degrees() throws Exception {
        injectActiveRotateState(sourceLine, new Vec2d(0, 0), new Vec2d(10, 0));

        ModifyResult result = invokeCompleteRotation(new Vec2d(0, 10));

        assertEquals(ModifyResult.COMPLETE, result);
        ModifyCommand command = strategy.getModifyCommand();
        assertNotNull(command);
        assertInstanceOf(ModifyCommand.class, command);

        LineShape rotated = (LineShape) command.getNewShapes().getFirst();
        assertEquals(0.0, rotated.getStart().x, 1e-6);
        assertEquals(0.0, rotated.getStart().y, 1e-6);
        assertEquals(0.0, rotated.getEnd().x, 1e-6);
        assertEquals(10.0, rotated.getEnd().y, 1e-6);
    }

    @Test
    void shiftKeySnapsPreviewAngleTo15DegreeSteps() throws Exception {
        injectActiveRotateState(sourceLine, new Vec2d(0, 0), new Vec2d(10, 0));
        double radius = 10.0;
        double rawAngle = Math.toRadians(17.0);
        Vec2d mousePoint = new Vec2d(radius * Math.cos(rawAngle), radius * Math.sin(rawAngle));

        strategy.onKeyDown(SHIFT_KEY, context);
        strategy.onMouseMove(mousePoint, context);

        Vec2d constrainedEnd = ModifyStrategyReflections.getField(strategy, "constrainedCurrentPoint");
        assertNotNull(constrainedEnd);
        double snappedAngle = Math.toDegrees(Math.atan2(constrainedEnd.y, constrainedEnd.x));
        assertEquals(15.0, snappedAngle, 1e-6);
        assertTrue(context.isPreviewEnabled());
    }

    @Test
    void ctrlCopyRotateCreatesCopyRotateCommand() throws Exception {
        injectActiveRotateState(sourceLine, new Vec2d(0, 0), new Vec2d(10, 0));
        ModifyStrategyReflections.setField(strategy, "isCtrlPressed", true);

        ModifyResult result = invokeCompleteRotation(new Vec2d(0, 10));

        assertEquals(ModifyResult.COMPLETE, result);
        assertInstanceOf(CopyRotateCommand.class, strategy.getModifyCommand());
        assertSame(sourceLine, appState.getShapes().stream()
                .filter(shape -> shape.getId().equals(sourceLine.getId()))
                .findFirst()
                .orElse(null));
    }

    private ModifyResult invokeCompleteRotation(Vec2d target) throws Exception {
        return (ModifyResult) ModifyStrategyReflections.invoke(
                strategy,
                "completeRotation",
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

    private void injectActiveRotateState(Shape shape, Vec2d center, Vec2d reference) throws Exception {
        RotateHandler rotateHandler = new RotateHandler(appState);
        ModifyStrategyReflections.setField(strategy, "currentMode",
                ModifyStrategyReflections.enumValue(
                        "com.plot.ui.tools.impl.modify.strategy.RotateWithSelectionStrategy$StrategyMode",
                        "ROTATE"));
        ModifyStrategyReflections.setField(strategy, "currentState", RotateState.ROTATING);
        ModifyStrategyReflections.setField(strategy, "selectedShapes", List.of(shape));
        ModifyStrategyReflections.setField(strategy, "centerPoint", center);
        ModifyStrategyReflections.setField(strategy, "referencePoint", reference);
        ModifyStrategyReflections.setField(strategy, "baseAngle", rotateHandler.calculateAngle(center, reference));
        ModifyStrategyReflections.setField(strategy, "rotateHandler", rotateHandler);
        ModifyStrategyReflections.setField(strategy, "rotateParameters",
                new com.plot.ui.tools.impl.modify.dto.ModifyParameters());
        ModifyStrategyReflections.setField(strategy, "rotateConstraints",
                new com.plot.ui.tools.impl.modify.constants.ModifyConstraints());

        injectSelection(shape);
    }
}
