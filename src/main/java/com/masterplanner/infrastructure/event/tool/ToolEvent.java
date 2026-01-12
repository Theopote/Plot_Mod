package com.masterplanner.infrastructure.event.tool;

import com.masterplanner.api.event.EventType;
import com.masterplanner.infrastructure.event.base.Event;

/**
 * 工具事件基类
 */
public class ToolEvent extends Event {
    // 工具事件类型
    public enum ToolEventType {
        TOOL_FILE,     // 文件操作
        TOOL_EDIT,     // 编辑操作
        TOOL_VIEW,     // 视图操作
        TOOL_SETTINGS, // 设置操作
        TOOL_SELECT,   // 工具选择
        TOOL_DRAW,     // 绘制工具
        TOOL_MODIFY,   // 修改工具
        TOOL_CONFIG    // 工具配置
    }
    
    private final String source;
    private final String toolId;
    private final ToolEventType type;
    private final boolean isPreview;
    
    /**
     * 创建一个工具事件
     * @param source 事件源
     * @param type 工具事件类型
     * @param toolId 工具ID
     */
    public ToolEvent(String source, ToolEventType type, String toolId) {
        this(source, type, toolId, false);
    }
    
    /**
     * 创建一个工具事件
     * @param source 事件源
     * @param type 工具事件类型
     * @param toolId 工具ID
     * @param isPreview 是否为预览
     */
    public ToolEvent(String source, ToolEventType type, String toolId, boolean isPreview) {
        super(EventType.TOOL_CHANGED);
        this.source = source;
        this.type = type;
        this.toolId = toolId;
        this.isPreview = isPreview;
    }
    
    public String getToolId() {
        return toolId;
    }
    
    public ToolEventType getToolEventType() {
        return type;
    }
    
    public boolean isPreview() {
        return isPreview;
    }
    
    @Override
    public String getSource() {
        return source;
    }
    
    @Override
    public String toString() {
        return String.format("ToolEvent[source=%s, tool=%s, type=%s, preview=%b]", 
            source, toolId, type, isPreview);
    }
} 