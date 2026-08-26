package com.plot.core.model.serialization.migration;

import com.google.gson.JsonObject;
import com.plot.core.model.ProjectFormatException;

/**
 * 单步项目格式迁移：{@code fromVersion} → {@code toVersion}（通常 to = from + 1）。
 */
public interface ProjectMigration {
    int fromVersion();

    int toVersion();

    /**
     * 就地改写 JSON 根对象，完成后应写入新的 {@code formatVersion}。
     */
    void migrate(JsonObject root) throws ProjectFormatException;
}
