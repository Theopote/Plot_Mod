package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.CopyScaleCommand;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.helper.ScaleHandler;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy.ModifyResult;
import com.plot.ui.tools.impl.modify.strategy.ScaleWithSelectionStrategy.ScaleState;
import com.plot.ui.tools.impl.modify.strategy.ScaleWithSelectionStrategy.StrategyMode;
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

class ScaleWithSelectionStrategyTest {

    private static final int MOUSE_RIGHT = 1;
    private static final int ESC_KEY = 27;
    private static final int SHIFT_KEY = 16;

    private AppState appState;
    private ScaleWithSelectionStrategy strategy;
    private ModifyStrategyTestContext context;
    private LineShape sourceLine;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        strategy = new ScaleWithSelectionStrategy();
        context = new ModifyStrategyTestContext(appState);
        sourceLine = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        appState.addShape(sourceLine);
    }

    @Test
    void startsInSelectionMode() {
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
    }

    @Test
    void escapeResetsFromActiveScaleState() throws Exception {
        injectActiveScaleState(sourceLine, new Vec2d(0, 0), new Vec2d(10, 0));

        ModifyResult result = strategy.onKeyDown(ESC_KEY, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
        assertNull(strategy.getModifyCommand());
    }

    @Test
    void rightClickWithSelectionSwitchesToScaleMode() throws Exception {
        injectSelection(sourceLine);

        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.CONTINUE, result);
        assertEquals(StrategyMode.SCALE, strategy.getCurrentMode());
    }

    @Test
    void rightClickWithoutSelectionNeedsSelection() {
        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.NEED_SELECTION, result);
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
    }

    @Test
    void rightClickDuringScaleReturnsToSelectionMode() throws Exception {
        injectActiveScaleState(sourceLine, new Vec2d(0, 0), new Vec2d(10, 0));

        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
        assertNull(strategy.getModifyCommand());
    }

    @Test
    void completeScaleDoublesLineLength() throws Exception {
        injectActiveScaleState(sourceLine, new Vec2d(0, 0), new Vec2d(10, 0));

        ModifyResult result = invokeCompleteScale(new Vec2d(20, 0));

        assertEquals(ModifyResult.COMPLETE, result);
        ModifyCommand command = strategy.getModifyCommand();
        assertNotNull(command);

        LineShape scaled = (LineShape) command.getNewShapes().getFirst();
        assertEquals(0.0, scaled.getStart().x, 1e-6);
        assertEquals(0.0, scaled.getStart().y, 1e-6);
        assertEquals(20.0, scaled.getEnd().x, 1e-6);
        assertEquals(0.0, scaled.getEnd().y, 1e-6);
    }

    @Test
    void shiftKeyKeepsUniformScalePreviewEnabled() throws Exception {
        injectActiveScaleState(sourceLine, new Vec2d(0, 0), new Vec2d(10, 0));
        ModifyStrategyReflections.setField(strategy, "currentPoint", new Vec2d(15, 4));

        strategy.onKeyDown(SHIFT_KEY, context);
        ModifyResult result = strategy.onMouseMove(new Vec2d(15, 4), context);

        assertEquals(ModifyResult.CONTINUE, result);
        assertTrue(context.isPreviewEnabled());
        assertNotNull(strategy.getPreviewShapes());
    }

    @Test
    void ctrlCopyScaleCreatesCopyScaleCommand() throws Exception {
        injectActiveScaleState(sourceLine, new Vec2d(0, 0), new Vec2d(10, 0));
        ModifyStrategyReflections.setField(strategy, "isCtrlPressed", true);

        ModifyResult result = invokeCompleteScale(new Vec2d(20, 0));

        assertEquals(ModifyResult.COMPLETE, result);
        assertInstanceOf(CopyScaleCommand.class, strategy.getModifyCommand());
        assertSame(sourceLine, appState.getShapes().stream()
                .filter(shape -> shape.getId().equals(sourceLine.getId()))
                .findFirst()
                .orElse(null));
    }

    private ModifyResult invokeCompleteScale(Vec2d target) throws Exception {
        return (ModifyResult) ModifyStrategyReflections.invoke(
                strategy,
                "completeScale",
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

    private void injectActiveScaleState(Shape shape, Vec2d center, Vec2d reference) throws Exception {
        ModifyStrategyReflections.setField(strategy, "currentMode",
                ModifyStrategyReflections.enumValue(
                        "com.plot.ui.tools.impl.modify.strategy.ScaleWithSelectionStrategy$StrategyMode",
                        "SCALE"));
        ModifyStrategyReflections.setField(strategy, "currentState", ScaleState.SCALING);
        ModifyStrategyReflections.setField(strategy, "selectedShapes", List.of(shape));
        ModifyStrategyReflections.setField(strategy, "centerPoint", center);
        ModifyStrategyReflections.setField(strategy, "referencePoint", reference);
        ModifyStrategyReflections.setField(strategy, "baseDistance", center.distance(reference));
        ModifyStrategyReflections.setField(strategy, "scaleHandler", new ScaleHandler(appState));
        ModifyStrategyReflections.setField(strategy, "scaleParameters",
                new com.plot.ui.tools.impl.modify.dto.ModifyParameters());
        ModifyStrategyReflections.setField(strategy, "scaleConstraints",
                new com.plot.ui.tools.impl.modify.constants.ModifyConstraints());

        injectSelection(shape);
    }
}
