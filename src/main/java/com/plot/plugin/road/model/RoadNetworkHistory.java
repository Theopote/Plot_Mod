package com.plot.plugin.road.model;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 道路网络轻量撤销栈（深拷贝 JSON 快照）
 */
public class RoadNetworkHistory {
    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();
    private static final int MAX_HISTORY = 50;

    public void push(RoadNetwork current) {
        if (current != null) {
            undoStack.push(current.toJson());
            while (undoStack.size() > MAX_HISTORY) {
                undoStack.removeLast();
            }
        }
        redoStack.clear();
    }

    public RoadNetwork undo(RoadNetwork current) {
        if (undoStack.isEmpty()) {
            return current;
        }
        if (current != null) {
            redoStack.push(current.toJson());
        }
        return RoadNetwork.fromJson(undoStack.pop());
    }

    public RoadNetwork redo(RoadNetwork current) {
        if (redoStack.isEmpty()) {
            return current;
        }
        if (current != null) {
            undoStack.push(current.toJson());
        }
        return RoadNetwork.fromJson(redoStack.pop());
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
