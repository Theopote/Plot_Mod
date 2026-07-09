package com.plot.ui.tools.impl.drawing.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.ui.tools.impl.drawing.DrawingTool;
import com.plot.utils.PlotI18n;

import java.util.List;

/**
 * 绘图工具交互策略接口
 * 
 * <p>定义了绘图工具与用户交互的统一接口，支持多种交互模式：</p>
 * <ul>
 *   <li>拖放模式 (DRAG_AND_DROP)：按下-拖动-释放</li>
 *   <li>多步骤模式 (MULTI_STEP)：点击-移动-点击</li>
 * </ul>
 * 
 * <p>使用策略模式，让DrawingTool可以动态切换不同的交互行为，
 * 避免了大量的if-else判断，提高了代码的可扩展性和可维护性。</p>
 * 
 * <p><strong>纯粹策略模式设计：</strong></p>
 * <ul>
 *   <li>策略接收上下文(DrawingTool)作为参数，可以回调上下文的公共方法</li>
 *   <li>所有交互逻辑封装在策略内部，上下文只负责委托</li>
 *   <li>策略完全控制自己的状态和生命周期</li>
 *   <li>通过返回值明确告知上下文交互状态，避免状态同步问题</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 3.0 - 统一状态管理
 */
public interface IInteractionStrategy {
    
    /**
     * 交互结果枚举
     * 策略通过返回这些结果来告知上下文应该如何响应
     */
    enum InteractionResult {
        /** 交互继续，需要更多输入 */
        CONTINUE("mode.plot.interaction.continue", "mode.plot.interaction.continue.desc"),
        
        /** 交互完成，可以提交图形 */
        COMPLETE("mode.plot.interaction.complete", "mode.plot.interaction.complete.desc"),
        
        /** 交互取消，需要重置状态 */
        CANCEL("mode.plot.interaction.cancel", "mode.plot.interaction.cancel.desc"),
        
        /** 事件被忽略，不需要处理 */
        IGNORED("mode.plot.interaction.ignored", "mode.plot.interaction.ignored.desc");
        
        private final String nameKey;
        private final String descKey;
        
        InteractionResult(String nameKey, String descKey) {
            this.nameKey = nameKey;
            this.descKey = descKey;
        }
        
        public String getDisplayName() { return PlotI18n.modeLabel(nameKey); }
        public String getDescription() { return PlotI18n.modeLabel(descKey); }
        
        @Override
        public String toString() {
            return getDisplayName() + ": " + getDescription();
        }
    }
    
    /**
     * 处理鼠标按下事件
     * @param pos 鼠标位置
     * @param button 鼠标按键
     * @param context 绘图工具上下文，策略可以回调其公共方法
     * @return 交互结果，告知上下文如何响应
     */
    InteractionResult onMouseDown(Vec2d pos, int button, DrawingToolContext context);
    
    /**
     * 处理鼠标移动事件
     * @param pos 鼠标位置
     * @param context 绘图工具上下文，策略可以回调其公共方法
     * @return 交互结果，告知上下文如何响应
     */
    InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context);
    
    /**
     * 处理鼠标释放事件
     * @param pos 鼠标位置
     * @param button 鼠标按键
     * @param context 绘图工具上下文，策略可以回调其公共方法
     * @return 交互结果，告知上下文如何响应
     */
    InteractionResult onMouseUp(Vec2d pos, int button, DrawingToolContext context);
    
    /**
     * 获取最终图形
     * 只有在交互完成后才应该调用此方法
     * @return 最终图形，如果交互未完成或创建失败则返回null
     */
    com.plot.core.model.Shape getFinalShape();
    
    /**
     * 重置交互状态
     * 由上下文调用，策略不应该自己调用上下文的重置方法
     */
    void reset();
    
    /**
     * 获取交互策略的名称
     * @return 策略名称
     */
    String getStrategyName();
    
    /**
     * 获取交互策略的描述
     * @return 策略描述
     */
    String getStrategyDescription();
    
    /**
     * 获取控制点列表
     * @return 当前交互的控制点列表
     */
    List<Vec2d> getControlPoints();
    
    /**
     * 获取当前鼠标位置
     * @return 当前鼠标位置，如果没有则返回null
     */
    Vec2d getCurrentMousePoint();
    
    /**
     * 绘图工具上下文接口
     * 定义策略可以回调的上下文方法
     */
    interface DrawingToolContext {
        /**
         * 提交图形（原子操作：提交并重置状态）
         * 这是一个完整的提交流程，包括样式应用、图形添加和状态重置
         * @param shape 要提交的图形
         */
        void commitShape(com.plot.core.model.Shape shape);
        
        /**
         * 重置绘制状态
         * @param reason 重置原因
         */
        void resetDrawing(String reason);
        
        /**
         * 设置预览图形
         * @param shape 预览图形
         */
        void setPreviewShape(com.plot.core.model.Shape shape);
        
        /**
         * 设置工具状态
         * 直接使用DrawingTool.ToolState，避免类型转换
         * @param state 工具状态
         */
        void setToolState(DrawingTool.ToolState state);
        
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
         * 获取图形工厂
         * @return 图形工厂
         */
        IShapeFactory getShapeFactory();
        
        /**
         * 获取相机
         * @return 相机对象
         */
        com.plot.ui.canvas.CanvasCamera getCamera();
    }
} 