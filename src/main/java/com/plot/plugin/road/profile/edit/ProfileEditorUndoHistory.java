package com.plot.plugin.road.profile.edit;

import com.plot.plugin.road.model.RoadNetwork;

import java.util.ArrayDeque;
import java.util.Deque;

/** In-session undo/redo stack for the profile editor working copy. */
final class ProfileEditorUndoHistory {

    private static final int MAX_DEPTH = 48;

    private final Deque<RoadNetwork> undoStack = new ArrayDeque<>();
    private final Deque<RoadNetwork> redoStack = new ArrayDeque<>();

    void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    void record(RoadNetwork workingNetwork) {
        if (workingNetwork == null) {
            return;
        }
        undoStack.push(workingNetwork.snapshot());
        while (undoStack.size() > MAX_DEPTH) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    boolean canUndo() {
        return !undoStack.isEmpty();
    }

    boolean canRedo() {
        return !redoStack.isEmpty();
    }

    RoadNetwork undo(RoadNetwork workingNetwork) {
        if (workingNetwork == null || undoStack.isEmpty()) {
            return workingNetwork;
        }
        redoStack.push(workingNetwork.snapshot());
        return undoStack.pop();
    }

    RoadNetwork redo(RoadNetwork workingNetwork) {
        if (workingNetwork == null || redoStack.isEmpty()) {
            return workingNetwork;
        }
        undoStack.push(workingNetwork.snapshot());
        return redoStack.pop();
    }
}
