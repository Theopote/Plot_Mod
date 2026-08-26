package com.plot.core.model.serialization.migration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.plot.core.model.ProjectFormatException;
import com.plot.utils.PlotI18n;

/**
 * v0（无 formatVersion 或显式 0）→ v1：补齐基础结构。
 */
public final class MigrateV0ToV1 implements ProjectMigration {
    @Override
    public int fromVersion() {
        return 0;
    }

    @Override
    public int toVersion() {
        return 1;
    }

    @Override
    public void migrate(JsonObject root) throws ProjectFormatException {
        if (root == null) {
            throw new ProjectFormatException(
                ProjectFormatException.Reason.MIGRATION_FAILED,
                PlotI18n.error("error.plot.project.migration_failed", 0, 1));
        }
        if (!root.has("layers") || root.get("layers").isJsonNull()) {
            root.add("layers", new JsonArray());
        } else if (!root.get("layers").isJsonArray()) {
            throw new ProjectFormatException(
                ProjectFormatException.Reason.MIGRATION_FAILED,
                PlotI18n.error("error.plot.project.migration_failed", 0, 1));
        }
        if (!root.has("name") || root.get("name").isJsonNull()) {
            root.addProperty("name", "Untitled");
        }
        root.addProperty("formatVersion", 1);
    }
}
