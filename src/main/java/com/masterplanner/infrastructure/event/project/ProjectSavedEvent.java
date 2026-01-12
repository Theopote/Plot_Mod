package com.masterplanner.infrastructure.event.project;

/**
 * 项目保存事件
 */
public class ProjectSavedEvent extends ProjectEvent {
    private final String filePath;

    public ProjectSavedEvent(String projectId, String filePath) {
        super(projectId, null);
        this.filePath = filePath;
    }

    public String getFilePath() { return filePath; }
} 