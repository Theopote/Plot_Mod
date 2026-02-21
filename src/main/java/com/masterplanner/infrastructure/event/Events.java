package com.masterplanner.infrastructure.event;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.event.EventType;
import com.masterplanner.infrastructure.event.base.Event;

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
     * 工具提示事件
     */
    public static class TooltipEvent extends Event {
        private final String text;
        private final Vec2d position;
        private final String source;

        public TooltipEvent(String text, Vec2d position) {
            this("TooltipManager", text, position);
        }
        
        public TooltipEvent(String source, String text, Vec2d position) {
            super(EventType.TOOL_CONFIG_CHANGED);
            this.source = source;
            this.text = text;
            this.position = position;
        }
        
        @Override
        public String getSource() {
            return source;
        }

        @Override
        public String toString() {
            return String.format("TooltipEvent[source=%s, text=%s, position=%s]", source, text, position);
        }

        public String getText() { return text; }
        public Vec2d getPosition() { return position; }
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