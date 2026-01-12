package com.masterplanner.infrastructure.event.model;

import com.masterplanner.api.event.EventType;
import com.masterplanner.infrastructure.event.base.Event;

/**
 * 模型变化事件
 */
public class ModelChangedEvent extends Event {
    private final String modelId;
    private final ChangeType changeType;
    private final String source;
    
    /**
     * 变化类型枚举
     */
    public enum ChangeType {
        SHAPE_ADDED,      // 添加形状
        SHAPE_REMOVED,    // 移除形状
        SHAPE_MODIFIED,   // 修改形状
        LAYER_ADDED,      // 添加图层
        LAYER_REMOVED,    // 移除图层
        LAYER_MODIFIED,   // 修改图层
        SELECTION_CHANGED // 选择变化
    }
    
    public ModelChangedEvent(String modelId, ChangeType changeType) {
        this("ModelManager", modelId, changeType);
    }
    
    public ModelChangedEvent(String source, String modelId, ChangeType changeType) {
        super(EventType.VIEW_CHANGED);  // 使用 VIEW_CHANGED 事件类型，因为模型变化会影响视图
        this.source = source;
        this.modelId = modelId;
        this.changeType = changeType;
    }
    
    public String getModelId() {
        return modelId;
    }
    
    public ChangeType getChangeType() {
        return changeType;
    }
    
    @Override
    public String getSource() {
        return source;
    }
    
    @Override
    public String toString() {
        return String.format("ModelChangedEvent[source=%s, modelId=%s, type=%s]", 
            source, modelId, changeType);
    }
} 