package com.plot.plugin.building.model.persistence;

import com.plot.plugin.building.model.BuildingProject;
import com.plot.plugin.building.model.persistence.BuildingProjectLoadResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 建筑项目持久化边界（Facade）：稳定对外 API，内部仍委托 {@link BuildingProject} JSON 实现。
 * <p>
 * 加载时经 {@link BuildingProjectMigrator} 自动迁移至当前 {@link BuildingProjectSchema}。
 */
public final class BuildingProjectPersistence {
    private BuildingProjectPersistence() {
    }

    public static String serialize(BuildingProject project) {
        return project.toJson();
    }

    public static BuildingProject deserialize(String json) {
        return BuildingProject.loadWithDiagnostics(json).project();
    }

    public static BuildingProjectLoadResult deserializeWithDiagnostics(String json) {
        return BuildingProject.loadWithDiagnostics(json);
    }

    public static BuildingProject load(Path path) throws IOException {
        return loadWithDiagnostics(path).project();
    }

    public static BuildingProjectLoadResult loadWithDiagnostics(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return BuildingProjectLoadResult.empty();
        }
        return BuildingProject.loadFromWithDiagnostics(path);
    }

    public static void save(BuildingProject project, Path path) throws IOException {
        project.saveTo(path);
    }

    public static BuildingProject snapshot(BuildingProject project) {
        return BuildingProject.fromJson(project.toJson());
    }
}
