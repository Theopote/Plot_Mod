package com.plot.core.persistence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 通用 JSON 文档迁移注册表：{@code from → from+1 → … → currentVersion}。
 */
public final class MigrationRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/MigrationRegistry");

    public interface Step {
        int fromVersion();

        int toVersion();

        void migrate(JsonObject root) throws Exception;
    }

    private final int currentVersion;
    private final Map<Integer, Step> byFromVersion = new HashMap<>();

    public MigrationRegistry(int currentVersion) {
        if (currentVersion < 0) {
            throw new IllegalArgumentException("currentVersion must be >= 0");
        }
        this.currentVersion = currentVersion;
    }

    public int currentVersion() {
        return currentVersion;
    }

    public void register(Step step) {
        Objects.requireNonNull(step, "step");
        if (step.toVersion() != step.fromVersion() + 1) {
            throw new IllegalArgumentException(
                "Migration must advance by exactly one version: "
                    + step.fromVersion() + " → " + step.toVersion());
        }
        Step previous = byFromVersion.put(step.fromVersion(), step);
        if (previous != null) {
            throw new IllegalStateException("Duplicate migration from version " + step.fromVersion());
        }
    }

    public JsonObject migrateToCurrent(JsonObject root) throws PersistenceException {
        if (root == null || root.isJsonNull()) {
            throw new PersistenceException(
                PersistenceException.Reason.VALIDATION_FAILED,
                "Document root is null");
        }

        int version = readFormatVersion(root);
        if (version > currentVersion) {
            throw new PersistenceException(
                PersistenceException.Reason.UNSUPPORTED_VERSION,
                "Unsupported format version " + version + " (current: " + currentVersion + ")");
        }
        if (version == currentVersion) {
            return root;
        }

        LOGGER.info("Migrating document format {} → {}", version, currentVersion);
        while (version < currentVersion) {
            Step step = byFromVersion.get(version);
            if (step == null) {
                throw new PersistenceException(
                    PersistenceException.Reason.UNSUPPORTED_VERSION,
                    "No migration path from " + version + " to " + currentVersion);
            }
            try {
                step.migrate(root);
            } catch (PersistenceException e) {
                throw e;
            } catch (Exception e) {
                throw new PersistenceException(
                    PersistenceException.Reason.MIGRATION_FAILED,
                    "Migration failed " + step.fromVersion() + " → " + step.toVersion(),
                    e);
            }
            int after = readFormatVersion(root);
            if (after != step.toVersion()) {
                throw new PersistenceException(
                    PersistenceException.Reason.MIGRATION_FAILED,
                    "Migration " + step.fromVersion() + " → " + step.toVersion()
                        + " left formatVersion=" + after);
            }
            version = after;
        }
        LOGGER.info("Document format migration complete (now v{})", version);
        return root;
    }

    public static int readFormatVersion(JsonObject root) {
        JsonElement element = root.get("formatVersion");
        if (element == null || element.isJsonNull()) {
            return 0;
        }
        try {
            return element.getAsInt();
        } catch (Exception e) {
            return 0;
        }
    }
}
