package com.plot.ui.tools.impl.modify.strategy;

import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplodeStrategyTest {

    private AppState appState;
    private ExplodeStrategy strategy;
    private ModifyStrategyTestContext context;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        strategy = new ExplodeStrategy();
        context = new ModifyStrategyTestContext(appState);
    }

    @Test
    void prepareFailsWhenNothingSelected() {
        assertFalse(strategy.prepare(context));
        assertEquals("status.plot.explode.select_first", context.getLastStatusMessage());
        assertEquals(null, strategy.getModifyCommand());
    }

    @Test
    void prepareExplodesRectangleIntoSegments() {
        RectangleShape rectangle = new RectangleShape(new Vec2d(0, 0), 10, 5, 0);
        context.setSelectedShapes(List.of(rectangle));

        assertTrue(strategy.prepare(context));

        ModifyCommand command = strategy.getModifyCommand();
        assertNotNull(command);
        assertEquals(4, command.getNewShapes().size());
        assertTrue(command.getNewShapes().stream().allMatch(LineShape.class::isInstance));
    }

    @Test
    void prepareReportsNoEffectForSingleLine() {
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        context.setSelectedShapes(List.of(line));

        assertFalse(strategy.prepare(context));
        assertEquals("status.plot.explode.nothing", context.getLastStatusMessage());
    }
}
