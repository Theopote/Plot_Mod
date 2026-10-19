package com.plot.core.state;

import com.plot.core.model.Shape;
import com.plot.core.selection.Selection;
import com.plot.core.context.ApplicationContext;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.selection.SelectionChangedEvent;
import com.plot.utils.ExceptionDebug;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * 选择集状态：选中图形、高亮同步、防抖 SelectionChangedEvent。
 */
public final class SelectionState {
    private static final String EVENT_SELECTION = "selection";

    private final List<Shape> selectedShapes = new CopyOnWriteArrayList<>();
    private final Supplier<AppState> appStateSupplier;

    public SelectionState(Supplier<AppState> appStateSupplier) {
        this.appStateSupplier = appStateSupplier;
    }

    public Selection getSelection() {
        return new Selection(selectedShapes);
    }

    public List<Shape> getSelectedShapes() {
        return new ArrayList<>(selectedShapes);
    }

    public void setSelectedShapes(List<Shape> shapes) {
        List<Shape> oldSelection = new ArrayList<>(selectedShapes);
        List<Shape> newSelection = shapes != null ? new ArrayList<>(shapes) : new ArrayList<>();

        for (Shape old : oldSelection) {
            if (!newSelection.contains(old)) {
                clearShapeVisualState(old);
            }
        }

        for (Shape cur : newSelection) {
            try {
                cur.setSelected(true);
                cur.setHighlighted(false);
            } catch (Exception e) {
                ExceptionDebug.log("SelectionState: set new selection highlight", e);
            }
        }

        selectedShapes.clear();
        selectedShapes.addAll(newSelection);
        publishSelectionChangedEvent();
    }

    public void addSelectedShape(Shape shape) {
        if (shape != null && !selectedShapes.contains(shape)) {
            selectedShapes.add(shape);
            try {
                shape.setSelected(true);
                shape.setHighlighted(false);
            } catch (Exception e) {
                ExceptionDebug.log("SelectionState: add shape to selection", e);
            }
            publishSelectionChangedEvent();
        }
    }

    public void removeSelectedShape(Shape shape) {
        if (selectedShapes.remove(shape)) {
            clearShapeVisualState(shape);
            publishSelectionChangedEvent();
        }
    }

    public void clearSelection() {
        for (Shape s : new ArrayList<>(selectedShapes)) {
            clearShapeVisualState(s);
        }
        selectedShapes.clear();
        clearStaleHighlights();
        publishSelectionChangedEvent();
    }

    private static void clearShapeVisualState(Shape shape) {
        if (shape == null) {
            return;
        }
        try {
            shape.setSelected(false);
            shape.setHighlighted(false);
        } catch (Exception e) {
            ExceptionDebug.log("SelectionState: clear shape visual state", e);
        }
    }

    /** 清除框选预览等遗留的橙色高亮（selected=false 但 highlighted=true）。 */
    private void clearStaleHighlights() {
        try {
            AppState appState = appStateSupplier.get();
            if (appState == null) {
                return;
            }
            for (Shape shape : appState.getShapes()) {
                if (shape.isHighlighted()) {
                    shape.setHighlighted(false);
                }
            }
        } catch (Exception e) {
            ExceptionDebug.log("SelectionState: clear stale highlights", e);
        }
    }

    public void clear() {
        selectedShapes.clear();
    }

    private void publishSelectionChangedEvent() {
        DebouncedTasks.publishDebounced(EVENT_SELECTION, () -> {
            AppState appState = appStateSupplier.get();
            SelectionChangedEvent event = new SelectionChangedEvent(new ArrayList<>(selectedShapes), appState);
            ApplicationContext.getInstance().getEventBus().publish(event);
        });
    }
}
