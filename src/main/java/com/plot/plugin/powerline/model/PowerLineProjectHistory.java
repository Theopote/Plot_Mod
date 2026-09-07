package com.plot.plugin.powerline.model;

import com.plot.plugin.common.JsonSnapshotHistory;

/**
 * 电力线路项目轻量撤销栈（深拷贝 JSON 快照）。
 */
public class PowerLineProjectHistory {
    private final JsonSnapshotHistory<PowerLineProject> delegate = new JsonSnapshotHistory<>(
        PowerLineProject::toJson,
        PowerLineProject::fromJson
    );

    public void push(PowerLineProject current) {
        delegate.push(current);
    }

    public PowerLineProject undo(PowerLineProject current) {
        return delegate.undo(current);
    }

    public PowerLineProject redo(PowerLineProject current) {
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
