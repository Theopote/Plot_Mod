package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.model.RoadNetwork;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

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

    /**
     * 重新评估脏状态并更新闩锁。用于会话外撤销栈（如批量草稿）回退到与打开时等价后，
     * 避免 {@link #isDirty()} 因 dirtyLatched 仍弹出未保存确认。
     */
    public void refreshDirtyState() {
        dirtyLatched = dirtyPredicate.test(workingNetwork);
    }

    public void restore() {
        workingNetwork = openSnapshot.snapshot();
        dirtyLatched = false;
        clearUndoStack();
    }

    public void cancel() {
        restore();
    }

    /**
     * 在即将改写工作副本前压入检查点，供会话内撤销；新编辑清空重做栈。
     * <p>
     * 滑条等「几乎总会改写」的路径可直接用；若操作可能失败，改用
     * {@link #stageUndoCheckpoint()} + {@link #commitStagedUndoCheckpoint}，
     * 避免失败时留下空撤销步并清掉重做栈。
     */
    public void pushUndoCheckpoint() {
        undoStack.push(workingNetwork.deepCopy());
        redoStack.clear();
        while (undoStack.size() > MAX_UNDO_DEPTH) {
            undoStack.removeLast();
        }
    }

    /**
     * 暂存撤销检查点（深拷贝当前工作副本），不改动 undo/redo 栈。
     * 仅当随后的编辑成功时再 {@link #commitStagedUndoCheckpoint}。
     */
    public StagedUndoCheckpoint stageUndoCheckpoint() {
        return new StagedUndoCheckpoint(workingNetwork.deepCopy());
    }

    /**
     * 提交暂存检查点：压入撤销栈并清空重做栈。
     */
    public void commitStagedUndoCheckpoint(StagedUndoCheckpoint staged) {
        if (staged == null || staged.snapshot() == null) {
            return;
        }
        undoStack.push(staged.snapshot());
        redoStack.clear();
        while (undoStack.size() > MAX_UNDO_DEPTH) {
            undoStack.removeLast();
        }
    }

    /**
     * 先暂存检查点再执行编辑；仅成功时提交检查点，失败不污染撤销/重做栈。
     */
    public <T> T runWithUndoCheckpoint(Supplier<T> mutation, java.util.function.Predicate<T> successTest) {
        Objects.requireNonNull(mutation, "mutation");
        Objects.requireNonNull(successTest, "successTest");
        StagedUndoCheckpoint staged = stageUndoCheckpoint();
        T result = mutation.get();
        if (successTest.test(result)) {
            commitStagedUndoCheckpoint(staged);
        }
        return result;
    }

    /** 编辑前暂存的撤销检查点（成功后再提交）。 */
    public record StagedUndoCheckpoint(RoadNetwork snapshot) {
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
