package com.plot.core.command.commands;

import com.plot.api.geometry.Vec2d;
import com.plot.ui.tools.impl.drawing.helper.DrawingGeometrySnapshot;
import com.plot.ui.tools.impl.drawing.helper.PolylineDrawingSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawingGeometryEditCommandTest {

    private PolylineDrawingSession.GeometrySink sink;

    @AfterEach
    void tearDown() {
        if (sink != null) {
            PolylineDrawingSession.unregister(sink);
            sink = null;
        }
    }

    @Test
    void undoAndRedoRestorePolylinePoints() {
        AtomicReference<DrawingGeometrySnapshot> current = new AtomicReference<>(
                DrawingGeometrySnapshot.polyline(List.of(new Vec2d(0, 0), new Vec2d(10, 0))));

        sink = snapshot -> {
            current.set(snapshot);
            return true;
        };
        PolylineDrawingSession.register(sink);

        DrawingGeometrySnapshot before = current.get();
        DrawingGeometrySnapshot after = DrawingGeometrySnapshot.polyline(
                List.of(new Vec2d(0, 0), new Vec2d(20, 5)));
        current.set(after);

        DrawingGeometryEditCommand command = new DrawingGeometryEditCommand(before, after);
        command.undo();

        assertEquals(2, current.get().getPoints().size());
        assertEquals(10.0, current.get().getPoints().get(1).x, 1e-6);

        command.redo();
        assertEquals(20.0, current.get().getPoints().get(1).x, 1e-6);
        assertTrue(current.get().getPoints().get(1).y > 0.0);
    }
}
