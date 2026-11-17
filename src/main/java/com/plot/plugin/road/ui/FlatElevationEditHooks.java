package com.plot.plugin.road.ui;

import com.plot.plugin.road.manager.RoadChangeKind;

/** Shared undo / stale-notification hooks for flat elevation edits. */
public final class FlatElevationEditHooks {

    private final Runnable recordUndoPointBeforeEdit;
    private final Runnable onWorkingProfileChanged;

    public FlatElevationEditHooks(Runnable recordUndoPointBeforeEdit, Runnable onWorkingProfileChanged) {
        this.recordUndoPointBeforeEdit = recordUndoPointBeforeEdit;
        this.onWorkingProfileChanged = onWorkingProfileChanged;
    }

    public void recordUndoPointBeforeEdit() {
        if (recordUndoPointBeforeEdit != null) {
            recordUndoPointBeforeEdit.run();
        }
    }

    public void notifyWorkingProfileChanged() {
        if (onWorkingProfileChanged != null) {
            onWorkingProfileChanged.run();
        }
    }

    public static FlatElevationEditHooks forProfileEditor(
            Runnable recordUndoPointBeforeEdit,
            Runnable onWorkingProfileChanged) {
        return new FlatElevationEditHooks(recordUndoPointBeforeEdit, onWorkingProfileChanged);
    }

    public static FlatElevationEditHooks forLiveNetwork(RoadUiContext ctx) {
        return new FlatElevationEditHooks(
            () -> ctx.networkManager().pushHistory(RoadChangeKind.VERTICAL_PROFILE),
            ctx::requestOverlayRefresh);
    }
}
