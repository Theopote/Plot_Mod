package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.strategy.AlignWithSelectionStrategy.AlignState;
import com.plot.ui.tools.impl.modify.strategy.AlignWithSelectionStrategy.StrategyMode;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy.ModifyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AlignWithSelectionStrategyTest {

    private static final int MOUSE_RIGHT = 1;
    private static final int ESC_KEY = 27;

    private AppState appState;
    private AlignWithSelectionStrategy strategy;
    private ModifyStrategyTestContext context;
    private LineShape sourceLine;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        strategy = new AlignWithSelectionStrategy();
        context = new ModifyStrategyTestContext(appState);
        sourceLine = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        appState.addShape(sourceLine);
    }

    @Test
    void startsInSelectionMode() {
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
    }

    @Test
    void escapeResetsFromActiveAlignState() throws Exception {
        injectActiveAlignState(sourceLine);

        ModifyResult result = strategy.onKeyDown(ESC_KEY, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
        assertNull(strategy.getModifyCommand());
    }

    @Test
    void rightClickWithSelectionSwitchesToAlignMode() throws Exception {
        injectSelection(sourceLine);

        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.CONTINUE, result);
        assertEquals(StrategyMode.ALIGN, strategy.getCurrentMode());
        assertEquals(AlignState.AWAIT_SOURCE1, ModifyStrategyReflections.getField(strategy, "alignState"));
    }

    @Test
    void rightClickWithoutSelectionNeedsSelection() {
        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.NEED_SELECTION, result);
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
    }

    @Test
    void rightClickDuringAlignReturnsToSelectionMode() throws Exception {
        injectActiveAlignState(sourceLine);

        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
        assertNull(strategy.getModifyCommand());
    }

    @Test
    void fourPointAlignTranslatesSelection() throws Exception {
        injectFourPointAlignState(
                sourceLine,
                new Vec2d(0, 0),
                new Vec2d(5, 0),
                new Vec2d(10, 0),
                new Vec2d(15, 0),
                false);

        ModifyResult result = invokePerformPointPairAlign();

        assertEquals(ModifyResult.COMPLETE, result);
        ModifyCommand command = strategy.getModifyCommand();
        assertNotNull(command);

        LineShape aligned = (LineShape) command.getNewShapes().getFirst();
        assertEquals(5.0, aligned.getStart().x, 1e-6);
        assertEquals(0.0, aligned.getStart().y, 1e-6);
        assertEquals(15.0, aligned.getEnd().x, 1e-6);
        assertEquals(0.0, aligned.getEnd().y, 1e-6);
    }

    @Test
    void fourPointAlignWithScaleEnabledDoublesLength() throws Exception {
        injectFourPointAlignState(
                sourceLine,
                new Vec2d(0, 0),
                new Vec2d(0, 0),
                new Vec2d(10, 0),
                new Vec2d(20, 0),
                true);

        ModifyResult result = invokePerformPointPairAlign();

        assertEquals(ModifyResult.COMPLETE, result);
        LineShape aligned = (LineShape) strategy.getModifyCommand().getNewShapes().getFirst();
        assertEquals(0.0, aligned.getStart().x, 1e-6);
        assertEquals(0.0, aligned.getStart().y, 1e-6);
        assertEquals(20.0, aligned.getEnd().x, 1e-6);
        assertEquals(0.0, aligned.getEnd().y, 1e-6);
    }

    private ModifyResult invokePerformPointPairAlign() throws Exception {
        return (ModifyResult) ModifyStrategyReflections.invoke(
                strategy,
                "performPointPairAlign",
                new Class<?>[] {List.class, IModifyStrategy.ModifyToolContext.class},
                ModifyStrategyReflections.<List<Shape>>getField(strategy, "selectedShapes"),
                context);
    }

    private void injectSelection(Shape shape) throws Exception {
        @SuppressWarnings("unchecked")
        Set<String> selectedIds = ModifyStrategyReflections.getField(strategy, "selectedShapeIds");
        selectedIds.clear();
        selectedIds.add(shape.getId());
    }

    private void injectActiveAlignState(Shape shape) throws Exception {
        ModifyStrategyReflections.setField(strategy, "currentMode",
                ModifyStrategyReflections.enumValue(
                        "com.plot.ui.tools.impl.modify.strategy.AlignWithSelectionStrategy$StrategyMode",
                        "ALIGN"));
        ModifyStrategyReflections.setField(strategy, "alignState", AlignState.AWAIT_SOURCE1);
        ModifyStrategyReflections.setField(strategy, "selectedShapes", List.of(shape));
        injectSelection(shape);
    }

    private void injectFourPointAlignState(Shape shape,
                                           Vec2d source1,
                                           Vec2d target1,
                                           Vec2d source2,
                                           Vec2d target2,
                                           boolean scaleEnabled) throws Exception {
        ModifyStrategyReflections.setField(strategy, "currentMode",
                ModifyStrategyReflections.enumValue(
                        "com.plot.ui.tools.impl.modify.strategy.AlignWithSelectionStrategy$StrategyMode",
                        "ALIGN"));
        ModifyStrategyReflections.setField(strategy, "alignState", AlignState.AWAIT_TARGET2);
        ModifyStrategyReflections.setField(strategy, "selectedShapes", List.of(shape));
        ModifyStrategyReflections.setField(strategy, "sourcePoint1", source1);
        ModifyStrategyReflections.setField(strategy, "targetPoint1", target1);
        ModifyStrategyReflections.setField(strategy, "sourcePoint2", source2);
        ModifyStrategyReflections.setField(strategy, "targetPoint2", target2);
        ModifyStrategyReflections.setField(strategy, "scaleEnabled", scaleEnabled);
        injectSelection(shape);
    }
}
