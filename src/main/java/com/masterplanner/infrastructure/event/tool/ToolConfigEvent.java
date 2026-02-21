package com.masterplanner.infrastructure.event.tool;

import com.masterplanner.api.event.EventType;
import com.masterplanner.infrastructure.event.base.Event;

/**
 * 工具配置事件
 * 当工具配置发生变化时触发
 */
public class ToolConfigEvent extends Event {
    private final String toolId;
    private final String configKey;
    private final Object oldValue;
    private final Object newValue;
    private final String source;

    public ToolConfigEvent(String toolId, String configKey, Object oldValue, Object newValue) {
        this("ToolConfig", toolId, configKey, oldValue, newValue);
    }
    
    public ToolConfigEvent(String source, String toolId, String configKey, Object oldValue, Object newValue) {
        super(EventType.TOOL_CHANGED);
        this.source = source;
        this.toolId = toolId;
        this.configKey = configKey;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public String getToolId() {
        return toolId;
    }

    public String getConfigKey() {
        return configKey;
    }

    /**
     * 获取配置键（别名方法，保持向后兼容性）
     * @return 配置键
     */
    public String getOptionName() {
        return configKey;
    }

    public Object getNewValue() {
        return newValue;
    }

    /**
     * 获取配置值（别名方法，保持向后兼容性）
     * @return 配置值
     */
    public Object getValue() {
        return newValue;
    }
    
    @Override
    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return String.format("ToolConfigEvent[source=%s, toolId=%s, configKey=%s, oldValue=%s, newValue=%s]", 
                           source, toolId, configKey, oldValue, newValue);
    }
} 