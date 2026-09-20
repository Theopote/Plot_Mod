package com.plot.plugin.pattern.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatternPendingEditTest {
    private PatternPendingEdit pendingEdit;
    private AtomicInteger snapshots;

    @BeforeEach
    void setUp() {
        pendingEdit = new PatternPendingEdit();
        snapshots = new AtomicInteger();
    }

    @Test
    void activationWithoutChangeDoesNotSnapshot() {
        pendingEdit.trackVec2("offset", true, false, true, false, false, false, snapshots::incrementAndGet);
        pendingEdit.trackVec2("offset", false, false, false, false, false, false, snapshots::incrementAndGet);

        assertEquals(0, snapshots.get());
    }

    @Test
    void firstChangeSnapshotsOnceForVec2Group() {
        pendingEdit.trackVec2("offset", true, false, true, false, false, false, snapshots::incrementAndGet);
        pendingEdit.trackVec2("offset", false, true, true, false, false, false, snapshots::incrementAndGet);
        pendingEdit.trackVec2("offset", false, true, true, false, false, false, snapshots::incrementAndGet);

        assertEquals(1, snapshots.get());
    }

    @Test
    void switchingFromXToZKeepsSingleSnapshot() {
        pendingEdit.trackVec2("offset", true, false, true, false, false, false, snapshots::incrementAndGet);
        pendingEdit.trackVec2("offset", false, true, true, false, false, false, snapshots::incrementAndGet);
        pendingEdit.trackVec2("offset", false, false, false, true, false, true, snapshots::incrementAndGet);
        pendingEdit.trackVec2("offset", false, false, false, false, true, true, snapshots::incrementAndGet);
        pendingEdit.trackVec2("offset", false, false, false, false, false, false, snapshots::incrementAndGet);

        assertEquals(1, snapshots.get());
    }

    @Test
    void separateGroupsSnapshotIndependently() {
        pendingEdit.trackVec2("offset", true, false, true, false, false, false, snapshots::incrementAndGet);
        pendingEdit.trackVec2("offset", false, true, true, false, false, false, snapshots::incrementAndGet);
        pendingEdit.trackVec2("offset", false, false, false, false, false, false, snapshots::incrementAndGet);

        pendingEdit.trackVec2("center", true, false, true, false, false, false, snapshots::incrementAndGet);
        pendingEdit.trackVec2("center", false, true, true, false, false, false, snapshots::incrementAndGet);

        assertEquals(2, snapshots.get());
    }
}
