package com.masterplanner.infrastructure.event.tool;

import com.masterplanner.api.event.EventType;
import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.infrastructure.event.base.Event;
import java.util.List;
import java.util.Map;

/**
 * 工具配置预览事件
 * 用于在配置面板中实时显示参数调整效果的预览
 */
public class ToolConfigPreviewEvent extends Event {
    private final String toolId;       // 工具ID
    private final Map<String, String> config; // 配置参数
    private final List<Vec2d> previewPoints;  // 预览点列表
    private final String previewType;    // 预览类型
    
    /**
     * 创建工具配置预览事件
     * @param toolId 工具ID
     * @param config 配置参数
     * @param previewPoints 预览点列表
     * @param previewType 预览类型，指示应如何渲染预览
     */
    public ToolConfigPreviewEvent(String toolId, Map<String, String> config, 
            List<Vec2d> previewPoints, String previewType) {
        super(EventType.TOOL_CONFIG_CHANGED);
        this.toolId = toolId;
        this.config = config;
        this.previewPoints = previewPoints;
        this.previewType = previewType;
    }
    
    /**
     * 获取工具ID
     */
    public String getToolId() {
        return toolId;
    }
    
    /**
     * 获取配置参数
     */
    public Map<String, String> getConfig() {
        return config;
    }
    
    /**
     * 获取预览点列表
     */
    public List<Vec2d> getPreviewPoints() {
        return previewPoints;
    }
    
    /**
     * 获取预览类型
     */
    public String getPreviewType() {
        return previewType;
    }
    
    @Override
    public String toString() {
        return String.format("ToolConfigPreviewEvent{toolId='%s', config=%s, previewType='%s', points=%d}", 
            toolId, config, previewType, previewPoints.size());
    }
    
    @Override
    public String getSource() {
        return "ToolManager";
    }
} 