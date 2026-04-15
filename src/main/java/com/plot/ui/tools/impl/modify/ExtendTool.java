package com.plot.ui.tools.impl.modify;

import com.plot.api.snap.ISnapManager;
import com.plot.api.state.IAppState;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.graphics.DrawContext;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import com.plot.ui.component.Icons;
import com.plot.ui.tools.impl.modify.strategy.ExtendWithSelectionStrategy;
import com.plot.ui.tools.impl.modify.strategy.IModifyStrategy;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 延伸工具 - 自动模式版本
 * 
 * <p>实现CAD风格的延伸工具，采用策略模式架构：</p>
 * <ul>
 *   <li><strong>自动延伸模式</strong>：自动检测最佳延伸方式，先尝试标准延伸，再尝试投影延伸</li>
 *   <li><strong>智能边界处理</strong>：如果边界太短，自动延长边界长度以完成延伸</li>
 *   <li><strong>精确方向判断</strong>：根据点击位置判断延伸哪一端</li>
 *   <li><strong>实时预览效果</strong>：显示延伸预览和方向指示</li>
 *   <li><strong>完全兼容撤销/重做系统</strong></li>
 * </ul>
 * 
 * <p><strong>CAD风格交互流程：</strong></p>
 * <ol>
 *   <li><strong>选择边界</strong>：左键点击选择边界图形（线、圆、圆弧等），可多选</li>
 *   <li><strong>确认边界</strong>：右键确认边界选择完成</li>
 *   <li><strong>选择目标</strong>：左键点击要延伸的图形端点，自动延伸</li>
 *   <li><strong>自动处理</strong>：工具自动选择最佳延伸方式</li>
 * </ol>
 * 
 * @author Plot Team
 * @version 5.0 - 自动模式版本
 */
