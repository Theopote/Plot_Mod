package com.plot.plugin.earthwork.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 土方工程 JSON 持久化 schema 版本号。
 * <p>
 * 版本链：v1（{@code regions[]}）→ v2（{@code sites[]}）→ v3（完整场地模型：孔洞、材料、边坡、
 * 设计面、现状快照元数据、全场平衡等）。
 */
public final class EarthworkProjectSchema {
    /** 仅 {@code regions[]}，无 {@code schemaVersion} 时视为 v1。 */
    public static final int V1 = 1;
    /** 引入 {@link com.plot.plugin.earthwork.model.EarthworkSite} 聚合根。 */
    public static final int V2 = 2;
    /**
     * 完整场地持久化：{@code outerRing}/{@code holes}、{@code designSurface}、
     * {@code edgeSettings}、{@code compositionPolicy}、{@code existingTerrainRef} 等。
     */
    public static final int V3 = 3;

    public static final int CURRENT = V3;

    private EarthworkProjectSchema() {
    }

  /**
   * 从已解析 JSON 根对象推断存储版本（用于迁移入口）。
   */
    public static int resolveStoredVersion(JsonObject root) {
        if (root == null) {
            return V1;
        }
        if (root.has("schemaVersion") && !root.get("schemaVersion").isJsonNull()) {
            return root.get("schemaVersion").getAsInt();
        }
        if (root.has("sites") && root.getAsJsonArray("sites").size() > 0) {
            return V2;
        }
        return V1;
    }

    public static int resolveStoredVersion(String json) {
        if (json == null || json.isBlank()) {
            return CURRENT;
        }
        try {
            return resolveStoredVersion(JsonParser.parseString(json).getAsJsonObject());
        } catch (RuntimeException e) {
            return V1;
        }
    }

    public static void assertSupported(int version) {
        if (version > CURRENT) {
            throw new IllegalArgumentException(
                "Earthwork project schema version " + version + " is newer than supported " + CURRENT);
        }
        if (version < V1) {
            throw new IllegalArgumentException("Invalid earthwork project schema version: " + version);
        }
    }
}
