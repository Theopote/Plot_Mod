package com.masterplanner.infrastructure.event.clipboard;

import com.masterplanner.api.event.EventType;
import com.masterplanner.infrastructure.event.base.Event;

/**
 * 剪贴板事件
 */
public class ClipboardEvent extends Event {
    private final Type clipboardType;
    private final String source;
    
    /**
     * 剪贴板操作类型
     */
    public enum Type {
        COPY,   // 复制
        CUT,    // 剪切
        PASTE   // 粘贴
    }
    
    public ClipboardEvent(Type clipboardType) {
        this("ClipboardManager", clipboardType);
    }
    
    public ClipboardEvent(String source, Type clipboardType) {
        super(EventType.COMMAND_EXECUTED);  // 使用 COMMAND_EXECUTED 事件类型
        this.source = source;
        this.clipboardType = clipboardType;
    }
    
    public Type getClipboardType() {
        return clipboardType;
    }
    
    @Override
    public String getSource() {
        return source;
    }
    
    @Override
    public String toString() {
        return String.format("ClipboardEvent[source=%s, type=%s]", source, clipboardType);
    }
} 