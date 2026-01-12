package com.masterplanner.ui.tools.impl.drawing.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.tools.impl.drawing.strategy.IInteractionStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * 绘制模式处理器接口
 * 
 * 该接口定义了多段线工具中每种绘制模式的标准行为。
 * 通过将不同模式的逻辑分离到独立的处理器中，实现了更好的代码组织和可维护性。
 * 
 * 支持的绘制模式：
 * - 折线模式：直线段连接的多点绘制
 * - 钢笔模式：支持控制点的贝塞尔曲线绘制
 * - 编辑模式：对已存在图形的节点和控制点编辑
 */
public interface IModeHandler {
    
    /**
     * 处理鼠标按下事件
     * 
     * @param pos 鼠标位置（世界坐标）
     * @param button 鼠标按键（0=左键，1=右键）
     * @param context 绘制工具上下文
     * @return 交互结果
     */
    IInteractionStrategy.InteractionResult onMouseDown(Vec2d pos, int button, DrawingToolContext context);
    
    /**
     * 处理鼠标移动事件
     * 
     * @param pos 鼠标位置（世界坐标）
     * @param context 绘制工具上下文
     * @return 交互结果
     */
    IInteractionStrategy.InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context);
    
    /**
     * 处理鼠标释放事件
     * 
     * @param pos 鼠标位置（世界坐标）
     * @param button 鼠标按键（0=左键，1=右键）
     * @param context 绘制工具上下文
     * @return 交互结果
     */
    IInteractionStrategy.InteractionResult onMouseUp(Vec2d pos, int button, DrawingToolContext context);
    
    /**
     * 渲染当前模式的预览
     * 
     * @param adapter 绘制适配器，用于抽象不同的渲染后端
     */
    void renderPreview(DrawingAdapter adapter);
    
    /**
     * 生成最终的图形对象
     * 
     * @return 完成的图形，如果没有有效图形则返回 null
     */
    Shape getFinalShape();
    
    /**
     * 重置当前模式的所有状态
     * 
     * 此方法会清除所有临时数据，将模式恢复到初始状态，
     * 通常在切换模式或取消当前操作时调用。
     */
    void reset();
    
    /**
     * 获取当前模式的状态提示消息
     * 
     * @return 向用户显示的操作提示文本
     */
    String getStatusMessage();
    
    /**
     * 检查当前模式是否正在进行绘制
     * 
     * @return 如果正在绘制则返回 true，否则返回 false
     */
    boolean isDrawing();
    
    /**
     * 处理键盘按键事件
     * 
     * @param key 按键代码
     * @param context 绘制工具上下文
     * @return 交互结果，告知上层工具如何响应
     */
    default IInteractionStrategy.InteractionResult onKeyDown(int key, DrawingToolContext context) {
        return IInteractionStrategy.InteractionResult.IGNORED;
    }
    
    /**
     * 获取控制点列表
     * 
     * @return 当前交互的控制点列表
     */
    default List<Vec2d> getControlPoints() {
        return new ArrayList<>();
    }
    
    /**
     * 获取当前鼠标位置
     * 
     * @return 当前鼠标位置，如果没有则返回null
     */
    default Vec2d getCurrentMousePoint() {
        return null;
    }
    
    /**
     * 绘制工具上下文接口
     * 
     * 提供模式处理器需要的基本服务和工具
     */
    interface DrawingToolContext {
        /**
         * 获取样式处理器
         */
        StyleHandler getStyleHandler();
        
        /**
         * 获取吸附处理器
         */
        SnapHandler getSnapHandler();
        
        /**
         * 更新状态消息
         */
        void updateStatusMessage(String message);
        
        /**
         * 重置绘制状态
         */
        void resetDrawing(String reason);
        
        /**
         * 设置工具状态
         */
        void setToolState(Object state);
        
        /**
         * 获取相机对象
         */
        Object getCamera();
        
        /**
         * 提交图形到画布
         */
        void commitShape(Shape shape);
        
        /**
         * 获取多重绘制模式
         */
        boolean isMultipleMode();
    }
    
    /**
     * 绘制适配器接口
     * 
     * 抽象不同的渲染后端（DrawContext vs ImGui）
     */
    interface DrawingAdapter {
        void drawLine(Vec2d p1, Vec2d p2, java.awt.Color color, float thickness);
        void drawCircle(Vec2d center, double radius, java.awt.Color color, boolean filled);
        void drawBezierCurve(Vec2d p0, Vec2d p1, Vec2d p2, Vec2d p3, java.awt.Color color, float thickness);
    }
    
    /**
     * 样式处理器接口
     */
    interface StyleHandler {
        Object getFinalStyle();
    }
    
    /**
     * 吸附处理器接口
     */
    interface SnapHandler {
        Vec2d getSnappedWorldPoint(Vec2d screenPos, Object camera);
    }
}