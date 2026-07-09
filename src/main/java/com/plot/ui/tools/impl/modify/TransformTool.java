package com.plot.ui.tools.impl.modify;

import com.plot.api.snap.ISnapManager;
import com.plot.api.state.IAppState;
import com.plot.core.shortcut.ShortcutManager;
import com.plot.ui.component.Icons;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy;
import com.plot.core.graphics.DrawContext;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.EventListener;
import com.plot.infrastructure.event.base.Event;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * 变换工具 - 专业变换工具
 * 
 * <p>这是一个专业的图形变换工具，提供完整的缩放、旋转和变形功能。
 * 采用策略模式架构，支持多种变换模式和精确的数值输入。</p>
 * 
 * <p><strong>主要特性：</strong></p>
 * <ul>
 *   <li><strong>缩放变换</strong>：支持自由缩放、等比缩放、单向缩放</li>
 *   <li><strong>中心缩放</strong>：按住Alt键从中心点缩放</li>
 *   <li><strong>旋转功能</strong>：角点外侧拖拽进行旋转</li>
 *   <li><strong>数值输入</strong>：精确的缩放比例和尺寸输入</li>
 *   <li><strong>多选支持</strong>：同时变换多个图形</li>
 *   <li><strong>预览功能</strong>：实时预览变换效果</li>
 * </ul>
 * 
 * <p><strong>使用方式：</strong></p>
 * <ol>
 *   <li>选择要变换的图形</li>
 *   <li>右键进入变换模式</li>
 *   <li>拖拽控制点进行缩放</li>
 *   <li>按住Alt键从中心缩放</li>
 *   <li>角点外侧拖拽进行旋转</li>
 * </ol>
 * 
 * @author Plot Team
 * @version 3.0 - 专业变换工具
 */
