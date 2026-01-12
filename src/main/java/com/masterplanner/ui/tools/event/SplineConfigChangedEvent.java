package com.masterplanner.ui.tools.event;

import com.masterplanner.ui.tools.impl.drawing.config.SplineConfig;
import com.masterplanner.infrastructure.event.base.Event;
import com.masterplanner.api.event.EventType;

/**
 * 样条工具配置变更事件
 * 
 * <p>当样条工具的配置发生变更时触发此事件，用于通知UI组件更新显示。</p>
 * 
 * @author MasterPlanner Team
 * @version 1.1 - 继承Event基类
 */
public class SplineConfigChangedEvent extends Event {
    
    private final String toolId;
    private final String configKey;
    private final Object oldValue;
    private final Object newValue;
    private final SplineConfig currentConfig;
    
    /**
     * 构造配置变更事件
     * 
     * @param toolId 工具ID
     * @param configKey 变更的配置键
     * @param oldValue 旧值
     * @param newValue 新值
     * @param currentConfig 当前完整配置（副本）
     */
    public SplineConfigChangedEvent(String toolId, 
                                  String configKey, 
                                  Object oldValue, 
                                  Object newValue, 
                                  SplineConfig currentConfig) {
        super(EventType.TOOL_CONFIG_CHANGED); // 使用工具配置变更事件类型
        this.toolId = toolId;
        this.configKey = configKey;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.currentConfig = new SplineConfig(currentConfig); // 创建副本以避免并发修改
    }
    
    /**
     * 获取工具ID
     * @return 工具ID
     */
    public String getToolId() {
        return toolId;
    }
    
    /**
     * 获取变更的配置键
     * @return 配置键
     */
    public String getConfigKey() {
        return configKey;
    }

    /**
     * 获取新值
     * @return 新值
     */
    public Object getNewValue() {
        return newValue;
    }
    
    /**
     * 获取当前完整配置
     * @return 配置副本
     */
    public SplineConfig getCurrentConfig() {
        return currentConfig;
    }
    
    @Override
    public String getSource() {
        return "SplineTool";
    }
    
    @Override
    public String toString() {
        return String.format("SplineConfigChangedEvent{toolId='%s', configKey='%s', oldValue=%s, newValue=%s}", 
                toolId, configKey, oldValue, newValue);
    }
}