package com.plot.plugin.road.model;

import com.plot.plugin.common.JsonSnapshotHistory;

/**
 * 道路网络轻量撤销栈（深拷贝 JSON 快照）
 */
public class RoadNetworkHistory {
    private final JsonSnapshotHistory<RoadNetwork> delegate = new JsonSnapshotHistory<>(
        RoadNetwork::toJson,
        RoadNetwork::fromJson
    );

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
}
