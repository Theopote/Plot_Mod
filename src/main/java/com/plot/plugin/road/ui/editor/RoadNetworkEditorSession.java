package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.model.RoadNetwork;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * 浮动编辑器窗口级事务：打开时捕获快照，编辑期间写入工作副本，确认时一次性写回 live。
 */
public final class RoadNetworkEditorSession {

    public enum ConfirmResult {
        NO_CHANGES,
        APPLIED,
        CONFLICT
    }

    private static final int MAX_UNDO_DEPTH = 32;

    private final RoadNetwork openSnapshot;
    private final long openRevision;
    private RoadNetwork workingNetwork;
    private final Predicate<RoadNetwork> dirtyPredicate;
    /** 一旦变脏即闩住，避免拖滑条时每帧深比较横断面。 */
    private boolean dirtyLatched;
    /** 会话内撤销/重做（工作副本快照）；还原/取消或新编辑时清空重做栈。 */
    private final Deque<RoadNetwork> undoStack = new ArrayDeque<>();
    private final Deque<RoadNetwork> redoStack = new ArrayDeque<>();

    private RoadNetworkEditorSession(
            RoadNetwork openSnapshot,
            RoadNetwork workingNetwork,
            long openRevision,
            Predicate<RoadNetwork> dirtyPredicate) {
        this.openSnapshot = Objects.requireNonNull(openSnapshot, "openSnapshot");
        this.workingNetwork = Objects.requireNonNull(workingNetwork, "workingNetwork");
        this.openRevision = openRevision;
        this.dirtyPredicate = Objects.requireNonNull(dirtyPredicate, "dirtyPredicate");
    }

    /**
     * 打开会话：对 live 只做一次 snapshot 作为基线，再 deepCopy 出可写工作副本。
     * {@code dirtyCheck} 收到 (openSnapshot, working)。
     */
    public static RoadNetworkEditorSession open(
            RoadNetwork live,
            long openRevision,
            BiPredicate<RoadNetwork, RoadNetwork> dirtyCheck) {
        Objects.requireNonNull(live, "live");
        Objects.requireNonNull(dirtyCheck, "dirtyCheck");
        RoadNetwork openSnapshot = live.snapshot();
        RoadNetwork working = openSnapshot.snapshot();
        return new RoadNetworkEditorSession(
            openSnapshot,
            working,
            openRevision,
            w -> dirtyCheck.test(openSnapshot, w));
    }

    /**
     * @deprecated 使用 {@link #open(RoadNetwork, long, BiPredicate)}，避免调用方再预 snapshot。
     */
    @Deprecated
    public static RoadNetworkEditorSession open(
            RoadNetwork live,
            long openRevision,
            Predicate<RoadNetwork> dirtyPredicate) {
        Objects.requireNonNull(live, "live");
        Objects.requireNonNull(dirtyPredicate, "dirtyPredicate");
        RoadNetwork openSnapshot = live.snapshot();
        RoadNetwork working = openSnapshot.snapshot();
        return new RoadNetworkEditorSession(openSnapshot, working, openRevision, dirtyPredicate);
    }

    public RoadNetwork openSnapshot() {
        return openSnapshot;
    }

    public long openRevision() {
        return openRevision;
    }

    public RoadNetwork workingNetwork() {
        return workingNetwork;
    }

    public boolean isDirty() {
        if (dirtyLatched) {
            return true;
        }
        dirtyLatched = dirtyPredicate.test(workingNetwork);
        return dirtyLatched;
    }

    public void restore() {
        workingNetwork = openSnapshot.snapshot();
        dirtyLatched = false;
        clearUndoStack();
    }

    public void cancel() {
        restore();
    }

    /** 在即将改写工作副本前压入检查点，供会话内撤销；新编辑清空重做栈。 */
    public void pushUndoCheckpoint() {
        undoStack.push(workingNetwork.deepCopy());
        redoStack.clear();
        while (undoStack.size() > MAX_UNDO_DEPTH) {
            undoStack.removeLast();
        }
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }
        redoStack.push(workingNetwork.deepCopy());
        while (redoStack.size() > MAX_UNDO_DEPTH) {
            redoStack.removeLast();
        }
        workingNetwork = undoStack.pop();
        dirtyLatched = dirtyPredicate.test(workingNetwork);
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) {
            return false;
        }
        undoStack.push(workingNetwork.deepCopy());
        while (undoStack.size() > MAX_UNDO_DEPTH) {
            undoStack.removeLast();
        }
        workingNetwork = redoStack.pop();
        dirtyLatched = dirtyPredicate.test(workingNetwork);
        return true;
    }

    public void clearUndoStack() {
        undoStack.clear();
        redoStack.clear();
    }

    public ConfirmResult confirm(long liveRevision, RoadNetworkApplier applier) {
        Objects.requireNonNull(applier, "applier");
        if (!isDirty()) {
            return ConfirmResult.NO_CHANGES;
        }
        if (liveRevision != openRevision) {
            return ConfirmResult.CONFLICT;
        }
        applier.apply(openSnapshot, workingNetwork);
        return ConfirmResult.APPLIED;
    }

    /** 将工作副本中的变更写回 live 路网。 */
    @FunctionalInterface
    public interface RoadNetworkApplier {
        void apply(RoadNetwork openSnapshot, RoadNetwork workingNetwork);
    }
}
