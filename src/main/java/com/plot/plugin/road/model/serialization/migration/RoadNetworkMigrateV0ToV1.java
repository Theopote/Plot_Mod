package com.plot.plugin.road.model.serialization.migration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNetworkFormatException;
import com.plot.utils.PlotI18n;

/**
 * v0（无 formatVersion 或显式 0）→ v1：补齐基础数组结构。
 */
public final class RoadNetworkMigrateV0ToV1 implements RoadNetworkMigration {
    @Override
    public int fromVersion() {
        return 0;
    }

    @Override
    public int toVersion() {
        return 1;
    }

    @Override
    public void migrate(JsonObject root) throws RoadNetworkFormatException {
        if (root == null) {
            throw new RoadNetworkFormatException(
                RoadNetworkFormatException.Reason.MIGRATION_FAILED,
                PlotI18n.error("error.plot.road.network.migration_failed", 0, 1));
        }
        ensureArray(root, "nodes");
        ensureArray(root, "edges");
        ensureArray(root, "roads");
        root.addProperty("formatVersion", RoadNetwork.CURRENT_FORMAT_VERSION);
    }

    private static void ensureArray(JsonObject root, String field) throws RoadNetworkFormatException {
        if (!root.has(field) || root.get(field).isJsonNull()) {
            root.add(field, new JsonArray());
            return;
        }
        if (!root.get(field).isJsonArray()) {
            throw new RoadNetworkFormatException(
                RoadNetworkFormatException.Reason.MIGRATION_FAILED,
                PlotI18n.error("error.plot.road.network.migration_failed", 0, 1));
        }
    }
}
