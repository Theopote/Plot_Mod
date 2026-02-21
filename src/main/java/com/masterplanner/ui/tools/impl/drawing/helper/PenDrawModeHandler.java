package com.masterplanner.ui.tools.impl.drawing.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.geometry.shapes.BezierCurveShape;
import com.masterplanner.core.geometry.shapes.PolylineShape;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.tools.impl.drawing.strategy.IInteractionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import imgui.ImGui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

// 导入常量以便使用
import static com.masterplanner.ui.tools.impl.drawing.PolylineTool.Constants.*;

/**
 * 钢笔绘制模式处理器（重构版）
 * <p>
 * 负责处理钢笔模式下的所有交互逻辑，包括：
 * - 锚点创建
 * - 控制点拖动
 * - 贝塞尔曲线预览
 * - 最终图形生成
 * <p>
 * 重构改进：
 * - 使用PathNode数据结构替代混合存储
 * - 采用状态机管理绘制状态
 * - 使用常量替代魔法数字
 * - 实现更符合用户直觉的交互模型
 */
public class PenDrawModeHandler extends AbstractModeHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PenDrawModeHandler.class);
    
    // 钢笔特有状态
    private final List<PathNode> pathNodes = new ArrayList<>();
    private PenDrawingState state = PenDrawingState.IDLE;
    private PathNode currentNode = null; // 当前正在编辑的节点
    
    public PenDrawModeHandler(StyleHandler styleHandler) {
        super(styleHandler);
    }
    
    @Override
    public IInteractionStrategy.InteractionResult onMouseDown(Vec2d pos, int button, DrawingToolContext context) {
        LOGGER.debug("PenDrawModeHandler.onMouseDown: 位置={}, 按钮={}, 状态={}", pos, button, state);
        
        if (button != PenConstants.MOUSE_BUTTON_LEFT) { // 非左键
            if (button == PenConstants.MOUSE_BUTTON_RIGHT) { // 右键完成绘制
                if (canCompleteDrawing()) {
                    LOGGER.debug("钢笔模式右键完成绘制，当前节点数：{}", pathNodes.size());
                    return IInteractionStrategy.InteractionResult.COMPLETE;
                } else {
                    // 节点数不足，取消绘制
                    LOGGER.debug("钢笔模式右键取消绘制，节点数不足");
                    reset();
                    context.resetDrawing(PenConstants.ERROR_INSUFFICIENT_POINTS);
                    return IInteractionStrategy.InteractionResult.CANCEL;
                }
            }
            LOGGER.debug("忽略非左键按钮: {}", button);
            return IInteractionStrategy.InteractionResult.IGNORED;
        }
        
        // 处理左键点击
        return handleLeftMouseDown(pos, context);
    }
    
    @Override
    protected IInteractionStrategy.InteractionResult handleLeftMouseDown(Vec2d pos, DrawingToolContext context) {
        Vec2d worldPoint = getWorldPoint(pos, context);
        
        LOGGER.debug("钢笔模式鼠标按下: 位置={}, 状态={}", worldPoint, state);
        
        switch (state) {
            case IDLE:
                // 开始绘制，添加第一个节点
                pathNodes.clear();
                PathNode firstNode = new PathNode(worldPoint);
                pathNodes.add(firstNode);
                state = PenDrawingState.WAITING_FOR_POINT;
                isDrawing = true;
                context.updateStatusMessage(PenConstants.MSG_FIRST_POINT);
                LOGGER.debug("钢笔模式：添加第一个锚点");
                break;
                
            case WAITING_FOR_POINT:
                // 添加新的锚点
                PathNode newNode = new PathNode(worldPoint);
                pathNodes.add(newNode);
                currentNode = newNode;
                state = PenDrawingState.DEFINING_HANDLE;
                context.updateStatusMessage(PenConstants.MSG_DRAG_FOR_CURVE);
                LOGGER.debug("钢笔模式：添加新锚点，当前节点数：{}", pathNodes.size());
                break;
                
            case DEFINING_HANDLE:
                // 在定义控制手柄时点击，确认当前节点为角点
                if (currentNode != null) {
                    currentNode.convertToCorner();
                }
                state = PenDrawingState.WAITING_FOR_POINT;
                context.updateStatusMessage(PenConstants.MSG_DRAG_FOR_CURVE);
                LOGGER.debug("钢笔模式：确认角点");
                break;
                
            default:
                LOGGER.warn("钢笔模式：未知状态 {}", state);
                break;
        }
        
        // 通知工具更新预览
        notifyPreviewUpdate(context);
        return IInteractionStrategy.InteractionResult.CONTINUE;
    }
    
    @Override
    public IInteractionStrategy.InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context) {
        // 调用父类方法处理基础鼠标移动
        IInteractionStrategy.InteractionResult result = super.onMouseMove(pos, context);
        
        // 钢笔模式特殊处理：如果鼠标按下并拖动，更新控制点
        if (isDrawing && ImGui.getIO().getMouseDown(PenConstants.MOUSE_BUTTON_LEFT)) {
            handlePenMouseDrag(currentMousePoint);
        }
        
        return result;
    }
    
    @Override
    public IInteractionStrategy.InteractionResult onMouseUp(Vec2d pos, int button, DrawingToolContext context) {
        // 钢笔模式的松开处理：确认当前状态
        if (isDrawing && button == PenConstants.MOUSE_BUTTON_LEFT) {
            LOGGER.debug("钢笔模式：鼠标松开，当前节点数={}, 状态={}", pathNodes.size(), state);
            
            if (state == PenDrawingState.DEFINING_HANDLE) {
                // 完成控制手柄定义，回到等待下一个点的状态
                state = PenDrawingState.WAITING_FOR_POINT;
                currentNode = null;
                context.updateStatusMessage(PenConstants.MSG_DRAG_FOR_CURVE);
            }
        }
        return IInteractionStrategy.InteractionResult.CONTINUE;
    }
    
    /**
     * 处理钢笔模式的拖动逻辑
     * 重构：使用PathNode数据结构，实现更清晰的交互逻辑
     */
    private void handlePenMouseDrag(Vec2d point) {
        if (state != PenDrawingState.DEFINING_HANDLE || currentNode == null) {
            return;
        }
        
        LOGGER.debug("钢笔模式拖动：更新控制点，拖拽位置={}", point);
        
        // 根据拖拽位置设置平滑控制点
        currentNode.setSmoothControlPoints(point);
        
        // 通知工具更新预览
        // 这里可以通过context通知工具更新预览
    }
    
    @Override
    public void renderPreview(DrawingAdapter adapter) {
        if (pathNodes.isEmpty()) return;
        
        // 获取当前图层的颜色
        Color layerColor = getLayerColor();
        Color controlColor = new Color(layerColor.getRed(), layerColor.getGreen(), layerColor.getBlue(), 128);
        Color previewColor = new Color(layerColor.getRed(), layerColor.getGreen(), layerColor.getBlue(), 128);
        
        // 绘制已完成的贝塞尔曲线段
        for (int i = 0; i < pathNodes.size() - 1; i++) {
            PathNode p0_node = pathNodes.get(i);
            PathNode p1_node = pathNodes.get(i + 1);
            
            if (!p0_node.isCorner() || !p1_node.isCorner()) {
                // 至少有一个节点有控制点，绘制贝塞尔曲线
                adapter.drawBezierCurve(p0_node.getAnchor(), p0_node.getControlNext(), 
                                      p1_node.getControlPrev(), p1_node.getAnchor(), 
                                      layerColor, Rendering.PREVIEW_LINE_THICKNESS);
                
                // 绘制控制线
                if (!p0_node.isCorner()) {
                    adapter.drawLine(p0_node.getAnchor(), p0_node.getControlNext(), 
                                   controlColor, Rendering.CONTROL_LINE_THICKNESS);
                }
                if (!p1_node.isCorner()) {
                    adapter.drawLine(p1_node.getAnchor(), p1_node.getControlPrev(), 
                                   controlColor, Rendering.CONTROL_LINE_THICKNESS);
                }
            } else {
                // 两个都是角点，绘制直线
                adapter.drawLine(p0_node.getAnchor(), p1_node.getAnchor(), 
                               layerColor, Rendering.PREVIEW_LINE_THICKNESS);
            }
        }
        
        // 绘制所有锚点
        for (PathNode node : pathNodes) {
            adapter.drawCircle(node.getAnchor(), Rendering.POINT_SIZE, layerColor, true);
        }
        
        // 绘制控制点
        for (PathNode node : pathNodes) {
            if (!node.isCorner()) {
                adapter.drawCircle(node.getControlPrev(), Rendering.CONTROL_POINT_SIZE, controlColor, true);
                adapter.drawCircle(node.getControlNext(), Rendering.CONTROL_POINT_SIZE, controlColor, true);
            }
        }
        
        // 绘制到当前鼠标位置的预览
        if (currentMousePoint != null && !pathNodes.isEmpty()) {
            PathNode lastNode = pathNodes.getLast();
            
            if (state == PenDrawingState.WAITING_FOR_POINT) {
                // 等待下一个点，显示直线预览
                adapter.drawLine(lastNode.getAnchor(), currentMousePoint, previewColor, Rendering.PREVIEW_LINE_THICKNESS);
                adapter.drawCircle(currentMousePoint, Rendering.POINT_SIZE, previewColor, true);
            } else if (state == PenDrawingState.DEFINING_HANDLE && currentNode != null) {
                // 正在定义控制手柄，显示曲线预览
                adapter.drawBezierCurve(lastNode.getAnchor(), lastNode.getControlNext(), 
                                      currentMousePoint, currentMousePoint, 
                                      previewColor, Rendering.PREVIEW_LINE_THICKNESS);
                adapter.drawCircle(currentMousePoint, Rendering.CONTROL_POINT_SIZE, previewColor, true);
            }
        }
    }
    
    /**
     * 获取当前图层的颜色
     * 优化：移除不必要的try-catch，直接进行类型检查
     */
    private Color getLayerColor() {
        Object styleObj = styleHandler.getFinalStyle();
        if (styleObj instanceof ShapeStyle shapeStyle) {
            // 优先使用描边颜色
            int strokeColor = shapeStyle.getStrokeColor();
            if (strokeColor != 0) {
                return new Color(strokeColor, true); // 支持透明度
            }
            // 其次使用填充颜色
            int fillColor = shapeStyle.getFillColor();
            if (fillColor != 0) {
                return new Color(fillColor, true); // 支持透明度
            }
        }
        
        // 回退到默认颜色
        return Colors.LINE;
    }
    
    @Override
    public Shape getFinalShape() {
        if (pathNodes.size() < PenConstants.MIN_POINTS_FOR_COMPLETION) {
            return null;
        }
        
        // 转换PathNode为BezierUtils格式
        List<Vec2d> bezierPoints = convertPathNodesToBezierPoints();
        
        // 使用 BezierUtils 转换贝塞尔曲线
        // 修复：钢笔工具应该创建开放路径，不自动闭合
        BezierUtils.BezierData bezierData = BezierUtils.convertPointsToCurveData(bezierPoints, false);
        
        if (bezierData.getAnchors().size() < PenConstants.MIN_POINTS_FOR_COMPLETION) {
            LOGGER.warn("钢笔模式贝塞尔曲线转换失败，锚点数不足: {}", bezierData.getAnchors().size());
            return null;
        }
        
        if (bezierData.getControls().isEmpty()) {
            // 只有锚点，创建直线
            PolylineShape polyline = new PolylineShape(bezierData.getAnchors(), bezierData.shouldClose());
            ShapeStyle style = getShapeStyle();
            if (style != null) {
                polyline.setStyle(style);
            }
            LOGGER.debug("钢笔模式创建直线: 确定节点数={}", pathNodes.size());
            return polyline;
        } else {
            // 创建贝塞尔曲线
            // 修复：bezierData.getControls()现在直接返回List<Vec2d[]>，无需转换
            BezierCurveShape curve = new BezierCurveShape(bezierData.getAnchors(), bezierData.getControls(), bezierData.shouldClose());
            ShapeStyle style = getShapeStyle();
            if (style != null) {
                curve.setStyle(style);
            }
            LOGGER.debug("钢笔模式创建贝塞尔曲线: 确定节点数={}, 锚点数={}, 控制点数={}, 闭合={}", 
                       pathNodes.size(), bezierData.getAnchors().size(), 
                       bezierData.getControls().size(), bezierData.shouldClose());
            return curve;
        }
    }
    
    /**
     * 将PathNode列表转换为BezierUtils格式的点列表
     */
    private List<Vec2d> convertPathNodesToBezierPoints() {
        List<Vec2d> bezierPoints = new ArrayList<>();
        
        for (int i = 0; i < pathNodes.size(); i++) {
            PathNode node = pathNodes.get(i);
            bezierPoints.add(node.getAnchor()); // 添加锚点
            
            if (i < pathNodes.size() - 1) {
                // 添加控制点（除了最后一个节点）
                bezierPoints.add(node.getControlNext());
                bezierPoints.add(pathNodes.get(i + 1).getControlPrev());
            }
        }
        
        return bezierPoints;
    }
    
    /**
     * 安全获取ShapeStyle
     */
    private ShapeStyle getShapeStyle() {
        Object styleObj = styleHandler.getFinalStyle();
        if (styleObj instanceof ShapeStyle) {
            return (ShapeStyle) styleObj;
        }
        return null;
    }
    
    /**
     * 检查是否可以完成绘制
     */
    private boolean canCompleteDrawing() {
        return isDrawing && pathNodes.size() >= PenConstants.MIN_POINTS_FOR_COMPLETION;
    }
    
    @Override
    public void reset() {
        pathNodes.clear();
        state = PenDrawingState.IDLE;
        currentNode = null;
        super.reset(); // 调用父类的 reset 方法
    }
    
    @Override
    public String getStatusMessage() {
        switch (state) {
            case IDLE:
                return PenConstants.MSG_START_DRAWING;
            case WAITING_FOR_POINT:
                if (pathNodes.size() == 1) {
                    return PenConstants.MSG_FIRST_POINT;
                } else {
                    return String.format(PenConstants.MSG_ADDING_POINTS, pathNodes.size());
                }
            case DEFINING_HANDLE:
                return PenConstants.MSG_DRAG_FOR_CURVE;
            case COMPLETED:
                return "绘制完成";
            default:
                return "未知状态";
        }
    }
    
    @Override
    public IInteractionStrategy.InteractionResult onKeyDown(int key, DrawingToolContext context) {
        if (key == PenConstants.KEY_ESC) { // ESC键：总是取消绘制，遵循通用UI惯例
            LOGGER.debug("钢笔模式ESC键取消绘制");
            reset();
            context.resetDrawing(PenConstants.ERROR_CANCELLED);
            return IInteractionStrategy.InteractionResult.CANCEL;
        } else if (key == PenConstants.KEY_ENTER && canCompleteDrawing()) { // Enter键：完成绘制
            LOGGER.debug("钢笔模式Enter键完成绘制，当前节点数：{}", pathNodes.size());
            return IInteractionStrategy.InteractionResult.COMPLETE;
        }
        return super.onKeyDown(key, context); // 处理其他键
    }

    /**
     * 获取当前绘制状态
     */
    public PenDrawingState getState() {
        return state;
    }
}