package com.masterplanner.infrastructure.event.file;

/**
 * 新建文件事件
 */
public class NewFileEvent extends FileEvent {
    public NewFileEvent() {
        super(null, null);
    }

    @Override
    public String toString() {
        return "NewFileEvent[]";
    }
} 