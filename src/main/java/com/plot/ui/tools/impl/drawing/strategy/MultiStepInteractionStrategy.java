package com.plot.ui.tools.impl.drawing.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.plot.utils.PlotI18n;

import java.util.ArrayList;
import java.util.List;

/**
 * 多步骤交互策略实现 - 统一状态管理版本
 * 
 * <p>处理"点击-移动-点击"交互模式，适用于：</p>
 * <ul>
 *   <li>直线工具（2步：起点、终点）</li>
 *   <li>多边形工具（多步：连续点击设置顶点）</li>
 *   <li>贝塞尔曲线工具（多步：控制点设置）</li>
 *   <li>弧线工具（3步：起点、中间点、终点）</li>
 * </ul>
 * 
 * <p><strong>统一状态管理特点：</strong></p>
 * <ul>
 *   <li>完全自管理状态，不依赖上下文状态</li>
 *   <li>通过返回值告知上下文如何响应</li>
 *   <li>职责单一，只处理多步骤交互逻辑</li>
 *   <li>不直接调用上下文的重置方法</li>
 * </ul>
 * 
 * <p><strong>特性：</strong></p>
 * <ul>
 *   <li>支持可变步数（2步到无限步）</li>
 *   <li>支持提前完成（双击或右键）</li>
 *   <li>实时预览当前步骤</li>
 *   <li>智能状态管理</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 3.0 - 统一状态管理
 */
