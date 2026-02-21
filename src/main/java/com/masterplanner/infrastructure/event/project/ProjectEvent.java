package com.masterplanner.infrastructure.event.project;

import com.masterplanner.api.event.EventType;
import com.masterplanner.infrastructure.event.base.Event;

/**
 * 项目事件基类
 */
public abstract class ProjectEvent extends Event {
    protected final String projectId;
    protected final String source;

    /**
     * 构造项目事件
     * @param projectId 项目ID
     * @param type 事件类型
     */
    protected ProjectEvent(String projectId, EventType type) {
        this("ProjectManager", projectId, type);
    }
    
    /**
     * 构造项目事件
     * @param source 事件源
     * @param projectId 项目ID
     * @param type 事件类型
     */
    protected ProjectEvent(String source, String projectId, EventType type) {
        super(type);  // 调用父类构造函数，传入事件类型
        this.source = source;
        this.projectId = projectId;
    }

    @Override
    public String getSource() {
        return source;
    }
} 