package com.plot.ui.tools.impl.drawing.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.CommandHistory;
import com.plot.core.state.AppState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DrawingEditHistoryTest {

    private CommandHistory commandHistory;
    private PolylineDrawingSession.GeometrySink sink;

    @BeforeEach
    void setUp() {
        commandHistory = AppState.getInstance().getCommandHistory();
        commandHistory.clear();
    }

    @AfterEach
    void tearDown() {
        if (sink != null) {
            PolylineDrawingSession.unregister(sink);
            sink = null;
        }
        commandHistory.clear();
    }

    @Test
    void commitGeometryEditSkipsIdenticalSnapshots() {
        DrawingGeometrySnapshot snapshot = DrawingGeometrySnapshot.polyline(
                List.of(new Vec2d(0, 0), new Vec2d(10, 0)));

        DrawingEditHistory.commitGeometryEdit(snapshot, snapshot);

        assertEquals(0, commandHistory.size());
    }

    @Test
    void commitGeometryEditRecordsUndoableCommand() {
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

        DrawingEditHistory.commitGeometryEdit(before, after);

        assertEquals(1, commandHistory.size());
        commandHistory.undo();
        assertEquals(10.0, current.get().getPoints().get(1).x, 1e-6);

        commandHistory.redo();
        assertEquals(20.0, current.get().getPoints().get(1).x, 1e-6);
    }

    @Test
    void commitGeometryEditIgnoresNullSnapshots() {
        DrawingGeometrySnapshot snapshot = DrawingGeometrySnapshot.polyline(
                List.of(new Vec2d(0, 0), new Vec2d(10, 0)));

        DrawingEditHistory.commitGeometryEdit(null, snapshot);
        DrawingEditHistory.commitGeometryEdit(snapshot, null);

        assertEquals(0, commandHistory.size());
    }
}
