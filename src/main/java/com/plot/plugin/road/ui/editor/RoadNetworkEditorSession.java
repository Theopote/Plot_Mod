package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.model.RoadNetwork;

import java.util.Objects;
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

    private final RoadNetwork openSnapshot;
    private final long openRevision;
    private RoadNetwork workingNetwork;
    private final Predicate<RoadNetwork> dirtyPredicate;

    private RoadNetworkEditorSession(
            RoadNetwork openSnapshot,
            long openRevision,
            Predicate<RoadNetwork> dirtyPredicate) {
        this.openSnapshot = Objects.requireNonNull(openSnapshot, "openSnapshot");
        this.openRevision = openRevision;
        this.dirtyPredicate = Objects.requireNonNull(dirtyPredicate, "dirtyPredicate");
        this.workingNetwork = openSnapshot.snapshot();
    }

    public static RoadNetworkEditorSession open(
            RoadNetwork live,
            long openRevision,
            Predicate<RoadNetwork> dirtyPredicate) {
        Objects.requireNonNull(live, "live");
        return new RoadNetworkEditorSession(live.snapshot(), openRevision, dirtyPredicate);
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
        return dirtyPredicate.test(workingNetwork);
    }

    public void restore() {
        workingNetwork = openSnapshot.snapshot();
    }

    public void cancel() {
        restore();
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
