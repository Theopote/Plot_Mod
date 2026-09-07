package com.plot.plugin.building.model.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 建筑项目 JSON 持久化 schema 版本号。
 * <p>
 * 版本链：v0（无 {@code schemaVersion}，仅 {@code buildings[]}）→ v1（显式版本号，当前）。
 */
public final class BuildingProjectSchema {
    /** 历史侧车 JSON：无 {@code schemaVersion} 字段。 */
    public static final int V0 = 0;
    /** 当前格式：写入 {@code schemaVersion: 1}，结构与 v0 兼容。 */
    public static final int V1 = 1;

    public static final int CURRENT = V1;

    private BuildingProjectSchema() {
    }

    /**
     * 从已解析 JSON 根对象推断存储版本（用于迁移入口）。
     */
    public static int resolveStoredVersion(JsonObject root) {
        if (root == null) {
            return V0;
        }
        if (root.has("schemaVersion") && !root.get("schemaVersion").isJsonNull()) {
            return root.get("schemaVersion").getAsInt();
        }
        return V0;
    }

    public static int resolveStoredVersion(String json) {
        if (json == null || json.isBlank()) {
            return CURRENT;
        }
        try {
            return resolveStoredVersion(JsonParser.parseString(json).getAsJsonObject());
        } catch (RuntimeException e) {
            return V0;
        }
    }

    public static void assertSupported(int version) {
        if (version > CURRENT) {
            throw new IllegalArgumentException(
                "Building project schema version " + version + " is newer than supported " + CURRENT);
        }
        if (version < V0) {
            throw new IllegalArgumentException("Invalid building project schema version: " + version);
        }
    }
}
