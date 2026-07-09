package com.plot.ui.tools.impl.modify;

import com.plot.api.model.ICanvas;
import com.plot.api.snap.ISnapManager;
import com.plot.api.state.IAppState;
import com.plot.core.graphics.DrawContext;
import com.plot.core.snap.SnapManager;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import com.plot.ui.component.Icons;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy;
import com.plot.ui.tools.impl.modify.strategy.SelectionStrategy;
import imgui.ImDrawList;
import com.plot.ui.canvas.CanvasCamera;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// import java.awt.Color; // 未直接使用

/**
 * 选择工具 - 策略模式版本
 * 
 * <p>采用策略模式架构的现代化选择工具：</p>
 * <ul>
 *   <li><strong>策略委托</strong>：所有选择逻辑委托给SelectionStrategy处理</li>
 *   <li><strong>配置驱动</strong>：支持多种选择模式的动态配置</li>
 *   <li><strong>状态管理</strong>：清晰的状态转换和错误处理</li>
 *   <li><strong>渲染优化</strong>：高效的选择框和套索渲染</li>
 * </ul>
 * 
 * <p><strong>支持的选择模式：</strong></p>
 * <ul>
 *   <li>普通选择：点击选择，拖动框选</li>
 *   <li>套索选择：自由绘制选择区域</li>
 *   <li>窗口选择：只选择完全包含的图形</li>
 *   <li>交叉选择：选择相交的图形</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 2.0 - 策略模式版本
 */
