package com.masterplanner.ui.tools.impl.drawing.strategy;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.model.Shape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 拖放交互策略实现 - 统一状态管理版本
 * 
 * <p>处理传统的"按下-拖动-释放"交互模式，适用于：</p>
 * <ul>
 *   <li>矩形工具</li>
 *   <li>圆形工具</li>
 *   <li>椭圆工具</li>
 *   <li>等需要起点和终点的图形</li>
 * </ul>
 * 
 * <p><strong>统一状态管理特点：</strong></p>
 * <ul>
 *   <li>完全自管理状态，不依赖上下文状态</li>
 *   <li>通过返回值告知上下文如何响应</li>
 *   <li>职责单一，只处理拖放交互逻辑</li>
 *   <li>不直接调用上下文的重置方法</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 3.0 - 统一状态管理
 */
public class DragInteractionStrategy implements IInteractionStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(DragInteractionStrategy.class);
    
    // 策略内部状态
    private Vec2d startPoint;
    private Vec2d currentPoint;
    private boolean isDragging = false;
    private Shape finalShape; // 缓存最终图形
    
    /**
     * 默认构造函数
     * 策略不再需要依赖注入，所有操作通过上下文回调
     */
    public DragInteractionStrategy() {
        // 策略模式下，依赖通过上下文提供
    }
    
    @Override
    public InteractionResult onMouseDown(Vec2d pos, int button, DrawingToolContext context) {
        if (button != 0 || isDragging) { // 0 = 左键
            return InteractionResult.IGNORED;
        }
        
        try {
            // 通过上下文获取吸附后的点
            startPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            currentPoint = startPoint;
            isDragging = true;
            finalShape = null; // 清除之前的最终图形
            
            // 通过上下文设置工具状态
            context.setToolState(com.masterplanner.ui.tools.impl.drawing.DrawingTool.ToolState.DRAWING);
            
            // 创建初始预览图形
            Shape previewShape = context.getShapeFactory().createShape(startPoint, startPoint);
            if (previewShape != null) {
                context.getStyleHandler().applyPreviewStyle(previewShape);
                context.setPreviewShape(previewShape);
            }
            
            LOGGER.debug("拖放交互开始: 起点({}, {})", startPoint.x, startPoint.y);
            return InteractionResult.CONTINUE;
            
        } catch (Exception e) {
            LOGGER.error("拖放交互开始失败: {}", e.getMessage(), e);
            reset();
            return InteractionResult.CANCEL;
        }
    }
    
    @Override
    public InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context) {
        if (!isDragging) {
            return InteractionResult.IGNORED;
        }
        
        try {
            // 通过上下文获取吸附后的点
            currentPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            
            // 创建并更新预览图形
            Shape previewShape = context.getShapeFactory().createShape(startPoint, currentPoint);
            if (previewShape != null) {
                context.getStyleHandler().applyPreviewStyle(previewShape);
                context.setPreviewShape(previewShape);
            }
            
            LOGGER.trace("拖放交互移动: 终点({}, {})", currentPoint.x, currentPoint.y);
            return InteractionResult.CONTINUE;
            
        } catch (Exception e) {
            LOGGER.error("拖放交互移动失败: {}", e.getMessage(), e);
            return InteractionResult.CANCEL;
        }
    }
    
    @Override
    public InteractionResult onMouseUp(Vec2d pos, int button, DrawingToolContext context) {
        if (button != 0 || !isDragging) {
            return InteractionResult.IGNORED;
        }
        
        try {
            // 通过上下文获取最终吸附后的点
            currentPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            
            // 检查是否有效（距离不能太短）
            if (startPoint.distance(currentPoint) < 1.0) {
                LOGGER.debug("拖放距离太短，取消绘制");
                reset();
                return InteractionResult.CANCEL;
            }
            
            // 创建最终图形
            finalShape = context.getShapeFactory().createShape(startPoint, currentPoint);
            if (finalShape != null) {
                // 应用最终样式
                context.getStyleHandler().applyFinalStyle(finalShape);
                
                // 重置策略状态
                isDragging = false;
                
                LOGGER.debug("拖放交互完成: 终点({}, {})", currentPoint.x, currentPoint.y);
                return InteractionResult.COMPLETE;
            } else {
                LOGGER.warn("无法创建最终图形");
                reset();
                return InteractionResult.CANCEL;
            }
            
        } catch (Exception e) {
            LOGGER.error("拖放交互完成失败: {}", e.getMessage(), e);
            reset();
            return InteractionResult.CANCEL;
        }
    }
    
    @Override
    public Shape getFinalShape() {
        return finalShape;
    }
    
    @Override
    public void reset() {
        LOGGER.debug("拖放交互重置");
        startPoint = null;
        currentPoint = null;
        isDragging = false;
        finalShape = null;
    }
    
    @Override
    public String getStrategyName() {
        return "拖放模式";
    }
    
    @Override
    public String getStrategyDescription() {
        return "按下鼠标拖动绘制，松开完成";
    }
    
    @Override
    public List<Vec2d> getControlPoints() {
        List<Vec2d> points = new ArrayList<>();
        if (startPoint != null) {
            points.add(startPoint);
        }
        if (currentPoint != null && !currentPoint.equals(startPoint)) {
            points.add(currentPoint);
        }
        return points;
    }
    
    @Override
    public Vec2d getCurrentMousePoint() {
        return currentPoint;
    }
    
    // ====== 辅助方法（供调试使用） ======
    
    /**
     * 获取起点（只读）
     */
    public Vec2d getStartPoint() {
        return startPoint;
    }
    
    /**
     * 获取当前点（只读）
     */
    public Vec2d getCurrentPoint() {
        return currentPoint;
    }
    
    /**
     * 检查是否正在拖动
     */
    public boolean isDragging() {
        return isDragging;
    }
} 