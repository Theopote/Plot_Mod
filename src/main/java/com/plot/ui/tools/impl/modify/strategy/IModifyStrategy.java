package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.api.tool.ToolState;
import com.plot.core.model.Shape;
import com.plot.core.command.commands.ModifyCommand;

import java.util.List;

/**
 * 修改工具策略接口
 * 
 * <p>定义了修改工具与用户交互的统一接口，支持多种修改模式：</p>
 * <ul>
 *   <li>选择模式 (SELECTION)：选择和管理图形</li>
 *   <li>变换模式 (TRANSFORM)：移动、旋转、缩放等变换</li>
 *   <li>编辑模式 (EDIT)：修剪、延伸、圆角等编辑</li>
 *   <li>操作模式 (OPERATION)：布尔运算、阵列等操作</li>
 * </ul>
 * 
 * <p>使用策略模式，让ModifyTool可以动态切换不同的修改行为，
 * 避免了大量的if-else判断，提高了代码的可扩展性和可维护性。</p>
 * 
 * <p><strong>与绘制工具策略的差异：</strong></p>
 * <ul>
 *   <li>操作对象：绘制工具创建新图形，修改工具操作现有图形</li>
 *   <li>状态依赖：修改工具通常依赖选择状态</li>
 *   <li>命令模式：修改工具使用命令模式支持撤销/重做</li>
 *   <li>预览机制：修改工具需要实时预览变更效果</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 1.0 - 修改工具策略模式
 */
public interface IModifyStrategy {
    
    /**
     * 修改策略结果枚举
     * 策略通过返回这些结果来告知上下文应该如何响应
     */
    enum ModifyResult {
        /** 修改继续，需要更多输入 */
        CONTINUE("继续", "修改正在进行中，等待更多用户输入"),
        
        /** 修改完成，可以执行修改命令 */
        COMPLETE("完成", "修改已完成，可以执行最终修改命令"),
        
        /** 修改取消，需要重置状态 */
        CANCEL("取消", "修改被取消，需要重置到初始状态"),
        
        /** 事件被忽略，不需要处理 */
        IGNORED("忽略", "当前事件不适用于此策略状态"),
        
        /** 需要选择图形 */
        NEED_SELECTION("需要选择", "需要先选择图形才能进行修改");
        
        private final String displayName;
        private final String description;
        
        ModifyResult(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        
        @Override
        public String toString() {
            return displayName + ": " + description;
        }
    }
    
    /**
     * 处理鼠标按下事件
     * @param pos 鼠标位置
     * @param button 鼠标按键
     * @param context 修改工具上下文，策略可以回调其公共方法
     * @return 修改结果，告知上下文如何响应
     */
    ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context);
    
