package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.CopyMirrorCommand;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.constants.ModifyConstraints;
import com.plot.ui.tools.impl.modify.dto.ModifyParameters;
import com.plot.ui.tools.impl.modify.helper.IModifyHandler;
import com.plot.ui.tools.impl.modify.helper.MirrorHandler;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy.ModifyResult;
import com.plot.ui.tools.impl.modify.strategy.MirrorWithSelectionStrategy.MirrorState;
import com.plot.ui.tools.impl.modify.strategy.MirrorWithSelectionStrategy.StrategyMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
class MirrorWithSelectionStrategyTest {

    private static final int MOUSE_RIGHT = 1;
    private static final int ESC_KEY = 27;
    private AppState appState;
    private MirrorWithSelectionStrategy strategy;
    private ModifyStrategyTestContext context;
    private LineShape sourceLine;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        strategy = new MirrorWithSelectionStrategy();
        context = new ModifyStrategyTestContext(appState);
        sourceLine = new LineShape(new Vec2d(5, 0), new Vec2d(10, 2));
        appState.addShape(sourceLine);
    }

    @Test
    void startsInSelectionMode() {
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
    }

    @Test
    void escapeResetsFromActiveMirrorState() throws Exception {
        injectActiveMirrorState(sourceLine, new Vec2d(0, 0));

        ModifyResult result = strategy.onKeyDown(ESC_KEY, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
        assertNull(strategy.getModifyCommand());
    }

    @Test
    void rightClickWithSelectionSwitchesToMirrorMode() throws Exception {
        injectSelection(sourceLine);

        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.CONTINUE, result);
        assertEquals(StrategyMode.MIRROR, strategy.getCurrentMode());
    }

    @Test
    void rightClickWithoutSelectionNeedsSelection() {
        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.NEED_SELECTION, result);
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
    }

    @Test
    void rightClickDuringMirrorReturnsToSelectionMode() throws Exception {
        injectActiveMirrorState(sourceLine, new Vec2d(0, 0));

        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
        assertNull(strategy.getModifyCommand());
    }

    @Test
    void completeMirrorReflectsAcrossVerticalAxis() throws Exception {
        injectActiveMirrorState(sourceLine, new Vec2d(0, 0));

        ModifyResult result = invokeCompleteMirror(new Vec2d(0, 10));

        assertEquals(ModifyResult.COMPLETE, result);
        ModifyCommand command = strategy.getModifyCommand();
        assertNotNull(command);

        LineShape mirrored = (LineShape) command.getNewShapes().getFirst();
        assertEquals(-5.0, mirrored.getStart().x, 1e-6);
        assertEquals(0.0, mirrored.getStart().y, 1e-6);
        assertEquals(-10.0, mirrored.getEnd().x, 1e-6);
        assertEquals(2.0, mirrored.getEnd().y, 1e-6);
    }

    @Test
    void shiftKeyConstrainsMirrorAxisToOrthogonal() throws Exception {
        injectActiveMirrorState(sourceLine, new Vec2d(0, 0));
        ModifyStrategyReflections.setField(strategy, "isShiftPressed", true);

        ModifyParameters params = new ModifyParameters();
        params.setVec2d(ModifyParameters.MIRROR_AXIS_START, new Vec2d(0, 0));
        params.setVec2d(ModifyParameters.MIRROR_AXIS_END, new Vec2d(10, 3));

        ModifyConstraints constraints = new ModifyConstraints();
        constraints.setOrthogonalConstraintEnabled(true);
        MirrorHandler mirrorHandler = ModifyStrategyReflections.getField(strategy, "mirrorHandler");
        IModifyHandler.ModifyParameters constrained = mirrorHandler.applyConstraints(params, constraints);

        Vec2d previewEnd = ((ModifyParameters) constrained).getVec2d(ModifyParameters.MIRROR_AXIS_END);
        assertNotNull(previewEnd);
        assertEquals(10.0, previewEnd.x, 1e-6);
        assertEquals(0.0, previewEnd.y, 1e-6);
    }

    @Test
    void ctrlCopyMirrorCreatesCopyMirrorCommand() throws Exception {
        injectActiveMirrorState(sourceLine, new Vec2d(0, 0));
        ModifyStrategyReflections.setField(strategy, "isCtrlPressed", true);

        ModifyResult result = invokeCompleteMirror(new Vec2d(0, 10));

        assertEquals(ModifyResult.COMPLETE, result);
        assertInstanceOf(CopyMirrorCommand.class, strategy.getModifyCommand());
        assertSame(sourceLine, appState.getShapes().stream()
                .filter(shape -> shape.getId().equals(sourceLine.getId()))
                .findFirst()
                .orElse(null));
    }

    private ModifyResult invokeCompleteMirror(Vec2d target) throws Exception {
        return (ModifyResult) ModifyStrategyReflections.invoke(
                strategy,
                "completeMirror",
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

    private void injectActiveMirrorState(Shape shape, Vec2d axisStart) throws Exception {
        ModifyStrategyReflections.setField(strategy, "currentMode",
                ModifyStrategyReflections.enumValue(
                        "com.plot.ui.tools.impl.modify.strategy.MirrorWithSelectionStrategy$StrategyMode",
                        "MIRROR"));
        ModifyStrategyReflections.setField(strategy, "currentState", MirrorState.SETTING_AXIS_END);
        ModifyStrategyReflections.setField(strategy, "selectedShapes", List.of(shape));
        ModifyStrategyReflections.setField(strategy, "axisStartPoint", axisStart);
        ModifyStrategyReflections.setField(strategy, "mirrorHandler", new MirrorHandler(appState));
        ModifyStrategyReflections.setField(strategy, "mirrorParameters",
                new com.plot.ui.tools.impl.modify.dto.ModifyParameters());
        ModifyStrategyReflections.setField(strategy, "mirrorConstraints",
                new com.plot.ui.tools.impl.modify.constants.ModifyConstraints());

        injectSelection(shape);
    }
}
