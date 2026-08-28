package com.plot.plugin.road.model.serialization.migration;

import com.google.gson.JsonObject;
import com.plot.plugin.road.model.RoadNetworkFormatException;

/**
 * Single-step road network sidecar format migration ({@code fromVersion} → {@code toVersion}).
 */
public interface RoadNetworkMigration {
    int fromVersion();

    int toVersion();

    void migrate(JsonObject root) throws RoadNetworkFormatException;
}
