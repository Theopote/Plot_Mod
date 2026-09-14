package com.plot.plugin.pick;

import com.plot.core.model.Shape;
import com.plot.core.state.AppState;

import java.util.List;

/** 拾取会话在帧间保存画布选择快照，用于计算 selection delta。 */
public final class PathPickSessionSnapshot {
    private List<Shape> previousCanvasSelection = List.of();
    private List<Shape> sessionStartSelection = List.of();
    private boolean initialized;

    public void reset() {
        previousCanvasSelection = List.of();
        sessionStartSelection = List.of();
        initialized = false;
    }

    public void beginSession(AppState appState) {
        sessionStartSelection = List.copyOf(appState.getSelectedShapes());
        previousCanvasSelection = sessionStartSelection;
        initialized = true;
    }

    public List<Shape> sessionStartSelection() {
        return sessionStartSelection;
    }

    public void ensureInitialized(AppState appState) {
        if (!initialized) {
            beginSession(appState);
        }
    }

    public List<Shape> previousSelection() {
        return previousCanvasSelection;
    }

    public void capture(AppState appState) {
        previousCanvasSelection = List.copyOf(appState.getSelectedShapes());
    }
}
