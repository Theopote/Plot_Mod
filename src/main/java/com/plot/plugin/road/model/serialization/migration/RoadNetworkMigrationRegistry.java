package com.plot.plugin.road.model.serialization.migration;

import com.google.gson.JsonObject;
import com.plot.core.persistence.MigrationRegistry;
import com.plot.core.persistence.PersistenceException;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNetworkFormatException;

/**
 * Road network sidecar migrations via {@link MigrationRegistry}.
 */
public final class RoadNetworkMigrationRegistry {
    private static final RoadNetworkMigrationRegistry INSTANCE = new RoadNetworkMigrationRegistry();

    private final MigrationRegistry registry =
        new MigrationRegistry(RoadNetwork.CURRENT_FORMAT_VERSION);

    private RoadNetworkMigrationRegistry() {
        register(new RoadNetworkMigrateV0ToV1());
    }

    public static RoadNetworkMigrationRegistry getInstance() {
        return INSTANCE;
    }

    public void register(RoadNetworkMigration migration) {
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

    public JsonObject migrateToCurrent(JsonObject root) throws RoadNetworkFormatException {
        try {
            return registry.migrateToCurrent(root);
        } catch (PersistenceException e) {
            throw map(e);
        }
    }

    private static RoadNetworkFormatException map(PersistenceException e) {
        RoadNetworkFormatException.Reason reason = switch (e.getReason()) {
            case UNSUPPORTED_VERSION -> RoadNetworkFormatException.Reason.UNSUPPORTED_FORMAT_VERSION;
            case MIGRATION_FAILED -> RoadNetworkFormatException.Reason.MIGRATION_FAILED;
            case VALIDATION_FAILED, EMPTY_INPUT, INVALID_CONTENT, IO_ERROR ->
                RoadNetworkFormatException.Reason.VALIDATION_FAILED;
        };
        return new RoadNetworkFormatException(reason, e.getMessage(), e);
    }
}
