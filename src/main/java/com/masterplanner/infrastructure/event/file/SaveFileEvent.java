package com.masterplanner.infrastructure.event.file;

import com.masterplanner.api.event.EventType;

/**
 * 保存文件事件
 */
public class SaveFileEvent extends FileEvent {
    public SaveFileEvent() {
        super(null, EventType.FILE_SAVED);
    }

    @Override
    public String toString() {
        return "SaveFileEvent[]";
    }
} 