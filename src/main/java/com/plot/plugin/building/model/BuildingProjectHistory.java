package com.plot.plugin.building.model;

import com.plot.plugin.common.JsonSnapshotHistory;

/**
 * 建筑项目轻量撤销栈（深拷贝 JSON 快照）
 */
public class BuildingProjectHistory {
    private final JsonSnapshotHistory<BuildingProject> delegate = new JsonSnapshotHistory<>(
        BuildingProject::toJson,
        BuildingProject::fromJson
    );

    public void push(BuildingProject current) {
        delegate.push(current);
    }

    public BuildingProject undo(BuildingProject current) {
        return delegate.undo(current);
    }

    public BuildingProject redo(BuildingProject current) {
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
