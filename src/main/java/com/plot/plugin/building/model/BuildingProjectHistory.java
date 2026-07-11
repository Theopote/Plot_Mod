package com.plot.plugin.building.model;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 建筑项目轻量撤销栈（深拷贝 JSON 快照）
 */
public class BuildingProjectHistory {
    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();
    private static final int MAX_HISTORY = 50;

    public void push(BuildingProject current) {
        if (current != null) {
            undoStack.push(current.toJson());
            while (undoStack.size() > MAX_HISTORY) {
                undoStack.removeLast();
            }
        }
        redoStack.clear();
    }

    public BuildingProject undo(BuildingProject current) {
        if (undoStack.isEmpty()) {
            return current;
        }
        if (current != null) {
            redoStack.push(current.toJson());
        }
        return BuildingProject.fromJson(undoStack.pop());
    }

    public BuildingProject redo(BuildingProject current) {
        if (redoStack.isEmpty()) {
            return current;
        }
        if (current != null) {
            undoStack.push(current.toJson());
        }
        return BuildingProject.fromJson(redoStack.pop());
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}
