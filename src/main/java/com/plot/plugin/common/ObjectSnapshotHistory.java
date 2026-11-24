package com.plot.plugin.common;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Function;

/**
 * 基于内存深拷贝的通用撤销/重做栈（道路主路网等热路径优先于 JSON 快照）。
 *
 * <p>栈中持有独立副本；恢复时转移所有权给调用方，避免与栈内条目共享可变状态。
 */
public final class ObjectSnapshotHistory<T> {
    private static final int DEFAULT_MAX_HISTORY = 50;

    private final Function<T, T> copy;
    private final int maxHistory;
    private final Deque<T> undoStack = new ArrayDeque<>();
    private final Deque<T> redoStack = new ArrayDeque<>();
    private T stagedUndoSnapshot;

    public ObjectSnapshotHistory(Function<T, T> copy) {
        this(copy, DEFAULT_MAX_HISTORY);
    }

    public ObjectSnapshotHistory(Function<T, T> copy, int maxHistory) {
        this.copy = copy;
        this.maxHistory = Math.max(1, maxHistory);
    }

    /** 推入撤销帧并立即清空 redo。 */
    public void push(T current) {
        stagePush(current);
        commitStagedPush();
    }

    /** 暂存 mutation 前快照；成功 {@link #commitStagedPush()} 前不清空 redo。 */
    public void stagePush(T current) {
        stagedUndoSnapshot = current != null ? copy.apply(current) : null;
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
        T restored = stagedUndoSnapshot;
        stagedUndoSnapshot = null;
        return restored;
    }

    public boolean hasStagedPush() {
        return stagedUndoSnapshot != null;
    }

    public T undo(T current) {
        if (stagedUndoSnapshot != null) {
            return discardStagedPush(current);
        }
        if (undoStack.isEmpty()) {
            return current;
        }
        if (current != null) {
            redoStack.push(copy.apply(current));
        }
        return undoStack.pop();
    }

    public T redo(T current) {
        if (stagedUndoSnapshot != null) {
            current = discardStagedPush(current);
        }
        if (redoStack.isEmpty()) {
            return current;
        }
        if (current != null) {
            undoStack.push(copy.apply(current));
        }
        return redoStack.pop();
    }

    public boolean canUndo() {
        return stagedUndoSnapshot != null || !undoStack.isEmpty();
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
        return undoStack.pop();
    }
}
