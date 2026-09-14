package com.plot.plugin.powerline.ui;

import java.util.Objects;

/**
 * Defers workspace snapshot capture until the first value change in an ImGui edit session.
 * Focus/activation alone does not push history; Esc or blur without change leaves the stack clean.
 */
final class PowerLinePendingEdit {
    private String activeId;
    private boolean snapshotCaptured;

    void begin(String editId) {
        activeId = Objects.requireNonNull(editId, "editId");
        snapshotCaptured = false;
    }

    void captureOnChange(String editId, Runnable pushSnapshot) {
        if (editId == null || !editId.equals(activeId) || snapshotCaptured) {
            return;
        }
        Objects.requireNonNull(pushSnapshot, "pushSnapshot").run();
        snapshotCaptured = true;
    }

    void end(String editId) {
        if (editId != null && editId.equals(activeId)) {
            activeId = null;
            snapshotCaptured = false;
        }
    }

    void track(String editId, boolean activated, boolean changed, boolean deactivatedAfterEdit, Runnable pushSnapshot) {
        if (activated) {
            begin(editId);
        }
        if (changed) {
            captureOnChange(editId, pushSnapshot);
        }
        if (deactivatedAfterEdit) {
            end(editId);
        }
    }
}
