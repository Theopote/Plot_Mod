package com.plot.plugin.earthwork.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.plot.core.material.MaterialConversionModel;
import com.plot.plugin.earthwork.model.EarthworkProject;

/**
 * 土方工程 JSON schema 迁移编排：按版本链逐步升级至 {@link EarthworkProjectSchema#CURRENT}。
 */
public final class EarthworkProjectMigrator {
    private static final Gson GSON = new GsonBuilder().create();

    private EarthworkProjectMigrator() {
    }

    /**
     * 将任意历史 JSON 规范化为当前 schema 的 JSON 字符串（不落盘）。
     */
    public static String normalizeJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int version = EarthworkProjectSchema.resolveStoredVersion(root);
        EarthworkProjectSchema.assertSupported(version);

        while (version < EarthworkProjectSchema.CURRENT) {
            switch (version) {
                case EarthworkProjectSchema.V1 -> migrateV1ToV2(root);
                case EarthworkProjectSchema.V2 -> migrateV2ToV3(root);
                default -> throw new IllegalArgumentException("No migration path from schema version " + version);
            }
            version++;
            root.addProperty("schemaVersion", version);
        }
        return GSON.toJson(root);
    }

    /**
     * 解析 JSON 为运行时模型，自动执行 v1 → v2 → v3 迁移。
     */
    public static EarthworkProject load(String json) {
        return EarthworkProject.fromNormalizedJson(normalizeJson(json));
    }

    private static void migrateV1ToV2(JsonObject root) {
        String v2Json = EarthworkProject.migrateV1JsonToV2Json(GSON.toJson(root));
        JsonObject migrated = JsonParser.parseString(v2Json).getAsJsonObject();
        root.entrySet().clear();
        for (var entry : migrated.entrySet()) {
            root.add(entry.getKey(), entry.getValue());
        }
    }

    private static void migrateV2ToV3(JsonObject root) {
        root.remove("regions");
        if (!root.has("sites")) {
            return;
        }
        var sites = root.getAsJsonArray("sites");
        for (var siteElement : sites) {
            if (!siteElement.isJsonObject()) {
                continue;
            }
            normalizeSiteV2ToV3(siteElement.getAsJsonObject());
        }
    }

    private static void normalizeSiteV2ToV3(JsonObject site) {
        normalizeGeometryArray(site, "gradingZones");
        normalizeGeometryArray(site, "exclusionZones");
        if (!site.has("compositionPolicy") || site.get("compositionPolicy").isJsonNull()) {
            site.add("compositionPolicy", defaultCompositionPolicy());
        }
        if (!site.has("materialModel") || site.get("materialModel").isJsonNull()) {
            site.add("materialModel", defaultMaterialModel());
        }
    }

    private static void normalizeGeometryArray(JsonObject site, String arrayKey) {
        if (!site.has(arrayKey)) {
            return;
        }
        var array = site.getAsJsonArray(arrayKey);
        for (var element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            promoteOuterRing(object);
            if ("gradingZones".equals(arrayKey)) {
                ensureObject(object, "designSurface");
                ensureObject(object, "edgeSettings");
                ensureObject(object, "materialModel");
                JsonObject designSurface = object.getAsJsonObject("designSurface");
                if (designSurface.has("facets")) {
                    for (var facet : designSurface.getAsJsonArray("facets")) {
                        if (facet.isJsonObject()) {
                            promoteOuterRing(facet.getAsJsonObject());
                        }
                    }
                }
            }
        }
    }

    private static void promoteOuterRing(JsonObject geometry) {
        if (geometry.has("outerRing") && geometry.getAsJsonArray("outerRing").size() >= 3) {
            return;
        }
        if (geometry.has("outerPoints") && geometry.getAsJsonArray("outerPoints").size() >= 3) {
            geometry.add("outerRing", geometry.get("outerPoints").deepCopy());
        }
    }

    private static void ensureObject(JsonObject parent, String key) {
        if (!parent.has(key) || parent.get(key).isJsonNull()) {
            parent.add(key, new JsonObject());
        }
    }

    private static JsonObject defaultCompositionPolicy() {
        JsonObject policy = new JsonObject();
        policy.addProperty("overlapResolution", "HIGHEST_PRIORITY_WINS");
        policy.addProperty("balanceScope", "SITE");
        policy.addProperty("optimizationMode", "CONSTRAINED_ZONE_OPTIMIZATION");
        policy.addProperty("balanceMethod", "CONSTRAINED_ZONE_OPTIMIZATION");
        policy.addProperty("balanceResidualUniformPolish", true);
        policy.addProperty("outsideSiteBoundary", "IGNORE");
        policy.addProperty("exclusionPrecedence", "ABSOLUTE");
        policy.addProperty("breaklinePrecedence", "ABSOLUTE");
        policy.addProperty("blendWidthBlocks", 0);
        return policy;
    }

    private static JsonObject defaultMaterialModel() {
        JsonObject material = new JsonObject();
        material.addProperty("reusableRatio", MaterialConversionModel.DEFAULT_REUSABLE_RATIO);
        material.addProperty("cutToCompactedFillRatio", MaterialConversionModel.DEFAULT_CUT_TO_COMPACTED_FILL_RATIO);
        return material;
    }
}
