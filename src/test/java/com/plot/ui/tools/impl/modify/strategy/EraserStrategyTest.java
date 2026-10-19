package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.DeleteShapesCommand;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.tools.impl.modify.strategy.EraserStrategy.EraserMode;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy.ModifyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EraserStrategyTest {

    private static final int MOUSE_LEFT = 0;
    private static final int MOUSE_RIGHT = 1;
    private static final int ESC_KEY = 27;

    private AppState appState;
    private EraserStrategy strategy;
    private ModifyStrategyTestContext context;
    private LineShape line;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        strategy = new EraserStrategy();
        context = new ModifyStrategyTestContext(appState);
        line = new LineShape(new Vec2d(0, 0), new Vec2d(20, 0));
        appState.addShape(line);
        context.setAreaPredicate((shape, bounds) -> true);
    }

    @Test
    void clickDeleteCreatesDeleteCommandForShapeOnLine() {
        ModifyResult result = strategy.onMouseDown(new Vec2d(10, 0), MOUSE_LEFT, context);

        assertEquals(ModifyResult.COMPLETE, result);
        ModifyCommand command = strategy.getModifyCommand();
        assertInstanceOf(DeleteShapesCommand.class, command);
        assertTrue(command.getOldShapes().contains(line));
    }

    @Test
    void clickDeleteOnEmptyAreaDoesNotCreateCommand() {
        ModifyResult result = strategy.onMouseDown(new Vec2d(100, 100), MOUSE_LEFT, context);

        assertEquals(ModifyResult.CONTINUE, result);
        assertNull(strategy.getModifyCommand());
        assertEquals("status.plot.eraser.not_found", context.getLastStatusMessage());
    }

    @Test
    void dragDeleteAccumulatesShapesUntilMouseUp() {
        strategy.setEraserMode(EraserMode.DRAG_DELETE);

        strategy.onMouseDown(new Vec2d(10, 0), MOUSE_LEFT, context);
        strategy.onMouseMove(new Vec2d(12, 0), context);
        ModifyResult result = strategy.onMouseUp(new Vec2d(12, 0), MOUSE_LEFT, context);

        assertEquals(ModifyResult.COMPLETE, result);
        assertInstanceOf(DeleteShapesCommand.class, strategy.getModifyCommand());
    }

    @Test
    void escapeCancelsPendingDelete() {
        strategy.onMouseDown(new Vec2d(10, 0), MOUSE_LEFT, context);

        ModifyResult result = strategy.onKeyDown(ESC_KEY, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertNull(strategy.getModifyCommand());
    }

    @Test
    void rightClickDuringDragCancelsDelete() {
        strategy.setEraserMode(EraserMode.DRAG_DELETE);
        strategy.onMouseDown(new Vec2d(10, 0), MOUSE_LEFT, context);

        ModifyResult result = strategy.onMouseDown(new Vec2d(10, 0), MOUSE_RIGHT, context);

        assertEquals(ModifyResult.CANCEL, result);
        assertNull(strategy.getModifyCommand());
    }
}
