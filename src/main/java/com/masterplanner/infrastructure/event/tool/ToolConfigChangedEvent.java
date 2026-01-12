package com.masterplanner.infrastructure.event.tool;

/**
 * 工具配置改变事件
 */
public class ToolConfigChangedEvent extends ToolEvent {
    private final String configKey;
    private final String configValue;
    
    public ToolConfigChangedEvent(String toolId, String configKey, String configValue) {
        super("ToolManager", ToolEventType.TOOL_CONFIG, toolId);
        this.configKey = configKey;
        this.configValue = configValue;
    }
    
    public String getConfigKey() {
        return configKey;
    }
    
    public String getConfigValue() {
        return configValue;
    }
    
    @Override
    public String toString() {
        return String.format("ToolConfigChangedEvent[tool=%s, key=%s, value=%s]",
            getToolId(), configKey, configValue);
    }
} 