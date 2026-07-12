package com.plot.plugin.earthwork.model;

import com.plot.plugin.common.JsonSnapshotHistory;

/**
 * 土方项目轻量撤销栈（深拷贝 JSON 快照）
 */
public class EarthworkProjectHistory {
    private final JsonSnapshotHistory<EarthworkProject> delegate = new JsonSnapshotHistory<>(
        EarthworkProject::toJson,
        EarthworkProject::fromJson
    );

    public void push(EarthworkProject current) {
        delegate.push(current);
    }

    public EarthworkProject undo(EarthworkProject current) {
        return delegate.undo(current);
    }

    public EarthworkProject redo(EarthworkProject current) {
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
