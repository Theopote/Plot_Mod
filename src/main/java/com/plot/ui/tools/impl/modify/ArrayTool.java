package com.plot.ui.tools.impl.modify;

import com.plot.api.snap.ISnapManager;
import com.plot.api.state.IAppState;
import com.plot.core.graphics.DrawContext;
import com.plot.core.state.AppState;
import com.plot.ui.component.Icons;
import com.plot.ui.tools.impl.modify.strategy.ArrayStrategy;
import com.plot.ui.tools.impl.modify.strategy.ArrayWithSelectionStrategy;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy;
import com.plot.ui.tools.impl.modify.config.ArrayConfigManager;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 阵列工具 - 策略模式版本
 *
 * <p>用于创建图形的阵列复制，采用策略模式架构：</p>
 * <ul>
 *   <li>支持矩形阵列（行列排列）</li>
 *   <li>支持环形阵列（圆形排列）</li>
 *   <li>支持路径阵列（沿路径排列）</li>
 *   <li>支持可调节的间距和数量</li>
 * </ul>
 *
 * @author Plot Team
 * @version 2.0 - 策略模式版本
 */
public class ArrayTool extends ModifyTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArrayTool.class);
    
    // 配置管理器
    private ArrayConfigManager configManager;

    /**
     * 构造函数（推荐方式 - 依赖注入）
     * @param appState 应用状态管理器
     * @param snapManager 吸附管理器
     */
    public ArrayTool(IAppState appState, ISnapManager snapManager) {
        super("array", "阵列", Icons.ARRAY_IDENTIFIER, "创建图形的阵列复制",
              (AppState) appState, snapManager);
        LOGGER.info("ArrayTool 已创建");
        
        // 订阅ToolConfigEvent
        EventBus.getInstance().subscribe(ToolConfigEvent.class, this::handleToolConfigEvent);
    }

    /**
     * 构造函数（兼容方式 - 单例获取）
     * @deprecated 推荐使用依赖注入构造函数
     */
    @Deprecated
    public ArrayTool() {
        super("array", "阵列", Icons.ARRAY_IDENTIFIER, "创建图形的阵列复制");
        LOGGER.info("ArrayTool 已创建（兼容模式）");
        
        // 订阅ToolConfigEvent
        EventBus.getInstance().subscribe(ToolConfigEvent.class, this::handleToolConfigEvent);
    }
    
    /**
     * 处理工具配置事件
     * @param event 工具配置事件
     */
    private void handleToolConfigEvent(Object event) {
        if (event instanceof ToolConfigEvent configEvent) {
            if ("array".equals(configEvent.getToolId())) {
                LOGGER.debug("ArrayTool 收到配置事件: {} = {}", 
                    configEvent.getOptionName(), configEvent.getValue());
                updateConfig(configEvent.getOptionName(), String.valueOf(configEvent.getValue()));
            }
        }
    }

    @Override
    protected IModifyStrategy createStrategy() {
        // 使用新的ArrayWithSelectionStrategy

        // 初始化配置管理器（暂时注释掉，因为需要适配新的策略）
        // this.configManager = new ArrayConfigManager(arrayStrategy);
        
        return new ArrayWithSelectionStrategy();
    }

    @Override
    protected String getInitialStatusMessage() {
        return "点击选择要阵列的图形 → 选择阵列类型(R/C/P) → 设置参数";
    }

    @Override
    protected void renderPreview(DrawContext context) {
        if (modifyStrategy instanceof ArrayWithSelectionStrategy arrayStrategy) {
            arrayStrategy.renderPreview(context);
        } else if (modifyStrategy instanceof ArrayStrategy oldArrayStrategy) {
            oldArrayStrategy.renderPreview(context);
        }
    }

    @Override
    public void updateConfig(String key, String value) {
        // 新的ArrayWithSelectionStrategy处理
        if (modifyStrategy instanceof ArrayWithSelectionStrategy arrayStrategy) {
            handleNewStrategyConfig(key, value, arrayStrategy);
            return;
        }
        
        // 旧的ArrayStrategy处理
        if (configManager != null) {
            boolean success = configManager.updateConfig(key, value);
            
            // 处理特殊的确认操作
            if (success && "confirm".equals(key)) {
                ArrayStrategy arrayStrategy = getArrayStrategyInternal();
                if (arrayStrategy != null) {
                    // 检查是否可以确认阵列
                    if (arrayStrategy.canConfirmArray()) {
                        var cmd = arrayStrategy.buildArrayCommand();
                        if (cmd != null) {
                            executeModifyCommand(cmd);
                        } else {
                            LOGGER.warn("阵列确认失败：命令为空（可能缺少基点或数量）");
                        }
                    } else {
                        LOGGER.warn("阵列确认失败：缺少必要条件（源图形、基准点或路径）");
                    }
                }
            }
            
            // 处理状态消息更新
            if (success) {
                updateStatusMessageForConfigChange(key);
            }
        } else {
            LOGGER.warn("配置管理器未初始化，无法更新配置: {}", key);
        }
    }
    
    /**
     * 处理新策略的配置更新
     */
    private void handleNewStrategyConfig(String key, String value, ArrayWithSelectionStrategy arrayStrategy) {
        switch (key) {
            case "arrayType" -> {
                ArrayWithSelectionStrategy.ArrayType newType = ArrayWithSelectionStrategy.ArrayType.valueOf(value);
                arrayStrategy.setArrayType(newType);
                LOGGER.debug("阵列类型已更新为: {}", newType.getDisplayName());
                updateStatusMessage("已切换到" + newType.getDisplayName() + "模式");
            }
            case "confirm" -> {
                if (canConfirmArray()) {
                    var cmd = arrayStrategy.buildArrayCommand();
                    if (cmd != null) {
                        executeModifyCommand(cmd);
                        arrayStrategy.reset();
                        LOGGER.debug("阵列操作已确认");
                    } else {
                        LOGGER.warn("阵列确认失败：命令为空");
                    }
                } else {
                    LOGGER.warn("阵列确认失败：条件不满足");
                }
            }
            default -> LOGGER.debug("新策略配置项: {} = {}", key, value);
        }
    }
    
    /**
     * 根据配置变更更新状态消息
     */
    private void updateStatusMessageForConfigChange(String key) {
        switch (key) {
            case "beginPickPath" -> updateStatusMessage("在画布上点击路径对象进行拾取");
            case "beginPickObjects" -> updateStatusMessage("在画布上点击要阵列的物件");
            case "arrayType" -> {
                ArrayStrategy.ArrayType type = getArrayType();
                updateStatusMessage(String.format("已切换到%s模式", type.getDisplayName()));
            }
            default -> {
                // 其他配置变更不需要特殊的状态消息更新
            }
        }
    }

    /**
     * 获取阵列策略
     * @return 阵列策略实例
     * @deprecated 此方法将在未来版本中移除，请使用公共API方法
     */
    @Deprecated
    public ArrayStrategy getArrayStrategy() {
        return getArrayStrategyInternal();
    }

    // ========== 公共API方法 - 彻底封装策略 ==========

    /**
     * 获取当前阵列类型
     * @return 当前阵列类型
     */
    public ArrayStrategy.ArrayType getArrayType() {
        ArrayStrategy strategy = getArrayStrategyInternal();
        return strategy != null ? strategy.getCurrentType() : ArrayStrategy.ArrayType.RECTANGULAR;
    }

    /**
     * 获取行数
     * @return 当前行数
     */
    public int getRowCount() {
        if (modifyStrategy instanceof ArrayWithSelectionStrategy s) return s.getRowCount();
        ArrayStrategy strategy = getArrayStrategyInternal();
        return strategy != null ? strategy.getRowCount() : 2;
    }

    /**
     * 获取列数
     * @return 当前列数
     */
    public int getColumnCount() {
        if (modifyStrategy instanceof ArrayWithSelectionStrategy s) return s.getColumnCount();
        ArrayStrategy strategy = getArrayStrategyInternal();
        return strategy != null ? strategy.getColumnCount() : 2;
    }

    /**
     * 获取间距
     * @return 当前间距
     */
    public double getSpacing() {
        ArrayStrategy strategy = getArrayStrategyInternal();
        return strategy != null ? strategy.getSpacing() : 50.0;
    }
    
    /** 获取行间距（矩形阵列） */
    public double getRowSpacing() {
        if (modifyStrategy instanceof ArrayWithSelectionStrategy s) return s.getRowSpacing();
        ArrayStrategy strategy = getArrayStrategyInternal();
        return strategy != null ? strategy.getRowSpacing() : getSpacing();
    }
    
    /** 获取列间距（矩形阵列） */
    public double getColumnSpacing() {
        if (modifyStrategy instanceof ArrayWithSelectionStrategy s) return s.getColumnSpacing();
        ArrayStrategy strategy = getArrayStrategyInternal();
        return strategy != null ? strategy.getSpacing() : 50.0;
    }

    /**
     * 获取半径
     * @return 当前半径
     */
    public double getRadius() {
        if (modifyStrategy instanceof ArrayWithSelectionStrategy s) return s.getRadius();
        ArrayStrategy strategy = getArrayStrategyInternal();
        return strategy != null ? strategy.getRadius() : 100.0;
    }

    /**
     * 获取角度步长
     * @return 当前角度步长
     */
    public double getAngle() {
        ArrayStrategy strategy = getArrayStrategyInternal();
        return strategy != null ? strategy.getAngle() : 45.0;
    }

    /**
     * 获取阵列策略（私有方法）
     * @return 阵列策略实例
     */
    private ArrayStrategy getArrayStrategyInternal() {
        if (modifyStrategy instanceof ArrayStrategy) {
            return (ArrayStrategy) modifyStrategy;
        }
        return null;
    }
    
    /**
     * 检查是否可以确认阵列
     * @return 是否可以确认
     */
    public boolean canConfirmArray() {
        if (modifyStrategy instanceof ArrayWithSelectionStrategy arrayStrategy) {
            // 新策略的确认逻辑：需要有选中的图形且在预览状态
            return arrayStrategy.getCurrentMode() == ArrayWithSelectionStrategy.StrategyMode.ARRAY &&
                   arrayStrategy.getArrayState() == ArrayWithSelectionStrategy.ArrayState.PREVIEWING;
        }
        
        ArrayStrategy strategy = getArrayStrategyInternal();
        return strategy != null && strategy.canConfirmArray();
    }

    /**
     * 获取修改策略
     * @return 当前的修改策略
     */
    public IModifyStrategy getModifyStrategy() {
        return modifyStrategy;
    }
}