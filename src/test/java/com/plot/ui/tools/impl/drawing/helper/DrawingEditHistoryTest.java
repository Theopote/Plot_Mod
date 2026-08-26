package com.plot.ui.tools.impl.drawing.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.CommandService;
import com.plot.core.context.ApplicationContext;
import com.plot.core.state.AppState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DrawingEditHistoryTest {

    private CommandService commandService;
    private PolylineDrawingSession.GeometrySink sink;

    @BeforeEach
    void setUp() {
        commandService = ApplicationContext.getInstance().getAppState().getCommandService();
        commandService.clear();
    }

    @AfterEach
    void tearDown() {
        if (sink != null) {
            PolylineDrawingSession.unregister(sink);
            sink = null;
        }
        commandService.clear();
    }

    @Test
    void commitGeometryEditSkipsIdenticalSnapshots() {
        DrawingGeometrySnapshot snapshot = DrawingGeometrySnapshot.polyline(
                List.of(new Vec2d(0, 0), new Vec2d(10, 0)));

        DrawingEditHistory.commitGeometryEdit(snapshot, snapshot);

        assertEquals(0, commandService.size());
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

        assertEquals(1, commandService.size());
        commandService.undo();
        assertEquals(10.0, current.get().getPoints().get(1).x, 1e-6);

        commandService.redo();
        assertEquals(20.0, current.get().getPoints().get(1).x, 1e-6);
    }

    @Test
    void commitGeometryEditSupportsPenSnapshots() {
        AtomicReference<DrawingGeometrySnapshot> current = new AtomicReference<>(
                DrawingGeometrySnapshot.pen(List.of(new PathNode(new Vec2d(0, 0)))));
        sink = snapshot -> {
            current.set(snapshot);
            return true;
        };
        PolylineDrawingSession.register(sink);

        DrawingGeometrySnapshot before = current.get();
        PathNode moved = new PathNode(new Vec2d(30, 10));
        moved.setSmoothControlPoints(new Vec2d(30, 20));
        DrawingGeometrySnapshot after = DrawingGeometrySnapshot.pen(List.of(moved));
        current.set(after);

        DrawingEditHistory.commitGeometryEdit(before, after);

        assertEquals(1, commandService.size());
        commandService.undo();
        assertEquals(DrawingGeometrySnapshot.Kind.PEN, current.get().getKind());
        assertEquals(1, current.get().getPathNodes().size());
        assertEquals(0.0, current.get().getPathNodes().getFirst().anchorX(), 1e-6);

        commandService.redo();
        assertEquals(30.0, current.get().getPathNodes().getFirst().anchorX(), 1e-6);
        assertEquals(PathNode.NodeType.SMOOTH,
                current.get().getPathNodes().getFirst().toPathNode().getType());
    }

    @Test
    void commitGeometryEditIgnoresNullSnapshots() {
        DrawingGeometrySnapshot snapshot = DrawingGeometrySnapshot.polyline(
                List.of(new Vec2d(0, 0), new Vec2d(10, 0)));

        DrawingEditHistory.commitGeometryEdit(null, snapshot);
        DrawingEditHistory.commitGeometryEdit(snapshot, null);

        assertEquals(0, commandService.size());
    }
}
