package com.plot.plugin.pattern.ui;

import java.util.Objects;

/**
 * Defers project-history capture until the first value change in an ImGui edit session.
 * Vec2 groups share one session across X/Z so switching fields does not push twice.
 */
final class PatternPendingEdit {
    private String activeGroupId;
    private boolean snapshotCaptured;

    void trackVec2(
            String groupId,
            boolean xActivated,
            boolean xChanged,
            boolean xActive,
            boolean zActivated,
            boolean zChanged,
            boolean zActive,
            Runnable pushSnapshot) {
        Objects.requireNonNull(groupId, "groupId");
        if (xActivated || zActivated) {
            if (!groupId.equals(activeGroupId)) {
                activeGroupId = groupId;
                snapshotCaptured = false;
            }
        }
        if ((xChanged || zChanged)
                && groupId.equals(activeGroupId)
                && !snapshotCaptured
                && pushSnapshot != null) {
            pushSnapshot.run();
            snapshotCaptured = true;
        }
        if (groupId.equals(activeGroupId) && !xActive && !zActive) {
            activeGroupId = null;
            snapshotCaptured = false;
        }
    }

    void reset() {
        activeGroupId = null;
        snapshotCaptured = false;
    }
}
