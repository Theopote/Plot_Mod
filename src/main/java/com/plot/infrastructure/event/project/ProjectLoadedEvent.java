package com.plot.infrastructure.event.project;

import com.plot.api.event.EventType;

/**
 * 项目加载事件
 */
public class ProjectLoadedEvent extends ProjectEvent {
    private final String filePath;

    public ProjectLoadedEvent(String filePath) {
        this("ProjectManager", filePath);
    }
    
    public ProjectLoadedEvent(String source, String filePath) {
        super(source, filePath, EventType.FILE_OPENED);
        this.filePath = filePath;
    }

    public String getFilePath() {
        return filePath;
    }

    @Override
    public String toString() {
        return String.format("ProjectLoadedEvent[source=%s, filePath=%s]", getSource(), filePath);
    }
} 