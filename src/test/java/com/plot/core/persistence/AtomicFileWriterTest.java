package com.plot.core.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicFileWriterTest {
    @TempDir
    Path dir;

    @Test
    void writeCreatesFileAtomically() throws Exception {
        Path target = dir.resolve("doc.json");
        AtomicFileWriter.write(target, "{\"ok\":true}");
        assertTrue(Files.isRegularFile(target));
        assertEquals("{\"ok\":true}", Files.readString(target));
        assertFalse(Files.exists(dir.resolve("doc.json.tmp")));
    }

    @Test
    void fingerprintTrackerSkipsUnchanged() {
        Path file = dir.resolve("a.json");
        ContentFingerprint.Tracker tracker = new ContentFingerprint.Tracker();
        String json = "{}";
        assertFalse(tracker.isUnchanged(json, file));
        tracker.markSaved(json, file);
        assertTrue(tracker.isUnchanged(json, file));
        assertFalse(tracker.isUnchanged("{\"x\":1}", file));
    }
}
