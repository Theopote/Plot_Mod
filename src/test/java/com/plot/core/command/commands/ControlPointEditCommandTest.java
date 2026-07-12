package com.plot.core.command.commands;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.LineShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControlPointEditCommandTest {

    @Test
    void undoAndRedoRestoreLineEndpoints() {
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        LineShape before = line.clone();
        line.setEnd(new Vec2d(20, 5));
        LineShape after = line.clone();

        ControlPointEditCommand command = new ControlPointEditCommand(line, before, after);

        assertEquals(20.0, line.getEnd().x, 1e-6);
        assertEquals(5.0, line.getEnd().y, 1e-6);

        command.undo();

        assertEquals(10.0, line.getEnd().x, 1e-6);
        assertEquals(0.0, line.getEnd().y, 1e-6);

        command.redo();

        assertEquals(20.0, line.getEnd().x, 1e-6);
        assertEquals(5.0, line.getEnd().y, 1e-6);
    }
}
