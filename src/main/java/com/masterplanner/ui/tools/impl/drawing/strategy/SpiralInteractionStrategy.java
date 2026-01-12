package com.masterplanner.ui.tools.impl.drawing.strategy;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.tools.impl.drawing.SpiralTool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 螺旋线工具专用交互策略 - 协作版本
 * 
 * <p>职责：解释用户输入，通过上下文调用SpiralTool的方法</p>
 * <ul>
 *   <li>分析用户点击和移动</li>
 *   <li>通过DrawingToolContext调用SpiralTool的方法</li>
 *   <li>不持有任何状态数据</li>
 * </ul>
 * 
 * <p>交互模式：</p>
 * <ul>
 *   <li>线性螺旋：4步点击（中心、起始半径、螺距、最大半径）</li>
 *   <li>其它类型：2步点击（中心、半径点）</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 3.0 - 协作版本
 */
public class SpiralInteractionStrategy implements IInteractionStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpiralInteractionStrategy.class);
    
    // ====== 无状态设计 - 移除所有状态字段 ======
    
    // 构造函数
    public SpiralInteractionStrategy() {
        LOGGER.debug("SpiralInteractionStrategy: 创建协作式螺旋线交互策略");
    }
    
    // ====== 交互方法实现 ======
    
    @Override
    public InteractionResult onMouseDown(Vec2d pos, int button, DrawingToolContext context) {
        if (button != 0) { // 非左键
            if (button == 1) { // 右键取消
                LOGGER.debug("SpiralInteractionStrategy.onMouseDown: 右键取消");
                context.resetDrawing("右键取消");
                return InteractionResult.CANCEL;
            }
            return InteractionResult.IGNORED;
        }
        
        LOGGER.debug("SpiralInteractionStrategy.onMouseDown: 左键点击位置={}", pos);
        
        // 通过上下文获取SpiralTool并调用其方法
        if (context instanceof SpiralTool spiralTool) {
            // 直接调用SpiralTool的处理方法
            spiralTool.handleMouseClick(pos, button);
            
            // 检查是否完成绘制
            if (spiralTool.isDrawingComplete()) {
                LOGGER.debug("SpiralInteractionStrategy.onMouseDown: 绘制完成");
                return InteractionResult.COMPLETE;
            } else {
                LOGGER.debug("SpiralInteractionStrategy.onMouseDown: 继续绘制");
                return InteractionResult.CONTINUE;
            }
        } else {
            LOGGER.warn("SpiralInteractionStrategy.onMouseDown: 上下文不是SpiralTool实例");
            return InteractionResult.IGNORED;
        }
    }
    
    @Override
    public InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context) {
        LOGGER.debug("SpiralInteractionStrategy.onMouseMove: 鼠标移动到 {}", pos);
        
        // 通过上下文获取SpiralTool并调用其方法
        if (context instanceof SpiralTool spiralTool) {
            spiralTool.handleMouseMove(pos);
            return InteractionResult.CONTINUE;
        } else {
            LOGGER.warn("SpiralInteractionStrategy.onMouseMove: 上下文不是SpiralTool实例");
            return InteractionResult.IGNORED;
        }
    }
    
    @Override
    public InteractionResult onMouseUp(Vec2d pos, int button, DrawingToolContext context) {
        // 螺旋线工具不使用鼠标释放事件
        return InteractionResult.IGNORED;
    }
    
    @Override
    public Shape getFinalShape() {
        // 图形创建职责已转移到SpiralTool和SpiralShapeFactory
        LOGGER.debug("SpiralInteractionStrategy.getFinalShape: 图形创建职责已转移");
        return null;
    }
    
    @Override
    public void reset() {
        // 无状态设计，reset方法体为空
        LOGGER.debug("SpiralInteractionStrategy.reset: 无状态重置");
    }
    
    @Override
    public String getStrategyName() {
        return "SpiralInteractionStrategy";
    }
    
    @Override
    public String getStrategyDescription() {
        return "螺旋线工具协作式交互策略 - 通过上下文调用工具方法";
    }
    
    @Override
    public List<Vec2d> getControlPoints() {
        // 无状态设计，不持有控制点
        return List.of();
    }
    
    @Override
    public Vec2d getCurrentMousePoint() {
        // 无状态设计，不持有鼠标位置
        return null;
    }
} 