public class ExtendTool extends ModifyTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtendTool.class);
    
    // 配置键常量
    public static final String CONFIG_KEY_TOLERANCE = "tolerance";
    public static final String CONFIG_KEY_ENDPOINT_TOLERANCE = "endpointTolerance";

    private final AtomicBoolean eventSubscribed = new AtomicBoolean(false);
    private final com.plot.infrastructure.event.EventListener toolConfigListener = this::handleToolConfigEvent;
    
    /**
     * 依赖注入构造函数（推荐）
     * 
     * @param appState 应用状态管理器
     * @param snapManager 吸附管理器
     */
    public ExtendTool(AppState appState, ISnapManager snapManager) {
        super("extend",
              "延伸",
              Icons.EXTEND_IDENTIFIER,
              "CAD风格延伸工具 - 延伸对象到边界图形",
              appState,
              snapManager);
        LOGGER.debug("ExtendTool 已创建（依赖注入模式）");
    }
    
    /**
     * 兼容性构造函数（已弃用）
     * 
     * @deprecated 请使用依赖注入构造函数 {@link #ExtendTool(AppState, ISnapManager)}
     */
    @Deprecated
    public ExtendTool() {
        super("extend",
              "延伸",
              Icons.EXTEND_IDENTIFIER,
              "CAD风格延伸工具");
        LOGGER.debug("ExtendTool 已创建（兼容性模式）");
    }
    
    @Override
    protected IModifyStrategy createStrategy() {
        LOGGER.debug("===== ExtendTool.createStrategy 被调用 =====");
        // 获取AppState并转换为具体类型
        com.plot.api.state.IAppState iAppState = getAppState();
        if (iAppState instanceof com.plot.core.state.AppState concreteAppState) {
            // 使用新的构造函数，将 AppState 注入
            ExtendWithSelectionStrategy strategy = new ExtendWithSelectionStrategy(concreteAppState);
            LOGGER.debug("ExtendWithSelectionStrategy 创建成功: {}", strategy.getClass().getSimpleName());
            return strategy;
        } else {
            // 计算实际类型名称，避免重复代码
            String errorMessage = getString(iAppState);

            // 记录详细错误信息
            LOGGER.error(errorMessage);
            
            // 回退到全局单例，但添加警告
            ExtendWithSelectionStrategy strategy = new ExtendWithSelectionStrategy(com.plot.core.state.AppState.getInstance());
            LOGGER.warn("使用全局单例回退策略创建 ExtendWithSelectionStrategy。建议修复依赖注入配置以避免使用单例模式。");
            return strategy;
        }
    }

    private static @NotNull String getString(IAppState iAppState) {
        String actualTypeName = iAppState != null ? iAppState.getClass().getSimpleName() : "null";

        // 改进的回退逻辑：提供更详细的错误信息，并建议解决方案
        return String.format(
                """
                        ExtendTool 无法创建策略：AppState 类型不匹配。
                        期望: %s
                        实际: %s
                        建议: 检查工具管理器的依赖注入配置，确保正确传递 AppState 实例。""",
            AppState.class.getSimpleName(),
            actualTypeName
        );
    }

    // onKeyDown方法已移除，使用父类ModifyTool的实现
    // 父类会正确调用策略的onKeyDown方法，策略中已经完整实现了Esc键的处理逻辑
    
    /**
     * 处理工具配置事件
     */
    private void handleToolConfigEvent(Object event) {
        if (event instanceof ToolConfigEvent configEvent) {
            if ("extend".equals(configEvent.getToolId())) {
                LOGGER.debug("ExtendTool 收到配置事件: {} = {}",
                    configEvent.getOptionName(), configEvent.getValue());
                updateConfig(configEvent.getOptionName(), String.valueOf(configEvent.getValue()));
            }
        }
    }
    
    @Override
    public void updateConfig(String key, String value) {
        if (value == null) {
            LOGGER.warn("配置值不能为空: {}", key);
            return;
        }
        
        // 支持新的ExtendWithSelectionStrategy
        if (modifyStrategy instanceof ExtendWithSelectionStrategy newExtendStrategy) {
            switch (key) {
                case "mode" -> // 模式配置已移除，现在使用自动模式
                        LOGGER.debug("延伸工具现在使用自动模式，忽略模式配置: {}", value);
                case CONFIG_KEY_TOLERANCE -> {
                    try {
                        double tolerance = Double.parseDouble(value);
                        newExtendStrategy.setExtendTolerance(tolerance);
                    } catch (NumberFormatException e) {
                        LOGGER.warn("无效的容差值: {}", value);
                    }
                }
                case CONFIG_KEY_ENDPOINT_TOLERANCE -> {
                    try {
                        double tolerance = Double.parseDouble(value);
                        newExtendStrategy.setEndpointTolerance(tolerance);
                    } catch (NumberFormatException e) {
                        LOGGER.warn("无效的端点容差值: {}", value);
                    }
                }
                default -> LOGGER.debug("未知的配置键: {}", key);
            }
        }
    }
    
    @Override
    public void onActivate() {
        super.onActivate();
        
        // 在激活时订阅事件 - 使用原子操作确保线程安全
        if (eventSubscribed.compareAndSet(false, true)) {
            EventBus.getInstance().subscribe(ToolConfigEvent.class, toolConfigListener);
            LOGGER.debug("ExtendTool 已订阅 ToolConfigEvent");
        } else {
            LOGGER.debug("ExtendTool 已订阅 ToolConfigEvent，跳过重复订阅");
        }
        
        LOGGER.debug("CAD风格延伸工具已激活");
        updateStatusMessage("请左键选择边界图形，右键确认选择，然后左键点击要延伸的图形端点");
    }

    @Override
    public void onDeactivate() {
        LOGGER.debug("CAD风格延伸工具已停用");
        
        // [核心修改] 提交任何待处理的批量命令，防止数据丢失
        if (modifyStrategy instanceof ExtendWithSelectionStrategy extendStrategy) {
            ModifyCommand pendingCommand = extendStrategy.getModifyCommand(); // 这会创建批量命令
            if (pendingCommand != null) {
                LOGGER.debug("工具停用时，提交 {} 个待处理的批量延伸操作", 
                    pendingCommand.getOldShapes().size());
                
                // 通过 ModifyToolContext 接口提交命令
                // 这确保了命令被正确执行并记录到历史中
                executeModifyCommand(pendingCommand);
            }
        }
        
        // 在停用时取消订阅事件 - 使用原子操作确保线程安全
        if (eventSubscribed.compareAndSet(true, false)) {
            EventBus.getInstance().unsubscribe(ToolConfigEvent.class, toolConfigListener);
            LOGGER.debug("ExtendTool 已取消订阅 ToolConfigEvent");
        } else {
            LOGGER.debug("ExtendTool 未订阅 ToolConfigEvent，跳过取消订阅");
        }
        
        super.onDeactivate();
    }
    
    /**
     * 获取当前工具的完整状态
     */
    public ExtendToolState getToolState() {
        // 获取当前策略
        IModifyStrategy currentStrategy = getStrategy();
        if (currentStrategy instanceof ExtendWithSelectionStrategy extendStrategy) {
            return new ExtendToolState(
                extendStrategy.getExtendState(),    // 直接调用，无需转换
                extendStrategy.getHighlightedShape(),
                extendStrategy.getPreviewShapes(),
                extendStrategy.getBoundaryShapes(),
                null, // 不再有单独的预览目标
                extendStrategy.getExtendPoint(),
                extendStrategy.getTargetPoint(),
                false, // 不再使用投影模式，现在使用自动模式
                extendStrategy.getExtendTolerance(),
                extendStrategy.getEndpointTolerance()
            );
        }
        return ExtendToolState.EMPTY;
    }

    /**
     * 获取当前延伸模式（已弃用，现在使用自动模式）
     * 
     * @return 当前延伸模式
     */
    @Deprecated
    public ExtendWithSelectionStrategy.ExtendMode getCurrentExtendMode() {
        // 现在使用自动模式，返回默认值以保持兼容性
        LOGGER.debug("延伸工具现在使用自动模式，返回默认模式");
        return ExtendWithSelectionStrategy.ExtendMode.STANDARD;
    }
    
    /**
     * 获取边界缓存状态信息
     * 
     * @return 边界缓存状态描述
     */
    public String getBoundaryCacheStatus() {
        IModifyStrategy currentStrategy = getStrategy();
        if (currentStrategy instanceof ExtendWithSelectionStrategy extendStrategy) {
            List<Shape> boundaries = extendStrategy.getBoundaryShapes();
            if (boundaries != null && !boundaries.isEmpty()) {
                return String.format("已缓存 %d 个边界图形", boundaries.size());
            }
        }
        return "未缓存边界图形";
    }

    // ====== 兼容性方法 - 委托给getToolState() ======
    
    /**
     * 获取高亮的图形
     * 
     * @return 当前高亮的图形，如果没有则返回null
     * @deprecated 请使用 {@link #getToolState()} 获取完整状态信息
     */
    @Deprecated
    public Shape getHighlightedShape() {
        return getToolState().highlightedShape();
    }
    
    /**
     * 获取预览图形列表
     * 
     * @return 预览图形列表，如果没有则返回null
     * @deprecated 请使用 {@link #getToolState()} 获取完整状态信息
     */
    @Deprecated
    public List<Shape> getPreviewShapes() {
        return getToolState().previewShapes();
    }
    
    /**
     * 获取已选择的边界图形列表
     * 
     * @return 已选择的边界图形列表，如果没有则返回空列表
     * @deprecated 请使用 {@link #getToolState()} 获取完整状态信息
     */
    @Deprecated
    public List<Shape> getSelectedBoundaries() {
        return getToolState().selectedBoundaries();
    }
    
    /**
     * 获取预览的延伸目标
     * 
     * @return 预览的延伸目标，如果没有则返回null
     * @deprecated 请使用 {@link #getToolState()} 获取完整状态信息
     */
    @Deprecated
    public Shape getPreviewTarget() {
        return getToolState().previewTarget();
    }
    
    /**
     * 获取预览的延伸点
     * 
     * @return 预览的延伸点，如果没有则返回null
     * @deprecated 请使用 {@link #getToolState()} 获取完整状态信息
     */
    @Deprecated
    public com.plot.api.geometry.Vec2d getPreviewExtendPoint() {
        return getToolState().previewExtendPoint();
    }
    
    /**
     * 获取预览的目标点
     * 
     * @return 预览的目标点，如果没有则返回null
     * @deprecated 请使用 {@link #getToolState()} 获取完整状态信息
     */
    @Deprecated
    public com.plot.api.geometry.Vec2d getPreviewTargetPoint() {
        return getToolState().previewTargetPoint();
    }
    
    /**
     * 预览是否为投影模式
     * 
     * @return true如果是投影模式，false否则
     * @deprecated 请使用 {@link #getToolState()} 获取完整状态信息
     */
    @Deprecated
    public boolean isPreviewProjectMode() {
        return getToolState().previewIsProjectMode();
    }
    
    /**
     * 获取延伸容差
     * 
     * @return 延伸容差
     * @deprecated 请使用 {@link #getToolState()} 获取完整状态信息
     */
    @Deprecated
    public double getExtendTolerance() {
        return getToolState().extendTolerance();
    }
    
    /**
     * 获取端点容差
     * 
     * @return 端点容差
     * @deprecated 请使用 {@link #getToolState()} 获取完整状态信息
     */
    @Deprecated
    public double getEndpointTolerance() {
        return getToolState().endpointTolerance();
    }
    
    @Override
    protected String getInitialStatusMessage() {
        return "请左键选择边界图形，右键确认选择，然后左键点击要延伸的图形端点";
    }
    
    @Override
    public String getDefaultCursor() {
        return "extend";
    }
    
    @Override
    protected void renderPreview(DrawContext context) {
        IModifyStrategy currentStrategy = getStrategy();
        if (currentStrategy instanceof ExtendWithSelectionStrategy extendStrategy) {
            extendStrategy.renderPreview(context);
        }
    }
    


    /**
     * 延伸工具状态信息 - 不可变数据传输对象
     * 
     * @param currentState 当前延伸状态
     * @param highlightedShape 高亮的图形
     * @param previewShapes 预览图形列表
     * @param selectedBoundaries 已选择的边界图形列表
     * @param previewTarget 预览的延伸目标
     * @param previewExtendPoint 预览的延伸点
     * @param previewTargetPoint 预览的目标点
     * @param previewIsProjectMode 预览是否为投影模式
     * @param extendTolerance 延伸容差
     * @param endpointTolerance 端点容差
     */
    public record ExtendToolState(
        ExtendWithSelectionStrategy.ExtendState currentState,
        Shape highlightedShape,
        List<Shape> previewShapes,
        List<Shape> selectedBoundaries,
        Shape previewTarget,
        com.plot.api.geometry.Vec2d previewExtendPoint,
        com.plot.api.geometry.Vec2d previewTargetPoint,
        boolean previewIsProjectMode,
        double extendTolerance,
        double endpointTolerance
    ) {
        /**
         * 空状态常量
         */
        public static final ExtendToolState EMPTY = new ExtendToolState(
            ExtendWithSelectionStrategy.ExtendState.SELECTING_BOUNDARY, // 使用有效的初始状态
            null,
            null,
            List.of(),
            null,
            null,
            null,
            false,
            5.0,
            3.0
        );
    }
}