    /**
     * 处理鼠标移动事件
     * @param pos 鼠标位置
     * @param context 修改工具上下文，策略可以回调其公共方法
     * @return 修改结果，告知上下文如何响应
     */
    ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context);
    
    /**
     * 处理鼠标释放事件
     * @param pos 鼠标位置
     * @param button 鼠标按键
     * @param context 修改工具上下文，策略可以回调其公共方法
     * @return 修改结果，告知上下文如何响应
     */
    ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context);
    
    /**
     * 处理键盘按下事件
     * @param keyCode 按键代码
     * @param context 修改工具上下文
     * @return 修改结果，告知上下文如何响应
     */
    default ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        return ModifyResult.IGNORED;
    }
    
    /**
     * 处理键盘释放事件
     * @param keyCode 按键代码
     * @param context 修改工具上下文
     * @return 修改结果，告知上下文如何响应
     */
    default ModifyResult onKeyUp(int keyCode, ModifyToolContext context) {
        return ModifyResult.IGNORED;
    }
    
    /**
     * 处理鼠标滚轮事件
     * @param pos 鼠标位置
     * @param delta 滚轮增量（正值向上滚动，负值向下滚动）
     * @param context 修改工具上下文
     * @return 修改结果，告知上下文如何响应
     */
    default ModifyResult onMouseWheel(Vec2d pos, double delta, ModifyToolContext context) {
        return ModifyResult.IGNORED;
    }
    
    /**
     * 获取最终修改命令
     * 只有在修改完成后才应该调用此方法
     * @return 修改命令，如果修改未完成或创建失败则返回null
     */
    ModifyCommand getModifyCommand();
    
    /**
     * 重置修改状态
     * 由上下文调用，策略不应该自己调用上下文的重置方法
     */
    void reset();
    
    /**
     * 渲染修改预览（DrawContext版本）
     * @param context 绘制上下文
     */
    default void renderPreview(com.plot.core.graphics.DrawContext context) {
        // 默认实现为空，子类可以重写
    }
    
    /**
     * 渲染修改预览（ImGui版本）
     * @param drawList ImGui绘制列表
     * @param camera 画布相机
     */
    default void renderPreview(imgui.ImDrawList drawList, com.plot.ui.canvas.CanvasCamera camera) {
        // 默认实现为空，子类可以重写
    }
    
    /**
     * 获取修改策略的名称
     * @return 策略名称
     */
    String getStrategyName();
    
    /**
     * 获取修改策略的描述
     * @return 策略描述
     */
    String getStrategyDescription();
    
    /**
     * 检查策略是否需要选中的图形
     * @return true如果需要选中图形，false否则
     */
    default boolean requiresSelection() {
        return true;
    }
    
    /**
     * 获取策略支持的最小选择数量
     * @return 最小选择数量，0表示不需要选择
     */
    default int getMinimumSelectionCount() {
        return requiresSelection() ? 1 : 0;
    }
    
    /**
     * 获取策略支持的最大选择数量
     * @return 最大选择数量，-1表示无限制
     */
    default int getMaximumSelectionCount() {
        return -1;
    }
    
    /**
     * 更新策略配置
     * @param key 配置键
     * @param value 配置值
     */
    default void updateConfig(String key, Object value) {
        // 默认实现为空，子类可以重写
    }
    
    /**
     * 获取策略状态消息
     * @return 当前状态消息
     */
    default String getStatusMessage() {
        return "策略状态未知";
    }
    
    /**
     * 获取策略参数
     * @param key 参数键
     * @return 参数值，如果不存在则返回null
     */
    default Object getParameter(String key) {
        return null;
    }
    
    /**
     * 修改工具上下文接口
     * 定义策略可以回调的上下文方法
     */
    interface ModifyToolContext {
        /**
         * 执行修改命令（原子操作：执行并记录历史）
         * 这是一个完整的修改流程，包括命令执行、历史记录和状态重置
         * @param command 要执行的修改命令
         */
        void executeModifyCommand(ModifyCommand command);
        
        /**
         * 重置修改状态
         * @param reason 重置原因
         */
        void resetModification(String reason);
        
        /**
         * 设置预览状态
         * @param enabled 是否启用预览
         */
        void setPreviewEnabled(boolean enabled);
        
        /**
         * 设置工具状态
         * @param state 工具状态
         */
        void setToolState(ToolState state);
        
        /**
         * 设置修改工具内部状态
         * @param state 修改工具内部状态
         */
        default void setModifyToolState(Object state) {
            // 默认实现为空，子类可以重写
        }
        
        /**
         * 设置状态消息
         * @param message 状态消息
         */
        void setStatusMessage(String message);
        
        /**
         * 获取选中的图形列表
         * @return 选中的图形列表
         */
        List<Shape> getSelectedShapes();
        
        /**
         * 设置选中的图形列表
         * @param shapes 要选中的图形列表
         */
        void setSelectedShapes(List<Shape> shapes);
        
        /**
         * 添加选中的图形
         * @param shape 要添加的图形
         */
        void addSelectedShape(Shape shape);
        
        /**
         * 移除选中的图形
         * @param shape 要移除的图形
         */
        void removeSelectedShape(Shape shape);
        
        /**
         * 清除所有选中的图形
         */
        void clearSelection();
        
        // ===== 按键状态查询方法（解耦UI框架依赖） =====
        
        /**
         * 检查Shift键是否按下
         * @return true如果Shift键被按下
         */
        boolean isShiftKeyDown();
        
        /**
         * 检查Alt键是否按下
         * @return true如果Alt键被按下
         */
        boolean isAltKeyDown();
        
        /**
         * 检查Ctrl键是否按下
         * @return true如果Ctrl键被按下
         */
        boolean isCtrlKeyDown();
        
        /**
         * 在指定位置查找图形
         * @param pos 位置
         * @param tolerance 容差
         * @return 找到的图形，没有则返回null
         */
        Shape findShapeAt(Vec2d pos, double tolerance);
        
        /**
         * 在指定区域查找图形列表
         * @param startPos 起始位置
         * @param endPos 结束位置
         * @return 找到的图形列表
         */
        List<Shape> findShapesInArea(Vec2d startPos, Vec2d endPos);
        
        /**
         * 获取吸附处理器
         * @return 吸附处理器
         */
        com.plot.ui.tools.impl.drawing.helper.ISnapHandler getSnapHandler();
        
        /**
         * 获取样式处理器
         * @return 样式处理器
         */
        com.plot.ui.tools.impl.drawing.helper.IStyleHandler getStyleHandler();
        
        /**
         * 获取相机
         * @return 相机对象
         */
        com.plot.ui.canvas.CanvasCamera getCamera();
        
        /**
         * 获取画布
         * @return 画布对象
         */
        com.plot.api.model.ICanvas getCanvas();
        
        /**
         * 获取应用状态
         * @return 应用状态对象
         */
        com.plot.api.state.IAppState getAppState();
        
        /**
         * 获取控制点编辑工具
         * @return 控制点编辑工具，如果没有则返回null
         */
        com.plot.ui.tools.impl.modify.ControlPointEditTool getControlPointEditTool();
        
        /**
         * 获取活动图层中的所有图形
         * @return 活动图层中的所有图形列表
         */
        List<Shape> getAllShapesInActiveLayer();
        
        /**
         * 获取指定图层中的所有图形
         * @param layerId 图层ID
         * @return 指定图层中的所有图形列表
         */
        List<Shape> getAllShapesInLayer(String layerId);
    }
} 