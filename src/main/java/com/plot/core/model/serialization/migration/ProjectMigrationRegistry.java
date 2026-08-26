package com.plot.core.model.serialization.migration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.plot.core.model.ProjectFormatException;
import com.plot.core.model.serialization.ProjectSnapshot;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 项目格式迁移注册表：按 {@code from → from+1 → … → CURRENT} 链式升级。
 */
public final class ProjectMigrationRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/ProjectMigration");
    private static final ProjectMigrationRegistry INSTANCE = new ProjectMigrationRegistry();

    private final Map<Integer, ProjectMigration> byFromVersion = new HashMap<>();

    private ProjectMigrationRegistry() {
        register(new MigrateV0ToV1());
        register(new MigrateV1ToV2());
    }

    public static ProjectMigrationRegistry getInstance() {
        return INSTANCE;
    }

    public void register(ProjectMigration migration) {
        Objects.requireNonNull(migration, "migration");
        if (migration.toVersion() != migration.fromVersion() + 1) {
            throw new IllegalArgumentException(
                "Migration must advance by exactly one version: "
                    + migration.fromVersion() + " → " + migration.toVersion());
        }
        ProjectMigration previous = byFromVersion.put(migration.fromVersion(), migration);
        if (previous != null) {
            throw new IllegalStateException(
                "Duplicate migration from version " + migration.fromVersion());
        }
    }

    /**
     * 将 JSON 根对象迁移到 {@link ProjectSnapshot#CURRENT_FORMAT_VERSION}。
     */
    public JsonObject migrateToCurrent(JsonObject root) throws ProjectFormatException {
        if (root == null || root.isJsonNull()) {
            throw new ProjectFormatException(
                ProjectFormatException.Reason.VALIDATION_FAILED,
                PlotI18n.error("error.plot.project.null_snapshot"));
        }

        int version = readFormatVersion(root);
        int target = ProjectSnapshot.CURRENT_FORMAT_VERSION;

        if (version > target) {
            throw new ProjectFormatException(
                ProjectFormatException.Reason.UNSUPPORTED_FORMAT_VERSION,
                PlotI18n.error("error.plot.project.unsupported_format", version, target));
        }

        if (version == target) {
            return root;
        }

        LOGGER.info("Migrating project format {} → {}", version, target);
        while (version < target) {
            ProjectMigration step = byFromVersion.get(version);
            if (step == null) {
                throw new ProjectFormatException(
                    ProjectFormatException.Reason.UNSUPPORTED_FORMAT_VERSION,
                    PlotI18n.error("error.plot.project.migration_path_missing", version, target));
            }
            LOGGER.debug("Applying migration {} → {}", step.fromVersion(), step.toVersion());
            try {
                step.migrate(root);
            } catch (ProjectFormatException e) {
                throw e;
            } catch (Exception e) {
                throw new ProjectFormatException(
                    ProjectFormatException.Reason.MIGRATION_FAILED,
                    PlotI18n.error("error.plot.project.migration_failed",
                        step.fromVersion(), step.toVersion()),
                    e);
            }

            int after = readFormatVersion(root);
            if (after != step.toVersion()) {
                throw new ProjectFormatException(
                    ProjectFormatException.Reason.MIGRATION_FAILED,
                    PlotI18n.error("error.plot.project.migration_version_mismatch",
                        step.fromVersion(), step.toVersion(), after));
            }
            version = after;
        }

        LOGGER.info("Project format migration complete (now v{})", version);
        return root;
    }

    static int readFormatVersion(JsonObject root) {
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
