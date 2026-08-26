package com.plot.core.model.serialization.migration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.plot.core.model.ProjectFormatException;
import com.plot.utils.PlotI18n;

/**
 * v1 → v2：规范化图层默认字段（opacity / zOrder / shapes）。
 */
public final class MigrateV1ToV2 implements ProjectMigration {
    @Override
    public int fromVersion() {
        return 1;
    }

    @Override
    public int toVersion() {
        return 2;
    }

    @Override
    public void migrate(JsonObject root) throws ProjectFormatException {
        if (root == null) {
            throw new ProjectFormatException(
                ProjectFormatException.Reason.MIGRATION_FAILED,
                PlotI18n.error("error.plot.project.migration_failed", 1, 2));
        }

        if (!root.has("layers") || root.get("layers").isJsonNull()) {
            root.add("layers", new JsonArray());
        }
        if (!root.get("layers").isJsonArray()) {
            throw new ProjectFormatException(
                ProjectFormatException.Reason.MIGRATION_FAILED,
                PlotI18n.error("error.plot.project.migration_failed", 1, 2));
        }

        JsonArray layers = root.getAsJsonArray("layers");
        for (JsonElement element : layers) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject layer = element.getAsJsonObject();
            if (!layer.has("opacity") || layer.get("opacity").isJsonNull()) {
                layer.addProperty("opacity", 1.0);
            }
            if (!layer.has("zOrder") || layer.get("zOrder").isJsonNull()) {
                layer.addProperty("zOrder", 0);
            }
            if (!layer.has("visible") || layer.get("visible").isJsonNull()) {
                layer.addProperty("visible", true);
            }
            if (!layer.has("locked") || layer.get("locked").isJsonNull()) {
                layer.addProperty("locked", false);
            }
            if (!layer.has("shapes") || layer.get("shapes").isJsonNull()) {
                layer.add("shapes", new JsonArray());
            }
        }

        if (!root.has("modified") || root.get("modified").isJsonNull()) {
            root.addProperty("modified", false);
        }

        root.addProperty("formatVersion", 2);
    }
}