public class TransformTool extends ModifyTool implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransformTool.class);
    
    // 配置键常量
    public static final String CONFIG_KEY_MODE = "mode";
    public static final String CONFIG_KEY_CONSTRAINTS = "constraints";
    public static final String CONFIG_KEY_CENTER_SCALE = "centerScale";
    public static final String CONFIG_KEY_ROTATION = "rotation";
    public static final String CONFIG_KEY_NUMERIC_INPUT = "numericInput";
    
    // 数值输入配置键
    public static final String CONFIG_KEY_SCALE_X = "scale_x";
    public static final String CONFIG_KEY_SCALE_Y = "scale_y";
    public static final String CONFIG_KEY_MOVE_X = "move_x";
    public static final String CONFIG_KEY_MOVE_Y = "move_y";
    
    // 精度设置配置键
    public static final String CONFIG_KEY_STEP_SIZE = "step_size";
    public static final String CONFIG_KEY_SNAP_TO_GRID = "snap_to_grid";
    public static final String CONFIG_KEY_MAINTAIN_ASPECT_RATIO = "maintain_aspect_ratio";
    
    // 高级选项配置键
    public static final String CONFIG_KEY_SHOW_TRANSFORM_CENTER = "show_transform_center";
    public static final String CONFIG_KEY_SHOW_REFERENCE_POINTS = "show_reference_points";
    
    // 模式常量
    public static final String MODE_FREE = "FREE";
    public static final String MODE_HORIZONTAL = "HORIZONTAL";
    public static final String MODE_VERTICAL = "VERTICAL";
    
    // 默认模式
    public static final String DEFAULT_MODE = MODE_FREE;
    
    // 策略实例引用（用于直接更新）
    private com.plot.ui.tools.impl.modify.strategy.TransformWithSelectionStrategy transformStrategy;
    
    // 工具配置
    private String currentMode = DEFAULT_MODE;
    private boolean centerScaleEnabled = false;
    private boolean rotationEnabled = true;
    private boolean numericInputEnabled = true;
    
    /**
     * 依赖注入构造函数
     * 
     * @param appState 应用状态管理器
     * @param snapManager 吸附管理器
     * @param eventBus 事件总线
     */
    public TransformTool(IAppState appState, ISnapManager snapManager, EventBus eventBus) {
        super("transform", "变换工具", Icons.STRETCH_IDENTIFIER, "变换选中的图形",
                appState, snapManager, eventBus, ShortcutManager.getInstance());

        eventBus.subscribe(ToolConfigEvent.class, this);
        
        LOGGER.info("变换工具已初始化，支持中心缩放、旋转和数值输入");
    }
    
    /**
     * 创建变换策略
     */
    @Override
    protected IModifyStrategy createStrategy() {
        // 创建变换策略所需的依赖
        com.plot.ui.tools.impl.modify.helper.TransformHandler transformHandler = 
            new com.plot.ui.tools.impl.modify.helper.TransformHandler(concreteAppState);
        com.plot.ui.tools.impl.modify.helper.BoundingBoxControlManager controlManager = 
            new com.plot.ui.tools.impl.modify.helper.BoundingBoxControlManager();
        
        // 创建变换策略
        transformStrategy = new com.plot.ui.tools.impl.modify.strategy.TransformWithSelectionStrategy(
            transformHandler, controlManager, eventBus);
        transformStrategy.setRotationEnabled(rotationEnabled);
        return transformStrategy;
    }
    
    /**
     * 处理工具配置事件
     */
    @Override
    public void onEvent(Event event) {
        // 事件类型过滤：只处理ToolConfigEvent
        if (!(event instanceof ToolConfigEvent configEvent)) {
            return;
        }
        
        // 工具ID过滤：只处理变换工具的事件
        if (!"transform".equals(configEvent.getToolId())) {
            return;
        }
        
        String optionName = configEvent.getOptionName();
        Object value = configEvent.getValue();
        String stringValue = value != null ? value.toString() : "";
        
        LOGGER.debug("收到变换工具配置事件: {} = {}", optionName, stringValue);
        
        // 更新配置
        updateConfig(optionName, stringValue);
        
        // 同步更新策略实例
        syncStrategyInstance(optionName, stringValue);
    }
    
    /**
     * 更新工具配置
     */
    public void updateConfig(String optionName, String value) {
        switch (optionName) {
            case CONFIG_KEY_MODE -> {
                if (Set.of(MODE_FREE, MODE_HORIZONTAL, MODE_VERTICAL).contains(value)) {
                    currentMode = value;
                    LOGGER.debug("变换模式已更新: {}", value);
                } else {
                    LOGGER.warn("无效的变换模式: {}", value);
                }
            }
            case CONFIG_KEY_CONSTRAINTS -> // 约束配置处理
                    LOGGER.debug("约束配置已更新: {}", value);
            case CONFIG_KEY_CENTER_SCALE -> {
                centerScaleEnabled = Boolean.parseBoolean(value);
                LOGGER.debug("中心缩放已{}: {}", centerScaleEnabled ? "启用" : "禁用", value);
            }
            case CONFIG_KEY_ROTATION -> {
                rotationEnabled = Boolean.parseBoolean(value);
                LOGGER.debug("旋转功能已{}: {}", rotationEnabled ? "启用" : "禁用", value);
            }
            case CONFIG_KEY_NUMERIC_INPUT -> {
                numericInputEnabled = Boolean.parseBoolean(value);
                LOGGER.debug("数值输入已{}: {}", numericInputEnabled ? "启用" : "禁用", value);
            }
            // 数值输入配置
            case CONFIG_KEY_SCALE_X, CONFIG_KEY_SCALE_Y, 
                 CONFIG_KEY_MOVE_X, CONFIG_KEY_MOVE_Y, CONFIG_KEY_STEP_SIZE -> LOGGER.debug("数值输入配置已更新: {} = {}", optionName, value);
            // 精度设置配置
            case CONFIG_KEY_SNAP_TO_GRID, CONFIG_KEY_MAINTAIN_ASPECT_RATIO -> LOGGER.debug("精度设置配置已更新: {} = {}", optionName, value);
            // 高级选项配置
            case CONFIG_KEY_SHOW_TRANSFORM_CENTER, CONFIG_KEY_SHOW_REFERENCE_POINTS -> LOGGER.debug("高级选项配置已更新: {} = {}", optionName, value);
            default -> LOGGER.debug("未知的配置选项: {}", optionName);
        }
    }
    
    /**
     * 同步更新策略实例
     */
    private void syncStrategyInstance(String optionName, String value) {
        if (transformStrategy == null) {
            LOGGER.warn("策略实例为空，无法同步更新");
            return;
        }
        
        try {
            switch (optionName) {
                case CONFIG_KEY_MODE -> {
                    // 更新变换模式
                    com.plot.ui.tools.impl.modify.enums.TransformMode mode = 
                        com.plot.ui.tools.impl.modify.enums.TransformMode.fromValue(value);
                    transformStrategy.setTransformMode(mode);
                    LOGGER.debug("已同步更新策略实例的变换模式: {}", value);
                }
                case CONFIG_KEY_CONSTRAINTS -> {
                    boolean constraintsEnabled = Boolean.parseBoolean(value);
                    // 注意：TransformWithSelectionStrategy 目前没有 setConstraintsEnabled 方法
                    // 如果需要实现约束功能，可以在 TransformWithSelectionStrategy 中添加相应方法
                    LOGGER.debug("约束配置已更新: {}", constraintsEnabled);
                }
                case CONFIG_KEY_ROTATION -> {
                    boolean enabled = Boolean.parseBoolean(value);
                    transformStrategy.setRotationEnabled(enabled);
                    LOGGER.debug("已同步更新策略实例的旋转图示开关: {}", enabled);
                }
                default -> LOGGER.debug("未知的配置选项: {}", optionName);
            }
        } catch (Exception e) {
            LOGGER.error("同步策略实例失败: {} = {}", optionName, value, e);
        }
    }
    
    /**
     * 获取当前变换模式
     */
    public String getMode() {
        return currentMode;
    }
    
    /**
     * 获取工具描述
     */
    @Override
    public String getDescription() {
        return "专业的图形变换工具，支持缩放、旋转和精确数值输入";
    }

    /**
     * 检查是否支持旋转
     */
    public boolean isRotationEnabled() {
        return rotationEnabled;
    }

    /**
     * 设置旋转功能
     */
    public void setRotationEnabled(boolean enabled) {
        this.rotationEnabled = enabled;
        if (transformStrategy != null) {
            transformStrategy.setRotationEnabled(enabled);
        }
        LOGGER.debug("旋转功能已{}", enabled ? "启用" : "禁用");
    }

    /**
     * 工具激活时的处理
     */
    @Override
    public void onActivate() {
        super.onActivate();
        LOGGER.info("变换工具已激活，支持专业变换功能");
    }
    
    /**
     * 工具停用时的处理
     */
    @Override
    public void onDeactivate() {
        super.onDeactivate();
        // 切换工具时，清除变换框和预览状态
        if (transformStrategy != null) {
            transformStrategy.reset();
        }
        LOGGER.info("变换工具已停用，已清除变换框");
    }
    
    /**
     * 重写执行修改命令方法，对于变换工具，如果当前在变换模式，不清除选择
     */
    @Override
    public void executeModifyCommand(com.plot.core.command.commands.ModifyCommand command) {
        if (command != null) {
            try {
                concreteAppState.getCommandHistory().execute(command);
                LOGGER.debug("TransformTool 执行修改命令: {}", command.getClass().getSimpleName());
                
                // 强制同步清理新旧图形的视觉状态：不选中、不高亮
                try {
                    List<com.plot.core.model.Shape> oldShapes = command.getOldShapes();
                    if (oldShapes != null) {
                        for (com.plot.core.model.Shape s : oldShapes) {
                            try { s.setSelected(false); s.setHighlighted(false); } catch (Exception ignored) {}
                        }
                    }
                    List<com.plot.core.model.Shape> newShapes = command.getNewShapes();
                    if (newShapes != null) {
                        for (com.plot.core.model.Shape s : newShapes) {
                            try { s.setSelected(false); s.setHighlighted(false); } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("清理图形视觉状态时发生异常: {}", e.getMessage());
                }

                // 检查当前是否在变换模式
                // 通过检查策略是否有选中的图形来判断是否在变换模式
                boolean isInTransformMode = false;
                if (transformStrategy != null) {
                    try {
                        Object currentMode = transformStrategy.getCurrentMode();
                        // 使用字符串比较来检查模式
                        if (currentMode != null && currentMode.toString().contains("TRANSFORMING")) {
                            isInTransformMode = true;
                            
                            // 在变换模式下，更新选中图形ID列表，将旧图形ID替换为新图形ID
                            // 这样变换框才能正确更新到新图形的位置
                            List<com.plot.core.model.Shape> oldShapes = command.getOldShapes();
                            List<com.plot.core.model.Shape> newShapes = command.getNewShapes();
                            
                            if (oldShapes != null && newShapes != null && oldShapes.size() == newShapes.size()) {
                                // 更新策略中的选中图形ID列表
                                transformStrategy.updateSelectedShapeIdsAfterTransform(oldShapes, newShapes);
                                LOGGER.debug("变换工具：已更新选中图形ID列表，旧图形数量: {}, 新图形数量: {}", 
                                    oldShapes.size(), newShapes.size());
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.debug("检查变换模式时发生异常: {}", e.getMessage());
                    }
                }

                // 如果不在变换模式，清除选择（保持原有行为）
                if (!isInTransformMode) {
                    try {
                        clearSelection();
                    } catch (Exception e) {
                        LOGGER.debug("清空选择时发生异常: {}", e.getMessage());
                    }
                } else {
                    LOGGER.debug("变换工具在变换模式，保持选择状态以显示变换框");
                }

                // 命令执行后重置状态（但不清除变换框）
                resetModification("命令执行完成");
                
            } catch (Exception e) {
                LOGGER.error("TransformTool 执行修改命令失败: {}", e.getMessage(), e);
                resetModification("命令执行失败");
            }
        }
    }
    
    /**
     * 重写重置修改状态方法，对于变换工具，如果当前在变换模式，不清除变换框
     */
    @Override
    public void resetModification(String reason) {
        LOGGER.debug("TransformTool 重置修改状态: {}", reason);
        
        // 检查当前是否在变换模式
        // 通过检查策略是否有选中的图形来判断是否在变换模式
        if (transformStrategy != null) {
            List<com.plot.core.model.Shape> selectedShapes = transformStrategy.getSelectedShapes();
            // 如果有选中的图形，说明可能在变换模式（变换模式需要选中图形）
            // 更准确的方法是检查策略内部状态，但由于InteractionMode是私有的，我们使用间接方法
            // 如果策略有选中的图形且不在选择模式，则可能在变换模式
            if (selectedShapes != null && !selectedShapes.isEmpty()) {
                // 检查策略是否处于变换状态（通过检查是否有变换框）
                // 这里我们假设如果有选中的图形，且策略没有被重置，则可能在变换模式
                // 为了更准确，我们添加一个标志来跟踪是否在变换模式
                LOGGER.debug("变换工具有选中的图形，可能处于变换模式，保持变换框显示");
                setModifyToolState(ToolState.IDLE);
                setPreviewEnabled(false);
                // 不清除策略状态，保持变换框显示
                return;
            }
        }
        
        // 如果不在变换模式，使用父类的默认行为
        super.resetModification(reason);
    }
    
    /**
     * 获取初始状态消息
     */
    @Override
    public String getInitialStatusMessage() {
        return "选择要变换的图形，右键开始变换";
    }
    
    /**
     * 渲染预览
     */
    @Override
    public void renderPreview(DrawContext context) {
        if (transformStrategy != null) {
            transformStrategy.renderPreview(context);
        }
    }
    
    /**
     * 清理资源
     */
    @Override
    public void dispose() {
        if (eventBus != null) {
            eventBus.unsubscribe(ToolConfigEvent.class, this);
        }
        super.dispose();
        LOGGER.debug("变换工具资源已清理");
    }
}
