package com.plot.core.state;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionStateTest {

    private AppState appState;
    private SelectionState selectionState;

    @BeforeEach
    void setUp() {
        appState = ApplicationContext.getInstance().getAppState();
        appState.ensureDefaultLayer();
        selectionState = new SelectionState(() -> appState);
    }

    @Test
    void setSelectedShapesClearsHighlightedFlag() {
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        line.setHighlighted(true);

        selectionState.setSelectedShapes(List.of(line));

        assertTrue(line.isSelected());
        assertFalse(line.isHighlighted());
    }

    @Test
    void addSelectedShapeClearsHighlightedFlag() {
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        line.setHighlighted(true);

        selectionState.addSelectedShape(line);

        assertTrue(line.isSelected());
        assertFalse(line.isHighlighted());
    }

    @Test
    void clearSelectionClearsStaleHighlightsOnCanvas() {
        LineShape stale = new LineShape(new Vec2d(0, 0), new Vec2d(5, 0));
        stale.setHighlighted(true);
        stale.setSelected(false);
        appState.addShape(stale);

        selectionState.clearSelection();

        assertFalse(stale.isHighlighted());
        assertFalse(stale.isSelected());
    }

    @Test
    void removeSelectedShapeClearsVisualState() {
        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(10, 0));
        line.setSelected(true);
        line.setHighlighted(true);
        selectionState.setSelectedShapes(List.of(line));

        selectionState.removeSelectedShape(line);

        assertFalse(line.isSelected());
        assertFalse(line.isHighlighted());
    }
}
