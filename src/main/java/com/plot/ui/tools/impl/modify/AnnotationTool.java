package com.plot.ui.tools.impl.modify;

import com.plot.api.snap.ISnapManager;
import com.plot.api.state.IAppState;
import com.plot.core.graphics.DrawContext;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventListener;
import com.plot.infrastructure.event.base.Event;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import com.plot.ui.component.ToolPanelIcons;
import com.plot.ui.tools.impl.modify.strategy.AnnotationStrategy;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 标注工具
 * 用于在画布上标注距离、角度、半径和面积
 * 单位：画布上投影下方的方块数量
 */
public class AnnotationTool extends ModifyTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationTool.class);
    
    private static final String TOOL_ID = "annotation";
    private static final String TOOL_NAME = "标注";
    private static final String TOOL_DESCRIPTION = "标注距离、角度、半径和面积";
    
    /**
     * 标注模式枚举
     */
    public enum AnnotationMode {
        DISTANCE("distance", "距离"),    // 两点距离
        ANGLE("angle", "角度"),          // 角度
        RADIUS("radius", "半径"),        // 半径
        AREA("area", "面积");            // 面积（区域内方块数量）
        
        private final String id;
        private final String displayName;
        
        AnnotationMode(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
        
        public String getId() {
            return id;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public static AnnotationMode fromId(String id) {
            for (AnnotationMode mode : values()) {
                if (mode.id.equals(id)) {
                    return mode;
                }
            }
            return DISTANCE; // 默认返回距离模式
        }
    }
    
    // 当前模式
    private AnnotationMode currentMode = AnnotationMode.DISTANCE;
    
    /**
     * 构造函数（依赖注入）
     * @param appState 应用状态管理器
     * @param snapManager 吸附管理器
     */
    public AnnotationTool(IAppState appState, ISnapManager snapManager) {
        super(TOOL_ID, TOOL_NAME, ToolPanelIcons.ANNOTATION, TOOL_DESCRIPTION, 
              appState, snapManager);
        
        // 监听工具配置事件
        eventBus.subscribe(ToolConfigEvent.class, new AnnotationToolConfigListener());
        
        LOGGER.debug("AnnotationTool 已创建");
    }
    
    /**
     * 工具配置监听器
     */
    private class AnnotationToolConfigListener implements EventListener {
        @Override
        public void onEvent(Event event) {
            if (!(event instanceof ToolConfigEvent configEvent)) {
                return;
            }
            
            if (!TOOL_ID.equals(configEvent.getToolId())) {
                return;
            }
            
            String key = configEvent.getConfigKey();
            String value = configEvent.getNewValue() != null ? configEvent.getNewValue().toString() : null;
            
            try {
                if ("mode".equals(key)) {
                    AnnotationMode newMode = AnnotationMode.fromId(value);
                    if (newMode != currentMode) {
                        currentMode = newMode;
                        LOGGER.debug("AnnotationTool: 模式更新为 {}", currentMode.getDisplayName());
                        
                        // 更新策略的模式
                        IModifyStrategy strategy = getStrategy();
                        if (strategy instanceof AnnotationStrategy annotationStrategy) {
                            annotationStrategy.setMode(newMode);
                        }
                        
                        // 重置状态
                        resetModification("模式切换");
                        setStatusMessage(getInitialStatusMessage());
                    }
                }
            } catch (Exception e) {
                LOGGER.error("AnnotationTool: 处理配置事件失败: {}", e.getMessage(), e);
            }
        }
    }
    
    @Override
    protected IModifyStrategy createStrategy() {
        return new AnnotationStrategy(currentMode);
    }
    
    @Override
    protected String getInitialStatusMessage() {
        return switch (currentMode) {
            case DISTANCE -> "距离模式：点击两点进行距离标注";
            case ANGLE -> "角度模式：选择两条有夹角的直线，右键完成选中时自动标注角度";
            case RADIUS -> "半径模式：点选或框选圆形、半圆、圆弧图形，右键完成选中时自动标注半径";
            case AREA -> "面积模式：选择区域，右键完成选中时自动标注面积";
        };
    }
    
    @Override
    protected void renderPreview(DrawContext context) {
        // 策略会处理预览渲染
        IModifyStrategy strategy = getStrategy();
        if (strategy != null) {
            strategy.renderPreview(context);
        }
    }
    
    @Override
    public void updateConfig(String key, String value) {
        // 配置更新通过事件系统处理
        LOGGER.debug("AnnotationTool.updateConfig: {} = {}", key, value);
    }
    
    /**
     * 获取当前模式
     */
    public AnnotationMode getCurrentMode() {
        return currentMode;
    }
    
    /**
     * 设置模式
     */
    public void setMode(AnnotationMode mode) {
        this.currentMode = mode;
        IModifyStrategy strategy = getStrategy();
        if (strategy instanceof AnnotationStrategy annotationStrategy) {
            annotationStrategy.setMode(mode);
        }
        resetModification("模式切换");
        setStatusMessage(getInitialStatusMessage());
    }
}
