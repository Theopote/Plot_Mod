package com.plot.core.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 原子写盘灾难场景：校验失败不得破坏已有文件，临时文件必须收口。
 */
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

    @Test
    void failedValidationLeavesExistingFileIntact() throws Exception {
        Path target = dir.resolve("keep.json");
        Files.writeString(target, "{\"v\":1}", StandardCharsets.UTF_8);

        assertThrows(PersistenceException.class, () ->
            AtomicFileWriter.write(target, "{\"v\":2}", AtomicFileWriter.Options.projectDocument(content -> {
                throw new IllegalStateException("reject");
            })));

        assertEquals("{\"v\":1}", Files.readString(target, StandardCharsets.UTF_8));
        assertFalse(Files.exists(dir.resolve("keep.json.tmp")));
    }

    @Test
    void backupAndAutosaveCreatedOnProjectModeWrite() throws Exception {
        Path target = dir.resolve("proj.json");
        Files.writeString(target, "{\"old\":true}", StandardCharsets.UTF_8);

        AtomicFileWriter.write(target, "{\"new\":true}", AtomicFileWriter.Options.projectDocument(c -> {
        }));

        assertEquals("{\"new\":true}", Files.readString(target));
        assertTrue(Files.exists(Path.of(target + BackupManager.BACKUP_SUFFIX))
            || Files.exists(dir.resolve("proj.json.bak")));
        assertTrue(Files.exists(dir.resolve("proj.json.bak")));
        assertTrue(Files.exists(dir.resolve("proj.json.autosave")));
        assertEquals("{\"old\":true}", Files.readString(dir.resolve("proj.json.bak")));
        assertFalse(Files.exists(dir.resolve("proj.json.tmp")));
    }
}
