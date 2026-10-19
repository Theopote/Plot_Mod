package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.helper.ArrayHandler;
import com.plot.ui.tools.impl.modify.strategy.ArrayWithSelectionStrategy.ArrayState;
import com.plot.ui.tools.impl.modify.strategy.ArrayWithSelectionStrategy.ArrayType;
import com.plot.ui.tools.impl.modify.strategy.ArrayWithSelectionStrategy.StrategyMode;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy.ModifyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArrayWithSelectionStrategyTest {

    private static final int MOUSE_RIGHT = 1;
    private static final int ESC_KEY = 27;

    private AppState appState;
    private ArrayWithSelectionStrategy strategy;
    private ModifyStrategyTestContext context;
    private LineShape sourceLine;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        strategy = new ArrayWithSelectionStrategy();
        context = new ModifyStrategyTestContext(appState);
        sourceLine = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        appState.addShape(sourceLine);
    }

    @Test
    void startsInSelectionMode() {
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
    }

    @Test
    void escapeResetsFromActiveArrayState() throws Exception {
        injectRectangularPreviewState(sourceLine);

        ModifyResult result = strategy.onKeyDown(ESC_KEY, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
        assertNull(strategy.getModifyCommand());
    }

    @Test
    void rightClickWithSelectionEntersRectangularPreview() throws Exception {
        injectSelection(sourceLine);

        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.CONTINUE, result);
        assertEquals(StrategyMode.ARRAY, strategy.getCurrentMode());
        assertEquals(ArrayState.PREVIEWING, strategy.getArrayState());
        assertEquals(ArrayType.RECTANGULAR, strategy.getArrayType());
        assertTrue(context.isPreviewEnabled());
    }

    @Test
    void rightClickWithoutSelectionNeedsSelection() {
        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.NEED_SELECTION, result);
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
    }

    @Test
    void rightClickDuringArrayReturnsToSelectionMode() throws Exception {
        injectRectangularPreviewState(sourceLine);

        ModifyResult result = strategy.onMouseDown(new Vec2d(0, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertEquals(StrategyMode.SELECTION, strategy.getCurrentMode());
        assertNull(strategy.getModifyCommand());
    }

    @Test
    void buildRectangularArrayCommandCreatesExpectedCopies() throws Exception {
        injectRectangularPreviewState(sourceLine);
        strategy.setRowCount(2);
        strategy.setColumnCount(2);
        strategy.setColumnSpacing(10);
        strategy.setRowSpacing(10);

        ModifyCommand command = strategy.buildArrayCommand();

        assertNotNull(command);
        assertEquals(3, command.getNewShapes().size());
    }

    @Test
    void buildArrayCommandReturnsNullWhenNotPreviewing() throws Exception {
        injectSelection(sourceLine);
        ModifyStrategyReflections.setField(strategy, "currentMode",
                ModifyStrategyReflections.enumValue(
                        "com.plot.ui.tools.impl.modify.strategy.ArrayWithSelectionStrategy$StrategyMode",
                        "ARRAY"));
        ModifyStrategyReflections.setField(strategy, "selectedShapes", List.of(sourceLine));
        ModifyStrategyReflections.setField(strategy, "arrayHandler", new ArrayHandler(appState));
        ModifyStrategyReflections.setField(strategy, "arrayState", ArrayState.IDLE);

        assertNull(strategy.buildArrayCommand());
    }

    @Test
    void buildPathArrayCommandRequiresPathPoints() throws Exception {
        injectRectangularPreviewState(sourceLine);
        strategy.setArrayType(ArrayType.PATH);

        assertNull(strategy.buildArrayCommand());
    }

    private void injectSelection(Shape shape) throws Exception {
        @SuppressWarnings("unchecked")
        Set<String> selectedIds = ModifyStrategyReflections.getField(strategy, "selectedShapeIds");
        selectedIds.clear();
        selectedIds.add(shape.getId());
    }

    private void injectRectangularPreviewState(Shape shape) throws Exception {
        ModifyStrategyReflections.setField(strategy, "currentMode",
                ModifyStrategyReflections.enumValue(
                        "com.plot.ui.tools.impl.modify.strategy.ArrayWithSelectionStrategy$StrategyMode",
                        "ARRAY"));
        ModifyStrategyReflections.setField(strategy, "arrayState", ArrayState.PREVIEWING);
        ModifyStrategyReflections.setField(strategy, "arrayType", ArrayType.RECTANGULAR);
        ModifyStrategyReflections.setField(strategy, "selectedShapes", List.of(shape));
        ModifyStrategyReflections.setField(strategy, "basePoint", new Vec2d(0, 0));
        ModifyStrategyReflections.setField(strategy, "arrayHandler", new ArrayHandler(appState));
        ModifyStrategyReflections.setField(strategy, "rowCount", 2);
        ModifyStrategyReflections.setField(strategy, "columnCount", 2);
        ModifyStrategyReflections.setField(strategy, "spacing", 10.0);
        ModifyStrategyReflections.setField(strategy, "rowSpacing", 10.0);

        injectSelection(shape);
    }
}
