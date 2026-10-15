package com.plot.plugin.road.ui;

import com.plot.plugin.road.manager.RoadChangeKind;

/** Shared undo / stale-notification hooks for flat elevation edits. */
final class FlatElevationEditHooks {

    private final Runnable recordUndoPointBeforeEdit;
    private final Runnable onWorkingProfileChanged;

    FlatElevationEditHooks(Runnable recordUndoPointBeforeEdit, Runnable onWorkingProfileChanged) {
        this.recordUndoPointBeforeEdit = recordUndoPointBeforeEdit;
        this.onWorkingProfileChanged = onWorkingProfileChanged;
    }

    void recordUndoPointBeforeEdit() {
        if (recordUndoPointBeforeEdit != null) {
            recordUndoPointBeforeEdit.run();
        }
    }

    void notifyWorkingProfileChanged() {
        if (onWorkingProfileChanged != null) {
            onWorkingProfileChanged.run();
        }
    }

    static FlatElevationEditHooks forProfileEditor(
            Runnable recordUndoPointBeforeEdit,
            Runnable onWorkingProfileChanged) {
        return new FlatElevationEditHooks(recordUndoPointBeforeEdit, onWorkingProfileChanged);
    }

    static FlatElevationEditHooks forLiveNetwork(RoadUiContext ctx) {
        return new FlatElevationEditHooks(
            () -> ctx.networkManager().pushHistory(RoadChangeKind.VERTICAL_PROFILE),
            ctx::requestOverlayRefresh);
    }
}
