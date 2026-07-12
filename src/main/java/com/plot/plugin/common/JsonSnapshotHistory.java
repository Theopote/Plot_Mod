package com.plot.plugin.common;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Function;

/**
 * 基于 JSON 快照的通用撤销/重做栈（Building / Earthwork / Road 共用）。
 */
public final class JsonSnapshotHistory<T> {
    private static final int DEFAULT_MAX_HISTORY = 50;

    private final Function<T, String> toJson;
    private final Function<String, T> fromJson;
    private final int maxHistory;
    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();

    public JsonSnapshotHistory(Function<T, String> toJson, Function<String, T> fromJson) {
        this(toJson, fromJson, DEFAULT_MAX_HISTORY);
    }

    public JsonSnapshotHistory(Function<T, String> toJson, Function<String, T> fromJson, int maxHistory) {
        this.toJson = toJson;
        this.fromJson = fromJson;
        this.maxHistory = Math.max(1, maxHistory);
    }

    public void push(T current) {
        if (current != null) {
            undoStack.push(toJson.apply(current));
            while (undoStack.size() > maxHistory) {
                undoStack.removeLast();
            }
        }
        redoStack.clear();
    }

    public T undo(T current) {
        if (undoStack.isEmpty()) {
            return current;
        }
        if (current != null) {
            redoStack.push(toJson.apply(current));
        }
        return fromJson.apply(undoStack.pop());
    }

    public T redo(T current) {
        if (redoStack.isEmpty()) {
            return current;
        }
        if (current != null) {
            undoStack.push(toJson.apply(current));
        }
        return fromJson.apply(redoStack.pop());
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
