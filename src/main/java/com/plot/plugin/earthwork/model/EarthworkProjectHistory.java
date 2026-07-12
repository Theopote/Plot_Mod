package com.plot.plugin.earthwork.model;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 土方项目轻量撤销栈（深拷贝 JSON 快照）
 */
public class EarthworkProjectHistory {
    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();
    private static final int MAX_HISTORY = 50;

    public void push(EarthworkProject current) {
        if (current != null) {
            undoStack.push(current.toJson());
            while (undoStack.size() > MAX_HISTORY) {
                undoStack.removeLast();
            }
        }
        redoStack.clear();
    }

    public EarthworkProject undo(EarthworkProject current) {
        if (undoStack.isEmpty()) {
            return current;
        }
        if (current != null) {
            redoStack.push(current.toJson());
        }
        return EarthworkProject.fromJson(undoStack.pop());
    }

    public EarthworkProject redo(EarthworkProject current) {
        if (redoStack.isEmpty()) {
            return current;
        }
        if (current != null) {
            undoStack.push(current.toJson());
        }
        return EarthworkProject.fromJson(redoStack.pop());
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
