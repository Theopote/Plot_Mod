package com.plot.plugin.road.model;

import com.plot.plugin.common.JsonSnapshotHistory;

/**
 * 道路网络轻量撤销栈（深拷贝 JSON 快照）。
 *
 * <p>每次 {@link #commitStagedPush} 对当前 live 网络做 {@link RoadNetwork#snapshot()} 等价序列化；
 * undo/redo 用快照整体替换 live 实例，与单写者模型一致。
 */
public class RoadNetworkHistory {
    private final JsonSnapshotHistory<RoadNetwork> delegate = new JsonSnapshotHistory<>(
        RoadNetwork::toJson,
        RoadNetwork::parseSnapshot
    );

    public void stagePush(RoadNetwork current) {
        delegate.stagePush(current);
    }

    public void commitStagedPush() {
        delegate.commitStagedPush();
    }

    public RoadNetwork discardStagedPush(RoadNetwork current) {
        return delegate.discardStagedPush(current);
    }

    public boolean hasStagedPush() {
        return delegate.hasStagedPush();
    }

    /** @deprecated 使用 {@link #stagePush} + {@link #commitStagedPush} */
    @Deprecated
    public void push(RoadNetwork current) {
        delegate.push(current);
    }

    public RoadNetwork undo(RoadNetwork current) {
        return delegate.undo(current);
    }

    public RoadNetwork redo(RoadNetwork current) {
        return delegate.redo(current);
    }

    public boolean canUndo() {
        return delegate.canUndo();
    }

    public boolean canRedo() {
        return delegate.canRedo();
    }

    public void clear() {
        delegate.clear();
    }

    /** @see JsonSnapshotHistory#discardLatestUndoSnapshot */
    public RoadNetwork discardLatestUndoSnapshot(RoadNetwork current) {
        return delegate.discardLatestUndoSnapshot(current);
    }
}
