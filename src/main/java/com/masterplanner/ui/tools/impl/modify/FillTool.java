package com.masterplanner.ui.tools.impl.modify;

import com.masterplanner.api.snap.ISnapManager;
import com.masterplanner.core.state.AppState;
import com.masterplanner.ui.component.Icons;
import com.masterplanner.ui.tools.impl.modify.strategy.FillWithSelectionStrategy;
import com.masterplanner.ui.tools.impl.modify.strategy.IModifyStrategy;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.tool.ToolConfigEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 填充工具
 * 使用策略模式重构后的版本
 * 用于填充闭合区域，支持点击填充和边界填充两种模式
 * 
 * <p><strong>功能特点：</strong></p>
 * <ul>
 *   <li><strong>点击填充模式</strong>：直接在封闭区域内点击进行填充</li>
 *   <li><strong>边界填充模式</strong>：先选择边界，再点击区域内部确认填充</li>
 *   <li><strong>完整选择功能</strong>：支持点选、框选、多选等所有选择操作</li>
 *   <li><strong>智能模式切换</strong>：根据工具选项面板的选择自动切换策略</li>
 *   <li><strong>连续填充模式</strong>：支持连续多次填充操作</li>
 * </ul>
 */
public class FillTool extends ModifyTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(FillTool.class);
    
    // 模式常量
    private static final String MODE_POINT_FILL = "POINT_FILL";
    private static final String MODE_BOUNDARY_FILL = "BOUNDARY_FILL";
    
    // 当前模式
    private String currentMode = MODE_POINT_FILL;
    
    /**
     * 依赖注入构造函数
     */
    public FillTool(AppState appState, ISnapManager snapManager) {
        super("fill", "填充", Icons.FILL_IDENTIFIER, "填充闭合区域，支持点击填充和边界填充", appState, snapManager);
        LOGGER.info("FillTool 已创建，初始模式: {}", currentMode);
        
        // 订阅ToolConfigEvent
        EventBus.getInstance().subscribe(ToolConfigEvent.class, this::handleToolConfigEvent);
    }
    
    /**
     * @deprecated 使用依赖注入构造函数 {@link #FillTool(AppState, ISnapManager)}
     */
    @Deprecated
    public FillTool() {
        super("fill", "填充", Icons.FILL_IDENTIFIER, "填充闭合区域，支持点击填充和边界填充");
        LOGGER.warn("FillTool 使用兼容构造函数，建议使用依赖注入构造函数");
        
        // 订阅ToolConfigEvent
        EventBus.getInstance().subscribe(ToolConfigEvent.class, this::handleToolConfigEvent);
    }
    
    @Override
    protected IModifyStrategy createStrategy() {
        // 现在统一使用FillWithSelectionStrategy，它支持两种模式
        LOGGER.debug("创建FillWithSelectionStrategy");
        FillWithSelectionStrategy strategy = new FillWithSelectionStrategy(this.appState);
        
        // 根据当前模式设置策略的填充模式
        if (MODE_POINT_FILL.equals(currentMode)) {
            strategy.setFillMode(FillWithSelectionStrategy.FillMode.POINT_FILL);
        } else if (MODE_BOUNDARY_FILL.equals(currentMode)) {
            strategy.setFillMode(FillWithSelectionStrategy.FillMode.BOUNDARY_FILL);
        }
        
        return strategy;
    }
    
    /**
     * 处理工具配置事件
     * @param event 工具配置事件
     */
    private void handleToolConfigEvent(Object event) {
        if (event instanceof ToolConfigEvent configEvent) {
            if ("fill".equals(configEvent.getToolId())) {
                LOGGER.debug("FillTool 收到配置事件: {} = {}", 
                    configEvent.getOptionName(), configEvent.getValue());
                updateConfig(configEvent.getOptionName(), String.valueOf(configEvent.getValue()));
            }
        }
    }
    
    @Override
    public void updateConfig(String key, String value) {
        LOGGER.debug("更新配置: {} = {}", key, value);
        
        if ("mode".equals(key)) {
            // 模式切换
            if (!currentMode.equals(value)) {
                currentMode = value;
                LOGGER.info("切换填充模式: {} -> {}", currentMode, value);
                
                // 重置工具以使用新策略
                reset();
                LOGGER.info("工具已重置，将使用新策略: {}", currentMode);
            }
        } else if (getStrategy() instanceof FillWithSelectionStrategy fillStrategy) {
            // 将配置传递给FillWithSelectionStrategy
            fillStrategy.updateConfig(key, value);
        }
    }
    
    @Override
    protected void renderPreview(DrawContext context) {
        if (modifyStrategy != null) {
            modifyStrategy.renderPreview(context);
        }
    }
    
    @Override
    protected String getInitialStatusMessage() {
        return switch (currentMode) {
            case MODE_BOUNDARY_FILL -> "左键选择边界图形，右键完成选择";
            default -> "左键选择图形，右键完成选择";
        };
    }
    
    /**
     * 获取当前模式
     */
    public String getCurrentMode() {
        return currentMode;
    }
    
    /**
     * 设置当前模式
     */
    public void setCurrentMode(String mode) {
        if (!currentMode.equals(mode)) {
            currentMode = mode;
            LOGGER.info("设置填充模式: {}", mode);
            reset();
        }
    }
} 