public class SelectionTool extends ModifyTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionTool.class);
    
    // 配置键常量
    public static final String CONFIG_SELECTION_MODE = "selection_mode";
    public static final String CONFIG_VALUE_NORMAL = "normal";
    public static final String CONFIG_VALUE_LASSO = "lasso";
    
    // 当前选择模式
    private SelectionStrategy.SelectionMode currentMode = SelectionStrategy.SelectionMode.NORMAL;
    
    /**
     * 构造函数（依赖注入版本）
     */
    public SelectionTool(IAppState appState, ISnapManager snapManager, ICanvas canvas) {
        super("select", Icons.SELECT_IDENTIFIER, 
              appState, snapManager);
        
        // 设置画布引用
        setCanvas(canvas);
        
        // 注册事件监听器
        setupEventListeners();
        
        LOGGER.debug("SelectionTool 初始化完成");
    }
    
    /**
     * 构造函数（兼容版本）
     */
    public SelectionTool(ICanvas canvas) {
        this(AppState.getInstance(), SnapManager.getInstance(), canvas);
    }
    
    /**
     * 设置事件监听器
     */
    private void setupEventListeners() {
        // 监听工具配置事件
        eventBus.subscribe(ToolConfigEvent.class, event -> {
            ToolConfigEvent configEvent = (ToolConfigEvent) event;
            if ("select".equals(configEvent.getToolId())) {
                LOGGER.info("SelectionTool: 收到工具配置事件 key={}, value={}", configEvent.getConfigKey(), configEvent.getNewValue());
                updateConfig(configEvent.getConfigKey(), String.valueOf(configEvent.getNewValue()));
            }
        });
    }

    @Override
    protected IModifyStrategy createStrategy() {
        SelectionStrategy strategy = new SelectionStrategy();
        strategy.setSelectionMode(currentMode);
        return strategy;
    }

    @Override
    protected String getInitialStatusMessage() {
        return switch (currentMode) {
            case NORMAL -> "status.plot.select.normal";
            case LASSO -> "status.plot.select.lasso";
        };
    }

    @Override
    protected void renderPreview(DrawContext context) {
        LOGGER.debug("SelectionTool.renderPreview(DrawContext): 调用渲染，当前状态: {}", getCurrentState());
        
        // 渲染控制点编辑工具的预览
        ControlPointEditTool editTool = getControlPointEditTool();
        if (editTool != null && editTool.isActive()) {
            editTool.renderPreview(context);
        }
        
        // 选择工具应该始终尝试渲染（让策略决定是否需要渲染）
        try {
            IModifyStrategy strategy = getStrategy();
            if (strategy != null) {
                LOGGER.debug("SelectionTool.renderPreview(DrawContext): 委托给策略渲染");
                strategy.renderPreview(context);
            } else {
                LOGGER.warn("SelectionTool.renderPreview(DrawContext): 策略为null");
            }
        } catch (Exception e) {
            LOGGER.error("SelectionTool.renderPreview(DrawContext): 渲染时出错", e);
        }
    }

    /**
     * ImGui渲染支持
     */
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        LOGGER.debug("SelectionTool.renderPreview: 调用渲染，当前状态: {}", getCurrentState());
        
        // 渲染控制点编辑工具的预览
        ControlPointEditTool editTool = getControlPointEditTool();
        if (editTool != null && editTool.isActive()) {
            editTool.renderPreview(drawList, camera);
        }
        
        // 选择工具应该始终尝试渲染（让策略决定是否需要渲染）
        try {
            IModifyStrategy strategy = getStrategy();
            if (strategy != null) {
                LOGGER.debug("SelectionTool.renderPreview: 委托给策略渲染");
                strategy.renderPreview(drawList, camera);
            } else {
                LOGGER.warn("SelectionTool.renderPreview: 策略为null");
            }
        } catch (Exception e) {
            LOGGER.error("SelectionTool.renderPreview: 渲染时出错", e);
        }
    }

    @Override
    public void updateConfig(String key, String value) {
        LOGGER.info("SelectionTool.updateConfig: 收到配置更新 key={}, value={}", key, value);
        try {
            if (key.equals(CONFIG_SELECTION_MODE)) {
                LOGGER.info("SelectionTool.updateConfig: 处理选择模式配置更新");
                SelectionStrategy.SelectionMode newMode = parseSelectionMode(value);
                if (newMode != currentMode) {
                    LOGGER.info("SelectionTool.updateConfig: 模式变化 {} -> {}", currentMode, newMode);
                    currentMode = newMode;

                    // 更新策略的选择模式
                    IModifyStrategy strategy = getStrategy();
                    if (strategy instanceof SelectionStrategy selectionStrategy) {
                        LOGGER.info("SelectionTool.updateConfig: 更新策略的选择模式");
                        selectionStrategy.setSelectionMode(newMode);
                    } else {
                        LOGGER.warn("SelectionTool.updateConfig: 策略不是SelectionStrategy类型: {}",
                                strategy != null ? strategy.getClass().getSimpleName() : "null");
                    }

                    // 更新状态消息
                    setStatusMessage(getInitialStatusMessage());

                    LOGGER.info("SelectionTool 选择模式已更新: {}", newMode.getDisplayName());
                } else {
                    LOGGER.debug("SelectionTool.updateConfig: 模式没有变化，当前已经是: {}", currentMode);
                }
            } else {
                LOGGER.debug("SelectionTool: 未知配置键: {}", key);
            }
        } catch (Exception e) {
            LOGGER.error("SelectionTool 更新配置失败: key={}, value={}", key, value, e);
        }
    }
    
    /**
     * 解析选择模式配置值
     */
    private SelectionStrategy.SelectionMode parseSelectionMode(String value) {
        return switch (value) {
            case CONFIG_VALUE_NORMAL -> SelectionStrategy.SelectionMode.NORMAL;
            case CONFIG_VALUE_LASSO -> SelectionStrategy.SelectionMode.LASSO;
            default -> {
                LOGGER.warn("未知的选择模式配置值: {}, 使用默认值", value);
                yield SelectionStrategy.SelectionMode.NORMAL;
            }
        };
    }

    @Override
    public void onActivate() {
        super.onActivate();
        
        // 选择工具激活时的特殊处理
        LOGGER.debug("SelectionTool 已激活，当前模式: {}", currentMode.getDisplayName());
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        LOGGER.debug("SelectionTool 已停用");
    }

    @Override
    public String getDefaultCursor() {
        return "select";
    }

    // ====== 便利方法 ======
    
    /**
     * 获取当前选择模式
     */
    public SelectionStrategy.SelectionMode getCurrentSelectionMode() {
        return currentMode;
    }

    /**
     * 检查是否正在选择
     */
    @Override
    public boolean isSelecting() {
        IModifyStrategy strategy = getStrategy();
        return strategy instanceof SelectionStrategy selectionStrategy && 
               selectionStrategy.isSelecting();
    }
} 