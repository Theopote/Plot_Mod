package com.plot.core.model.serialization.migration;

import com.google.gson.JsonObject;
import com.plot.core.model.ProjectFormatException;
import com.plot.core.model.serialization.ProjectSnapshot;
import com.plot.core.persistence.MigrationRegistry;
import com.plot.core.persistence.PersistenceException;

/**
 * 项目格式迁移：基于 {@link MigrationRegistry}，面向 {@link ProjectSnapshot}。
 */
public final class ProjectMigrationRegistry {
    private static final ProjectMigrationRegistry INSTANCE = new ProjectMigrationRegistry();

    private final MigrationRegistry registry =
        new MigrationRegistry(ProjectSnapshot.CURRENT_FORMAT_VERSION);

    private ProjectMigrationRegistry() {
        register(new MigrateV0ToV1());
        register(new MigrateV1ToV2());
    }

    public static ProjectMigrationRegistry getInstance() {
        return INSTANCE;
    }

    public void register(ProjectMigration migration) {
        registry.register(new MigrationRegistry.Step() {
            @Override
            public int fromVersion() {
                return migration.fromVersion();
            }

            @Override
            public int toVersion() {
                return migration.toVersion();
            }

            @Override
            public void migrate(JsonObject root) throws Exception {
                migration.migrate(root);
            }
        });
    }

    public JsonObject migrateToCurrent(JsonObject root) throws ProjectFormatException {
        try {
            return registry.migrateToCurrent(root);
        } catch (PersistenceException e) {
            throw map(e);
        }
    }

    private static ProjectFormatException map(PersistenceException e) {
        ProjectFormatException.Reason reason = switch (e.getReason()) {
            case UNSUPPORTED_VERSION -> ProjectFormatException.Reason.UNSUPPORTED_FORMAT_VERSION;
            case MIGRATION_FAILED -> ProjectFormatException.Reason.MIGRATION_FAILED;
            case VALIDATION_FAILED, EMPTY_INPUT, INVALID_CONTENT, IO_ERROR ->
                ProjectFormatException.Reason.VALIDATION_FAILED;
        };
        return new ProjectFormatException(reason, e.getMessage(), e);
    }
}
