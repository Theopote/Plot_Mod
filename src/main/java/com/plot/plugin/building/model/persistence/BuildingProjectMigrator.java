package com.plot.plugin.building.model.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 建筑项目 JSON schema 迁移编排：按版本链逐步升级至 {@link BuildingProjectSchema#CURRENT}。
 */
public final class BuildingProjectMigrator {
    private static final Gson GSON = new GsonBuilder().create();

    private BuildingProjectMigrator() {
    }

    /**
     * 将任意历史 JSON 规范化为当前 schema 的 JSON 字符串（不落盘）。
     */
    public static String normalizeJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int version = BuildingProjectSchema.resolveStoredVersion(root);
        BuildingProjectSchema.assertSupported(version);

        while (version < BuildingProjectSchema.CURRENT) {
            switch (version) {
                case BuildingProjectSchema.V0 -> migrateV0ToV1(root);
                default -> throw new IllegalArgumentException("No migration path from schema version " + version);
            }
            version++;
            root.addProperty("schemaVersion", version);
        }
        return GSON.toJson(root);
    }

    private static void migrateV0ToV1(JsonObject root) {
        if (!root.has("buildings") || root.get("buildings").isJsonNull()) {
            root.add("buildings", new JsonArray());
        }
    }
}
