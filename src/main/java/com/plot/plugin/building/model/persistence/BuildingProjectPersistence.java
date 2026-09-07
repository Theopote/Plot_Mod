package com.plot.plugin.building.model.persistence;

import com.plot.plugin.building.model.BuildingProject;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 建筑项目持久化边界（Facade）：稳定对外 API，内部仍委托 {@link BuildingProject} JSON 实现。
 * <p>
 * 后续 format v2 / DTO 迁移时，调用方无需改动。
 */
public final class BuildingProjectPersistence {
    private BuildingProjectPersistence() {
    }

    public static String serialize(BuildingProject project) {
        return project.toJson();
    }

    public static BuildingProject deserialize(String json) {
        return BuildingProject.fromJson(json);
    }

    public static BuildingProject load(Path path) throws IOException {
        return BuildingProject.loadFrom(path);
    }

    public static void save(BuildingProject project, Path path) throws IOException {
        project.saveTo(path);
    }

    public static BuildingProject snapshot(BuildingProject project) {
        return BuildingProject.fromJson(project.toJson());
    }
}
