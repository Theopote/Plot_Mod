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
    private String stagedUndoSnapshot;

    public JsonSnapshotHistory(Function<T, String> toJson, Function<String, T> fromJson) {
        this(toJson, fromJson, DEFAULT_MAX_HISTORY);
    }

    public JsonSnapshotHistory(Function<T, String> toJson, Function<String, T> fromJson, int maxHistory) {
        this.toJson = toJson;
        this.fromJson = fromJson;
        this.maxHistory = Math.max(1, maxHistory);
    }

    /** 推入撤销帧并立即清空 redo（一次性 API，Building 等模块仍可使用）。 */
    public void push(T current) {
        stagePush(current);
        commitStagedPush();
    }

    /** 暂存 mutation 前快照；成功 {@link #commitStagedPush()} 前不清空 redo。 */
    public void stagePush(T current) {
        stagedUndoSnapshot = current != null ? toJson.apply(current) : null;
    }

    /** 将暂存快照写入 undo 栈并清空 redo。 */
    public void commitStagedPush() {
        if (stagedUndoSnapshot != null) {
            undoStack.push(stagedUndoSnapshot);
            while (undoStack.size() > maxHistory) {
                undoStack.removeLast();
            }
            stagedUndoSnapshot = null;
        }
        redoStack.clear();
    }

    /** 放弃暂存快照并恢复到 mutation 前状态；redo 栈不变。 */
    public T discardStagedPush(T current) {
        if (stagedUndoSnapshot == null) {
            return current;
        }
        T restored = fromJson.apply(stagedUndoSnapshot);
        stagedUndoSnapshot = null;
        return restored;
    }

    public boolean hasStagedPush() {
        return stagedUndoSnapshot != null;
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
        stagedUndoSnapshot = null;
    }

    /**
     * 丢弃 undo 栈顶帧并恢复（legacy：已 commit 的帧）。
     * 道路 mutation 失败应优先 {@link #discardStagedPush}。
     */
    public T discardLatestUndoSnapshot(T current) {
        if (undoStack.isEmpty()) {
            return current;
        }
        return fromJson.apply(undoStack.pop());
    }
}
