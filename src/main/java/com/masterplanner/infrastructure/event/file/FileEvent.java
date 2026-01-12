package com.masterplanner.infrastructure.event.file;

import com.masterplanner.api.event.EventType;
import com.masterplanner.infrastructure.event.base.Event;

/**
 * 文件事件基类
 */
public abstract class FileEvent extends Event {
    protected final String filePath;
    protected final String source;

    /**
     * 构造文件事件
     * @param filePath 文件路径
     * @param type 事件类型
     */
    protected FileEvent(String filePath, EventType type) {
        this("FileManager", filePath, type);
    }
    
    /**
     * 构造文件事件
     * @param source 事件源
     * @param filePath 文件路径
     * @param type 事件类型
     */
    protected FileEvent(String source, String filePath, EventType type) {
        super(type);  // 调用父类构造函数，传入事件类型
        this.source = source;
        this.filePath = filePath;
    }

    public String getFilePath() {
        return filePath;
    }
    
    @Override
    public String getSource() {
        return source;
    }
} 