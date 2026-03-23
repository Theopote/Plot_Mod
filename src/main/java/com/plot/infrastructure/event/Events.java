package com.plot.infrastructure.event;

import com.plot.api.event.EventType;
import com.plot.infrastructure.event.base.Event;

/**
 * 事件类型定义
 */
public class Events {

    /**
     * 状态消息事件
     */
    public static class StatusMessageEvent extends Event {
        private final String message;
        private final String source;

        public StatusMessageEvent(String message) {
            this("StatusManager", message);
        }
        
        public StatusMessageEvent(String source, String message) {
            super(EventType.TOOL_CONFIG_CHANGED);
            this.source = source;
            this.message = message;
        }
        
        @Override
        public String getSource() {
            return source;
        }

        @Override
        public String toString() {
            return String.format("StatusMessageEvent[source=%s, message=%s]", source, message);
        }

        public String getMessage() { return message; }
    }


    /**
     * 警告事件
     * 用于显示警告对话框
     */
    public static class WarningEvent extends Event {
        private final String message;
        private final String source;
        
        public WarningEvent(String source, String message) {
            super(EventType.WARNING);
            this.source = source;
            this.message = message;
        }
        
        @Override
        public String getSource() {
            return source;
        }

        @Override
        public String toString() {
            return String.format("WarningEvent[source=%s, message=%s]", source, message);
        }

        public String getMessage() { return message; }
    }

    /**
     * 光标改变事件
     */
    public static class CursorChangedEvent extends Event {
        private final String cursorType;
        private final String source;

        public CursorChangedEvent(String cursorType) {
            this("CursorManager", cursorType);
        }
        
        public CursorChangedEvent(String source, String cursorType) {
            super(EventType.TOOL_CONFIG_CHANGED);
            this.source = source;
            this.cursorType = cursorType;
        }
        
        @Override
        public String getSource() {
            return source;
        }

        @Override
        public String toString() {
            return String.format("CursorChangedEvent[source=%s, cursorType=%s]", source, cursorType);
        }

    }
} 