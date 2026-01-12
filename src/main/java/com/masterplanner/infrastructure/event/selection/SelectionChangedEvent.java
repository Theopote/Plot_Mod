package com.masterplanner.infrastructure.event.selection;

import com.masterplanner.api.event.EventType;
import com.masterplanner.infrastructure.event.base.Event;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import java.util.List;

/**
 * 选择改变事件
 */
public class SelectionChangedEvent extends Event {
    private final List<Shape> selectedShapes;
    private final AppState appState;
    private final String source;

    public SelectionChangedEvent(List<Shape> selectedShapes, AppState appState) {
        this("SelectionManager", selectedShapes, appState);
    }
    
    public SelectionChangedEvent(String source, List<Shape> selectedShapes, AppState appState) {
        super(EventType.SELECTION_CHANGED);
        this.source = source;
        this.selectedShapes = selectedShapes;
        this.appState = appState;
    }

    public List<Shape> getSelectedShapes() {
        return selectedShapes;
    }

    public AppState getAppState() {
        return appState;
    }
    
    @Override
    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return String.format("SelectionChangedEvent[source=%s, selectedShapes=%d]", 
            source, selectedShapes.size());
    }
} 