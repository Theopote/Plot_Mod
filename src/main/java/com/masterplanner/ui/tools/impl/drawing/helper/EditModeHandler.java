package com.masterplanner.ui.tools.impl.drawing.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.geometry.shapes.BezierCurveShape;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.tools.impl.drawing.strategy.IInteractionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import imgui.ImGui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 编辑模式处理器
 * <p>
 * 负责处理编辑模式下的所有交互逻辑，包括：
 * - 节点选择和移动
 * - 控制点调整
 * - 段类型转换
 * - 编辑预览渲染
 */
public class EditModeHandler implements IModeHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EditModeHandler.class);
    
    // 常量定义
    private static final float PREVIEW_LINE_THICKNESS = 2.5f;
    private static final float CONTROL_LINE_THICKNESS = 1.5f;
    private static final float POINT_SIZE = 4.0f;
    private static final float CONTROL_POINT_SIZE = 3.0f;
    private static final float SELECTED_POINT_SIZE = 5.0f;
    private static final double SELECTION_TOLERANCE = 8.0;
    private static final Color LINE_COLOR = new Color(70, 130, 255);

    // 编辑状态
    private final List<Vec2d> editPoints = new ArrayList<>();
    private final List<Boolean> isCurveSegment = new ArrayList<>();
    private Shape editingShape;
    private int selectedNodeIndex = -1;
    private int selectedControlPointIndex = -1;
    private boolean isDraggingNode = false;
    private boolean isDraggingControlPoint = false;
    private Vec2d dragStartPoint;
    private boolean isControlPointSymmetric = true;
    private final StyleHandler styleHandler;
    
    public EditModeHandler(StyleHandler styleHandler) {
        this.styleHandler = styleHandler;
    }

    @Override
    public IInteractionStrategy.InteractionResult onMouseDown(Vec2d pos, int button, DrawingToolContext context) {
        if (button != 0) return IInteractionStrategy.InteractionResult.IGNORED; // 只响应左键
        
        boolean isShiftPressed = ImGui.getIO().getKeyShift();
        
        // 查找最近的节点或控制点
        int nearestIndex = findNearestPoint(pos);
        
        if (nearestIndex != -1) {
            if (nearestIndex % 3 == 0) { // 锚点
                if (isShiftPressed) {
                    // 转换相邻段的类型
                    int segmentIndex = nearestIndex / 3;
                    if (segmentIndex < isCurveSegment.size()) {
                        toggleSegmentType(segmentIndex);
                    }
                    if (segmentIndex > 0) {
                        toggleSegmentType(segmentIndex - 1);
                    }
                } else {
                    // 选择并准备拖动锚点
                    selectedNodeIndex = nearestIndex;
                    isDraggingNode = true;
                    dragStartPoint = pos;
                }
            } else { // 控制点
                selectedControlPointIndex = nearestIndex;
                isDraggingControlPoint = true;
                dragStartPoint = pos;
            }
            return IInteractionStrategy.InteractionResult.CONTINUE;
        }
        
        // 如果没有点击到任何点，取消选择
        selectedNodeIndex = -1;
        selectedControlPointIndex = -1;
        return IInteractionStrategy.InteractionResult.IGNORED;
    }
    
    @Override
    public IInteractionStrategy.InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context) {
        if (isDraggingNode && selectedNodeIndex != -1) {
            // 移动锚点
            Vec2d delta = pos.subtract(dragStartPoint);
            
            editPoints.set(selectedNodeIndex, editPoints.get(selectedNodeIndex).add(delta));
            
            // 同时移动相邻的控制点
            if (selectedNodeIndex > 0) {
                editPoints.set(selectedNodeIndex - 1, editPoints.get(selectedNodeIndex - 1).add(delta));
            }
            if (selectedNodeIndex < editPoints.size() - 1) {
                editPoints.set(selectedNodeIndex + 1, editPoints.get(selectedNodeIndex + 1).add(delta));
            }
            
            dragStartPoint = pos;
            return IInteractionStrategy.InteractionResult.CONTINUE;
        }
        
        if (isDraggingControlPoint && selectedControlPointIndex != -1) {
            // 移动控制点
            editPoints.set(selectedControlPointIndex, pos);
            
            // 如果是对称模式，同步更新对称的控制点
            if (isControlPointSymmetric) {
                updateSymmetricControlPoint(selectedControlPointIndex, pos);
            }
            
            return IInteractionStrategy.InteractionResult.CONTINUE;
        }
        
        return IInteractionStrategy.InteractionResult.IGNORED;
    }
    
    @Override
    public IInteractionStrategy.InteractionResult onMouseUp(Vec2d pos, int button, DrawingToolContext context) {
        isDraggingNode = false;
        isDraggingControlPoint = false;
        return IInteractionStrategy.InteractionResult.CONTINUE;
    }
    
    /**
     * 查找最近的点
     */
    private int findNearestPoint(Vec2d point) {
        double minDistance = SELECTION_TOLERANCE;
        int nearestIndex = -1;
        
        // 优先检查锚点
        for (int i = 0; i < editPoints.size(); i += 3) {
            double dist = point.distance(editPoints.get(i));
            if (dist < minDistance) {
                minDistance = dist;
                nearestIndex = i;
            }
        }
        
        // 如果没有找到锚点，检查控制点
        if (nearestIndex == -1) {
            for (int i = 1; i < editPoints.size() - 1; i++) {
                if (i % 3 != 0) { // 只检查控制点
                    double dist = point.distance(editPoints.get(i));
                    if (dist < minDistance) {
                        minDistance = dist;
                        nearestIndex = i;
                    }
                }
            }
        }
        
        return nearestIndex;
    }
    
    /**
     * 更新对称控制点
     */
    private void updateSymmetricControlPoint(int controlIndex, Vec2d newPos) {
        // 找到相关的锚点
        int anchorIndex = (controlIndex / 3) * 3;
        Vec2d anchorPoint = editPoints.get(anchorIndex);
        
        // 计算从锚点到新控制点位置的向量
        Vec2d controlVector = newPos.subtract(anchorPoint);
        
        // 更新对称的控制点
        int oppositeIndex = (controlIndex % 3 == 1) ? controlIndex + 1 : controlIndex - 1;
        
        if (oppositeIndex >= 0 && oppositeIndex < editPoints.size()) {
            editPoints.set(oppositeIndex, anchorPoint.subtract(controlVector));
        }
    }
    
    /**
     * 切换段类型（直线/曲线）
     */
    private void toggleSegmentType(int segmentIndex) {
        if (segmentIndex < 0 || segmentIndex >= isCurveSegment.size()) return;
        
        boolean isCurve = isCurveSegment.get(segmentIndex);
        isCurveSegment.set(segmentIndex, !isCurve);
        
        // 更新控制点位置
        int startAnchorIndex = segmentIndex * 3;
        int endAnchorIndex = startAnchorIndex + 3;
        
        if (endAnchorIndex < editPoints.size()) {
            Vec2d startAnchor = editPoints.get(startAnchorIndex);
            Vec2d endAnchor = editPoints.get(endAnchorIndex);
            Vec2d dir = endAnchor.subtract(startAnchor);
            
            if (!isCurve) { // 转换为曲线
                calculateSmartControlPoints(segmentIndex, startAnchorIndex, endAnchorIndex);
            } else { // 转换为直线
                editPoints.set(startAnchorIndex + 1, startAnchor.add(dir.multiply(1.0/3.0)));
                editPoints.set(startAnchorIndex + 2, startAnchor.add(dir.multiply(2.0/3.0)));
            }
        }
        
        LOGGER.debug("段 {} 类型从 {} 切换到 {}", segmentIndex, 
                    isCurve ? "曲线" : "直线", !isCurve ? "曲线" : "直线");
    }
    
    /**
     * 计算智能控制点
     */
    private void calculateSmartControlPoints(int segmentIndex, int startAnchorIndex, int endAnchorIndex) {
        Vec2d startAnchor = editPoints.get(startAnchorIndex);
        Vec2d endAnchor = editPoints.get(endAnchorIndex);
        Vec2d dir = endAnchor.subtract(startAnchor);
        double segmentLength = dir.length();
        
        // 基础控制点长度（线段长度的1/3）
        double controlLength = segmentLength / 3.0;
        
        editPoints.set(startAnchorIndex + 1, startAnchor.add(dir.normalize().multiply(controlLength)));
        editPoints.set(startAnchorIndex + 2, endAnchor.subtract(dir.normalize().multiply(controlLength)));
    }
    
    @Override
    public void renderPreview(DrawingAdapter adapter) {
        if (editPoints.isEmpty()) return;
        
        // 获取当前图层的颜色
        Color layerColor = getLayerColor();
        Color controlColor = new Color(layerColor.getRed(), layerColor.getGreen(), layerColor.getBlue(), 128);
        Color selectedColor = new Color(255, 50, 50); // 选中状态保持红色
        
        // 绘制曲线段
        for (int i = 0; i <= editPoints.size() - 4; i += 3) {
            Vec2d p0 = editPoints.get(i);
            Vec2d p1 = editPoints.get(i + 1);
            Vec2d p2 = editPoints.get(i + 2);
            Vec2d p3 = editPoints.get(i + 3);
            
            int segmentIndex = i / 3;
            boolean isCurve = segmentIndex < isCurveSegment.size() && isCurveSegment.get(segmentIndex);
            
            if (isCurve) {
                adapter.drawBezierCurve(p0, p1, p2, p3, layerColor, PREVIEW_LINE_THICKNESS);
            } else {
                adapter.drawLine(p0, p3, layerColor, PREVIEW_LINE_THICKNESS);
            }
            
            // 绘制控制线（使用半透明的图层颜色）
            adapter.drawLine(p0, p1, controlColor, CONTROL_LINE_THICKNESS);
            adapter.drawLine(p2, p3, controlColor, CONTROL_LINE_THICKNESS);
        }
        
        // 绘制锚点
        for (int i = 0; i < editPoints.size(); i += 3) {
            Vec2d point = editPoints.get(i);
            boolean isSelected = (i == selectedNodeIndex);
            Color pointColor = isSelected ? selectedColor : layerColor;
            double pointSize = isSelected ? SELECTED_POINT_SIZE : POINT_SIZE;
            adapter.drawCircle(point, pointSize, pointColor, true);
        }
        
        // 绘制控制点
        for (int i = 1; i < editPoints.size() - 1; i++) {
            if (i % 3 != 0) {
                Vec2d point = editPoints.get(i);
                boolean isSelected = (i == selectedControlPointIndex);
                Color pointColor = isSelected ? selectedColor : controlColor;
                double pointSize = isSelected ? POINT_SIZE : CONTROL_POINT_SIZE;
                adapter.drawCircle(point, pointSize, pointColor, true);
            }
        }
    }
    
    /**
     * 获取当前图层的颜色
     * 优化：移除不必要的try-catch，直接进行类型检查，正确处理透明度
     */
    private Color getLayerColor() {
        Object styleObj = styleHandler.getFinalStyle();
        if (styleObj instanceof ShapeStyle shapeStyle) {
            // 优先使用描边颜色
            int strokeColor = shapeStyle.getStrokeColor();
            if (strokeColor != 0) {
                return new Color(strokeColor, true); // true 表示包含 alpha 通道
            }
            // 其次使用填充颜色
            int fillColor = shapeStyle.getFillColor();
            if (fillColor != 0) {
                return new Color(fillColor, true); // true 表示包含 alpha 通道
            }
        }
        
        // 回退到默认颜色
        return LINE_COLOR;
    }
    
    @Override
    public Shape getFinalShape() {
        if (editPoints.size() < 4 || editingShape == null) {
            return null;
        }
        
        // 使用 BezierUtils 转换编辑点为贝塞尔曲线数据
        BezierUtils.BezierData bezierData = BezierUtils.convertPointsToCurveData(editPoints, false);
        
        if (bezierData.getAnchors().size() >= 2) {
            // 修复：bezierData.getControls()现在直接返回List<Vec2d[]>，无需转换
            BezierCurveShape curve = new BezierCurveShape(bezierData.getAnchors(), 
                                                       bezierData.getControls(), 
                                                       bezierData.shouldClose());
            ShapeStyle style = (ShapeStyle) styleHandler.getFinalStyle();
            if (style != null) {
                curve.setStyle(style);
            }
            LOGGER.debug("编辑模式生成图形: 锚点数={}, 控制点数={}", 
                       bezierData.getAnchors().size(), bezierData.getControls().size());
            return curve;
        }
        
        return null;
    }
    
    @Override
    public void reset() {
        editPoints.clear();
        isCurveSegment.clear();
        editingShape = null;
        selectedNodeIndex = -1;
        selectedControlPointIndex = -1;
        isDraggingNode = false;
        isDraggingControlPoint = false;
        dragStartPoint = null;
        isControlPointSymmetric = true;
        LOGGER.debug("编辑模式状态已重置");
    }
    
    @Override
    public String getStatusMessage() {
        if (editingShape == null) {
            return "选择要编辑的图形";
        } else if (selectedNodeIndex != -1) {
            return "拖动调整锚点位置";
        } else if (selectedControlPointIndex != -1) {
            return "拖动调整控制点位置";
        } else {
            return "点击选择节点，拖动调整位置，Shift+点击转换段类型";
        }
    }
    
    @Override
    public boolean isDrawing() {
        return editingShape != null;
    }
    
    @Override
    public IInteractionStrategy.InteractionResult onKeyDown(int key, DrawingToolContext context) {
        if (key == 27) { // ESC键：取消编辑
            reset();
            context.resetDrawing("ESC键取消编辑");
            return IInteractionStrategy.InteractionResult.CANCEL;
        }
        return IInteractionStrategy.InteractionResult.IGNORED;
    }

}