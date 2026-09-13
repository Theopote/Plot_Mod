package com.plot.plugin.powerline.model;

import com.plot.plugin.common.JsonSnapshotHistory;

/**
 * 电力线路工作区轻量撤销栈（线路工程 + 杆塔设计工程 JSON 快照）。
 */
public class PowerLineProjectHistory {
    private final JsonSnapshotHistory<PowerLineWorkspaceSnapshot> delegate = new JsonSnapshotHistory<>(
        PowerLineWorkspaceSnapshot::toJson,
        PowerLineWorkspaceSnapshot::fromJson
    );

    public void push(PowerLineProject project, PowerLineDesignProject designProject) {
        delegate.push(PowerLineWorkspaceSnapshot.capture(project, designProject));
    }

    public PowerLineWorkspaceSnapshot undo(
            PowerLineProject currentProject,
            PowerLineDesignProject currentDesignProject) {
        return delegate.undo(PowerLineWorkspaceSnapshot.capture(currentProject, currentDesignProject));
    }

    public PowerLineWorkspaceSnapshot redo(
            PowerLineProject currentProject,
            PowerLineDesignProject currentDesignProject) {
        return delegate.redo(PowerLineWorkspaceSnapshot.capture(currentProject, currentDesignProject));
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
