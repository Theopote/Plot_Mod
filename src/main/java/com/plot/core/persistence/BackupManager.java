package com.plot.core.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * .bak / .autosave 辅助（Core Project 全量保存使用）。
 */
public final class BackupManager {
    public static final String BACKUP_SUFFIX = ".bak";
    public static final String AUTOSAVE_SUFFIX = ".autosave";

    private BackupManager() {
    }

    public static void backupExisting(Path target) throws IOException {
        if (target == null || !Files.isRegularFile(target)) {
            return;
        }
        Path backupFile = Path.of(target.toString() + BACKUP_SUFFIX);
        Files.copy(target, backupFile, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void updateAutosave(Path target) throws IOException {
        if (target == null || !Files.isRegularFile(target)) {
            return;
        }
        Path autosaveFile = Path.of(target.toString() + AUTOSAVE_SUFFIX);
        Files.copy(target, autosaveFile, StandardCopyOption.REPLACE_EXISTING);
    }
}
