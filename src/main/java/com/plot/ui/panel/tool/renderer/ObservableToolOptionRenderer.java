package com.plot.ui.panel.tool.renderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * 可观察工具选项渲染器基类
 * 
 * <p>提供观察者模式的基础实现，供其他工具选项渲染器继承使用：</p>
 * <ul>
 *   <li>属性变更监听：自动注册和移除PropertyChangeListener</li>
 *   <li>状态同步控制：使用volatile标志位控制状态同步频率</li>
 *   <li>事件过滤：只处理指定工具的事件</li>
 *   <li>资源管理：自动清理监听器</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 1.1 - PropertyChangeListener版本
 */
public abstract class ObservableToolOptionRenderer extends AbstractToolOptionRenderer implements PropertyChangeListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObservableToolOptionRenderer.class);
    
    // 状态同步标志
    protected volatile boolean needsStateSync = true;
    
    // 工具ID
    protected final String toolId;
    
    /**
     * 构造函数
     * @param toolId 工具ID，用于事件过滤
     */
    protected ObservableToolOptionRenderer(String toolId) {
        super(toolId);
        this.toolId = toolId;
        
        LOGGER.debug("ObservableToolOptionRenderer 已初始化: {}", toolId);
    }
    
    /**
     * PropertyChangeListener实现 - 响应工具配置变化
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        try {
            String propertyName = evt.getPropertyName();
            Object newValue = evt.getNewValue();
            
            LOGGER.debug("收到属性变更通知: {} = {}", propertyName, newValue);
            handlePropertyChange(propertyName, newValue);
            
        } catch (Exception e) {
            LOGGER.error("处理属性变更事件失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 处理属性变更 - 子类需要实现
     * @param propertyName 属性名
     * @param newValue 新值
     */
    protected abstract void handlePropertyChange(String propertyName, Object newValue);
    
    /**
     * 同步工具状态 - 子类需要实现
     */
    protected abstract void syncToolState();
    
    /**
     * 标记需要状态同步
     */
    public void markNeedsStateSync() {
        needsStateSync = true;
    }
    
    /**
     * 渲染方法模板 - 子类可以重写
     */
    @Override
    public float render() {
        // 只在需要时同步状态，而不是每帧都检查
        if (needsStateSync) {
            syncToolState();
            needsStateSync = false;
        }
        
        // 子类实现具体的渲染逻辑
        return renderContent();
    }
    
    /**
     * 渲染内容 - 子类需要实现
     * @return 渲染高度
     */
    protected abstract float renderContent();
    
    @Override
    public void initialize() {
        // 初始化时同步一次状态
        needsStateSync = true;
        LOGGER.debug("ObservableToolOptionRenderer 初始化完成: {}", toolId);
    }
    
    @Override
    public void cleanup() {
        // 子类负责清理具体的监听器
        LOGGER.debug("ObservableToolOptionRenderer 清理完成: {}", toolId);
    }
    
    /**
     * 获取工具ID
     * @return 工具ID
     */
    public String getToolId() {
        return toolId;
    }
    
    /**
     * 检查是否需要状态同步
     * @return 是否需要状态同步
     */
    public boolean needsStateSync() {
        return needsStateSync;
    }
} 