public class MultiStepInteractionStrategy implements IInteractionStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(MultiStepInteractionStrategy.class);
    
    // 配置参数
    private final int maxSteps;
    private final boolean allowEarlyCompletion;
    private final String toolName;
    
    // 策略内部状态
    private final List<Vec2d> controlPoints = new ArrayList<>();
    private Vec2d currentMousePoint;
    private boolean isActive = false;
    private int currentStep = 0;
    private Shape finalShape; // 缓存最终图形
    
    /**
     * 构造函数（完整配置）
     * 
     * @param maxSteps 最大步数（-1表示无限制）
     * @param allowEarlyCompletion 是否允许提前完成
     * @param toolName 工具名称（用于日志）
     */
    public MultiStepInteractionStrategy(int maxSteps, boolean allowEarlyCompletion, String toolName) {
        this.maxSteps = maxSteps;
        this.allowEarlyCompletion = allowEarlyCompletion;
        this.toolName = toolName != null ? toolName : "多步骤工具";
    }
    
    /**
     * 便利构造函数（直线工具专用：2步，不允许提前完成）
     */
    public static MultiStepInteractionStrategy forLineTool() {
        return new MultiStepInteractionStrategy(2, false, "直线工具");
    }
    
    /**
     * 便利构造函数（多边形工具专用：无限步，允许提前完成）
     */
    public static MultiStepInteractionStrategy forPolygonTool() {
        return new MultiStepInteractionStrategy(-1, true, "多边形工具");
    }
    
    @Override
    public InteractionResult onMouseDown(Vec2d pos, int button, DrawingToolContext context) {
        try {
            if (button == 0) { // 左键点击
                return handleLeftClick(pos, context);
            } else if (button == 1 && allowEarlyCompletion && controlPoints.size() >= 2) { // 右键提前完成
                return handleEarlyCompletion(context);
            }
            return InteractionResult.IGNORED;
            
        } catch (Exception e) {
            LOGGER.error("{} 鼠标按下处理失败: {}", toolName, e.getMessage(), e);
            reset();
            return InteractionResult.CANCEL;
        }
    }
    
    /**
     * 处理左键点击逻辑
     * 修复：避免每次点击都提交图形
     */
    private InteractionResult handleLeftClick(Vec2d pos, DrawingToolContext context) {
        // 修复：确保正确的吸附处理
        Vec2d snappedPoint;
        
        // 检查是否有有效的吸附处理器和相机
        if (context.getSnapHandler() != null && context.getCamera() != null) {
            snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            LOGGER.trace("{} 左键点击吸附: 原始({}, {}) -> 吸附({}, {})", 
                        toolName, pos.x, pos.y, snappedPoint.x, snappedPoint.y);
        } else {
            // 备用：如果吸附不可用，使用原始位置
            snappedPoint = pos;
            LOGGER.warn("{} 左键点击无吸附功能可用: SnapHandler={}, Camera={}", 
                       toolName, 
                       context.getSnapHandler() != null ? "有效" : "null",
                       context.getCamera() != null ? "有效" : "null");
        }
        
        if (!isActive) {
            // 开始新的交互
            return startInteraction(snappedPoint, context);
        } else {
            // 修复：检查控制点数量，而不是当前步数
            // 对于直线工具：第一次点击添加控制点，第二次点击完成交互
            if (controlPoints.size() >= maxSteps - 1) {
                // 已有足够控制点：添加最后一个控制点并完成交互
                controlPoints.add(snappedPoint);
                return finishInteraction(context);
            } else {
                // 中间步骤：添加控制点并继续
                return addControlPoint(snappedPoint, context);
            }
        }
    }
    
    /**
     * 开始交互
     */
    private InteractionResult startInteraction(Vec2d firstPoint, DrawingToolContext context) {
        isActive = true;
        controlPoints.clear();
        controlPoints.add(firstPoint);
        currentStep = 0;
        
        // 设置工具状态为绘制中
        context.setToolState(com.plot.ui.tools.impl.drawing.DrawingTool.ToolState.DRAWING);
        
        LOGGER.debug("{} 开始交互 - 第一个控制点: {}", toolName, firstPoint);
        return InteractionResult.CONTINUE;
    }
    
    /**
     * 添加控制点
     */
    private InteractionResult addControlPoint(Vec2d point, DrawingToolContext context) {
        controlPoints.add(point);
        currentStep++;
        
        LOGGER.debug("{} 添加控制点 - 步骤 {}/{}: {}", 
                    toolName, currentStep + 1, maxSteps, point);
        
        // 更新预览
        updatePreview(context);
        
        // 检查是否可以提前完成
        // 可以继续，也可以完成

        return InteractionResult.CONTINUE;
    }
    
    /**
     * 处理提前完成（如双击）
     */
    private InteractionResult handleEarlyCompletion(DrawingToolContext context) {
        if (allowEarlyCompletion && controlPoints.size() >= maxSteps) {
            LOGGER.debug("{} 提前完成交互 - 控制点数量: {}", toolName, controlPoints.size());
            return finishInteraction(context);
        }
        return InteractionResult.CONTINUE;
    }
    
    /**
     * 完成交互
     * 修复：创建最终图形但不直接提交，让DrawingTool通过COMPLETE结果处理提交
     */
    private InteractionResult finishInteraction(DrawingToolContext context) {
        try {
            // 检查是否有足够的控制点
            int minPointsRequired = getMinimumPointsForFinal();
            if (controlPoints.size() < minPointsRequired) {
                LOGGER.warn("{} 控制点数量不足，无法完成交互: 需要至少 {} 个，当前 {} 个", 
                           toolName, minPointsRequired, controlPoints.size());
                return InteractionResult.CONTINUE;
            }
            
            // 创建最终图形并缓存
            finalShape = createFinalShape(context);
            if (finalShape != null) {
                LOGGER.debug("{} 成功创建最终图形，准备完成交互", toolName);
                
                // 修复：不再直接调用commitShape，而是缓存图形并返回COMPLETE
                // 让DrawingTool的handleInteractionResult方法处理提交
                return InteractionResult.COMPLETE;
            } else {
                LOGGER.error("{} 创建最终图形失败", toolName);
                reset();
                return InteractionResult.CANCEL;
            }
        } catch (Exception e) {
            LOGGER.error("{} 完成交互时出错: {}", toolName, e.getMessage(), e);
            reset();
            return InteractionResult.CANCEL;
        }
    }
    
    @Override
    public InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context) {
        if (!isActive) {
            return InteractionResult.IGNORED;
        }
        
        try {
            // 修复：确保正确的吸附处理
            Vec2d snappedPoint;
            
            // 检查是否有有效的吸附处理器和相机
            if (context.getSnapHandler() != null && context.getCamera() != null) {
                snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
                LOGGER.trace("{} 鼠标移动吸附: 原始({}, {}) -> 吸附({}, {})", 
                            toolName, pos.x, pos.y, snappedPoint.x, snappedPoint.y);
            } else {
                // 备用：如果吸附不可用，使用原始位置
                snappedPoint = pos;
                LOGGER.trace("{} 鼠标移动无吸附: ({}, {})", 
                            toolName, pos.x, pos.y);
            }
            
            // 更新当前鼠标位置（用于预览）
            currentMousePoint = snappedPoint;
            
            // 更新预览图形
            updatePreview(context);
            
            LOGGER.trace("{} 鼠标移动: ({}, {}), 当前步数: {}", 
                        toolName, currentMousePoint.x, currentMousePoint.y, currentStep);
            
            return InteractionResult.CONTINUE;
            
        } catch (Exception e) {
            LOGGER.error("{} 鼠标移动处理失败: {}", toolName, e.getMessage(), e);
            return InteractionResult.CANCEL;
        }
    }
    
    @Override
    public InteractionResult onMouseUp(Vec2d pos, int button, DrawingToolContext context) {
        // 多步骤模式忽略鼠标释放事件
        // 所有逻辑都在onMouseDown中处理
        return InteractionResult.IGNORED;
    }
    
    /**
     * 更新预览图形
     */
    private void updatePreview(DrawingToolContext context) {
        if (controlPoints.size() < getMinimumPointsForPreview()) {
            return;
        }
        
        try {
            Shape previewShape = createPreviewShape(context);
            if (previewShape != null) {
                context.getStyleHandler().applyPreviewStyle(previewShape);
                context.setPreviewShape(previewShape);
            }
        } catch (Exception e) {
            LOGGER.error("{} 更新预览失败: {}", toolName, e.getMessage(), e);
        }
    }
    
    /**
     * 创建预览图形
     */
    private Shape createPreviewShape(DrawingToolContext context) {
        if (controlPoints.isEmpty() || currentMousePoint == null) {
            return null;
        }
        
        // 创建临时控制点列表，包含当前鼠标位置
        List<Vec2d> tempPoints = new ArrayList<>(controlPoints);
        if (!tempPoints.contains(currentMousePoint)) {
            tempPoints.add(currentMousePoint);
        }
        
        // 根据点数创建预览图形
        if (tempPoints.size() >= 2) {
            // 优先多点预览
            Shape shape = context.getShapeFactory().createShapeFromPoints(tempPoints);
            if (shape != null) return shape;
            // 兼容两点工具
            return context.getShapeFactory().createShape(tempPoints.getFirst(), tempPoints.getLast());
        }
        return null;
    }
    
    /**
     * 创建最终图形
     */
    private Shape createFinalShape(DrawingToolContext context) {
        if (controlPoints.size() < getMinimumPointsForFinal()) {
            return null;
        }
        try {
            // 优先多点创建，支持如星形等三点工具
            Shape shape = context.getShapeFactory().createShapeFromPoints(controlPoints);
            if (shape != null) return shape;
            // 兼容两点工具
            if (controlPoints.size() >= 2) {
                return context.getShapeFactory().createShape(controlPoints.getFirst(), controlPoints.getLast());
            }
            return null;
        } catch (Exception e) {
            LOGGER.error("{} 创建最终图形失败: {}", toolName, e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public Shape getFinalShape() {
        return finalShape;
    }
    
    @Override
    public void reset() {
        LOGGER.debug("{} 多步骤交互重置", toolName);
        
        controlPoints.clear();
        currentMousePoint = null;
        isActive = false;
        currentStep = 0;
        finalShape = null;
    }
    
    @Override
    public String getStrategyName() {
        return "多步骤模式";
    }
    
    @Override
    public String getStrategyDescription() {
        if (maxSteps > 0) {
            return PlotI18n.status("status.plot.draw.multistep.max", maxSteps);
        } else {
            return PlotI18n.tr("status.plot.draw.multistep.finish");
        }
    }
    
    @Override
    public List<Vec2d> getControlPoints() {
        return new ArrayList<>(controlPoints);
    }
    
    @Override
    public Vec2d getCurrentMousePoint() {
        return currentMousePoint;
    }
    
    // ====== 辅助方法 ======
    
    /**
     * 获取预览所需的最小点数
     */
    private int getMinimumPointsForPreview() {
        return 1; // 直线需要1点即可显示预览，其他图形也是1点
    }
    
    /**
     * 获取最终图形所需的最小点数
     */
    private int getMinimumPointsForFinal() {
        return Math.max(2, maxSteps > 0 ? maxSteps : 2);
    }

    /**
     * 检查是否正在进行交互
     */
    public boolean isActive() {
        return isActive;
    }
} 