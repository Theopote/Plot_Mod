package com.plot.plugin.powerline.model;

import com.plot.plugin.common.JsonSnapshotHistory;
import com.plot.plugin.powerline.design.PoleDesign;

/**
 * 杆塔设计器草稿撤销栈（与线路工程 {@link PowerLineProjectHistory} 独立）。
 */
public final class PoleDesignDraftHistory {
    private final JsonSnapshotHistory<PoleDesign> delegate = new JsonSnapshotHistory<>(
        PoleDesign::toJson,
        PoleDesign::fromJson
    );

    public void push(PoleDesign current) {
        delegate.push(current);
    }

    public PoleDesign undo(PoleDesign current) {
        return delegate.undo(current);
    }

    public PoleDesign redo(PoleDesign current) {
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
