package com.plot.plugin.pattern.model;

import com.plot.plugin.common.JsonSnapshotHistory;

/**
 * 铺装图案项目轻量撤销栈（深拷贝 JSON 快照）。
 */
public class PatternProjectHistory {
    private final JsonSnapshotHistory<PatternProject> delegate = new JsonSnapshotHistory<>(
        PatternProject::toJson,
        PatternProject::fromJson
    );

    public void push(PatternProject current) {
        delegate.push(current);
    }

    public PatternProject undo(PatternProject current) {
        return delegate.undo(current);
    }

    public PatternProject redo(PatternProject current) {
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
