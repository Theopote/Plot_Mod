package com.plot.core.state;

import com.plot.core.model.Shape;
import com.plot.core.selection.Selection;
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
                try {
                    old.setSelected(false);
                } catch (Exception e) {
                    ExceptionDebug.log("SelectionState: clear previous selection", e);
                }
            }
        }

        for (Shape cur : newSelection) {
            try {
                cur.setSelected(true);
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
            } catch (Exception e) {
                ExceptionDebug.log("SelectionState: add shape to selection", e);
            }
            publishSelectionChangedEvent();
        }
    }

    public void removeSelectedShape(Shape shape) {
        if (selectedShapes.remove(shape)) {
            try {
                shape.setSelected(false);
            } catch (Exception e) {
                ExceptionDebug.log("SelectionState: remove shape from selection", e);
            }
            publishSelectionChangedEvent();
        }
    }

    public void clearSelection() {
        if (selectedShapes.isEmpty()) {
            return;
        }
        for (Shape s : new ArrayList<>(selectedShapes)) {
            try {
                s.setSelected(false);
            } catch (Exception e) {
                ExceptionDebug.log("SelectionState: clear all selection highlights", e);
            }
        }
        selectedShapes.clear();
        publishSelectionChangedEvent();
    }

    public void clear() {
        selectedShapes.clear();
    }

    private void publishSelectionChangedEvent() {
        DebouncedTasks.publishDebounced(EVENT_SELECTION, () -> {
            AppState appState = appStateSupplier.get();
            SelectionChangedEvent event = new SelectionChangedEvent(new ArrayList<>(selectedShapes), appState);
            EventBus.getInstance().publish(event);
        });
    }
}
