package com.masterplanner.infrastructure.event.file;

import com.masterplanner.api.event.EventType;

/**
 * 导入文件事件
 */
public class ImportFileEvent extends FileEvent {
    public ImportFileEvent() {
        super(null, EventType.FILE_IMPORT);
    }
    
    public ImportFileEvent(String source) {
        super(source, null, EventType.FILE_IMPORT);
    }

    @Override
    public String toString() {
        return String.format("ImportFileEvent[source=%s]", getSource());
    }
} 