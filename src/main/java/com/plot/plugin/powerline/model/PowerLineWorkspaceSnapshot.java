package com.plot.plugin.powerline.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * 线路工程 + 杆塔设计工程的组合快照（线路 Tab 撤销/重做用）。
 */
public final class PowerLineWorkspaceSnapshot {
    private static final Gson GSON = new GsonBuilder().create();

    private final PowerLineProject project;
    private final PowerLineDesignProject designProject;

    public PowerLineWorkspaceSnapshot(PowerLineProject project, PowerLineDesignProject designProject) {
        this.project = project != null ? project : new PowerLineProject();
        this.designProject = designProject != null ? designProject : new PowerLineDesignProject();
    }

    public PowerLineProject project() {
        return project;
    }

    public PowerLineDesignProject designProject() {
        return designProject;
    }

    public static PowerLineWorkspaceSnapshot capture(
            PowerLineProject project,
            PowerLineDesignProject designProject) {
        return new PowerLineWorkspaceSnapshot(
            PowerLineProject.fromJson(project.toJson()),
            PowerLineDesignProject.fromJson(designProject.toJson()));
    }

    public String toJson() {
        SnapshotData data = new SnapshotData();
        data.project = project.toJson();
        data.designProject = designProject.toJson();
        return GSON.toJson(data);
    }

    public static PowerLineWorkspaceSnapshot fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new PowerLineWorkspaceSnapshot(new PowerLineProject(), new PowerLineDesignProject());
        }
        SnapshotData data = GSON.fromJson(json, SnapshotData.class);
        if (data == null) {
            return new PowerLineWorkspaceSnapshot(new PowerLineProject(), new PowerLineDesignProject());
        }
        return new PowerLineWorkspaceSnapshot(
            PowerLineProject.fromJson(data.project),
            PowerLineDesignProject.fromJson(data.designProject));
    }

    private static final class SnapshotData {
        String project;
        String designProject;
    }
}
