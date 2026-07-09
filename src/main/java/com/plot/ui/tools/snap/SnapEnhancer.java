package com.plot.ui.tools.snap;

import com.plot.api.geometry.Vec2d;
import com.plot.core.graphics.DrawContext;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.snap.SnapManager;
import com.plot.ui.canvas.CanvasCamera;
import imgui.ImColor;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.utils.ExceptionDebug;

import java.awt.Color;

/**
 * 捕捉增强器 - 通用的CAD级别捕捉功能增强器
 * <p>
 * 为所有绘制和修改工具提供统一的捕捉功能，包括：
 * - 增强的捕捉检测
 * - 丰富的视觉反馈
 * - 多种捕捉类型支持
 * 
 * @author Plot Team
 * @version 1.0
 */
public class SnapEnhancer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SnapEnhancer.class);
    
    // ====== 捕捉状态 ======
    private boolean isSnapping = false;
    private Vec2d snapPoint = null;
    private com.plot.core.snap.SnapPriorityEvaluator.SnapType currentSnapType = 
        com.plot.core.snap.SnapPriorityEvaluator.SnapType.NONE;
    private final String toolName;
    
    /**
     * 构造函数
     * @param toolName 工具名称，用于日志记录
     */
    public SnapEnhancer(String toolName) {
        this.toolName = toolName != null ? toolName : "Unknown";
    }
    
    /**
     * 执行增强的捕捉检测
     * @param screenPoint 屏幕坐标点
     * @param context 绘制工具上下文
     * @return 捕捉结果
     */
    public SnapResult performEnhancedSnap(Vec2d screenPoint, 
                                        com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy.DrawingToolContext context) {
        if (context == null || context.getSnapHandler() == null) {
            return new SnapResult(screenPoint, false, 
                com.plot.core.snap.SnapPriorityEvaluator.SnapType.NONE);
        }
        
        try {
            // 使用系统的吸附处理器
            Vec2d originalWorldPoint = context.getCamera() != null ? 
                context.getCamera().screenToWorld(screenPoint) : screenPoint;
            Vec2d snappedWorldPoint = context.getSnapHandler().getSnappedWorldPoint(screenPoint, context.getCamera());
            
            // 调试日志
            LOGGER.debug("{}: SnapEnhancer - 原始点={}, 捕捉点={}", 
                        toolName, originalWorldPoint, snappedWorldPoint);
            
            // 检查是否真正发生了捕捉（使用距离检测而不是equals）
            double distance = originalWorldPoint.distance(snappedWorldPoint);
            boolean actuallySnapped = distance > 0.001; // 允许小的浮点误差
            
            if (actuallySnapped) {
                // 直接使用 SnapManager 解析出的真实吸附类型，避免各工具再做一套启发式推断导致显示错乱。
                com.plot.core.snap.SnapPriorityEvaluator.SnapType snapType =
                    SnapManager.getInstance().getLastResolvedSnapType();
                if (snapType == null || snapType == com.plot.core.snap.SnapPriorityEvaluator.SnapType.NONE) {
                    snapType = inferSnapType(originalWorldPoint, snappedWorldPoint);
                }

                // 更新内部状态
                this.isSnapping = true;
                this.snapPoint = snappedWorldPoint;
                this.currentSnapType = snapType;
                
                LOGGER.debug("{}: 检测到捕捉 - 类型={}, 原始位置={}, 捕捉位置={}", 
                           toolName, snapType, originalWorldPoint, snappedWorldPoint);
                           
                return new SnapResult(snappedWorldPoint, true, snapType);
            } else {
                // 清除捕捉状态
                this.isSnapping = false;
                this.snapPoint = null;
                this.currentSnapType = com.plot.core.snap.SnapPriorityEvaluator.SnapType.NONE;
                
                return new SnapResult(snappedWorldPoint, false, 
                    com.plot.core.snap.SnapPriorityEvaluator.SnapType.NONE);
            }
            
        } catch (Exception e) {
            LOGGER.warn("{}: 增强捕捉检测失败: {}", toolName, e.getMessage());
            return new SnapResult(screenPoint, false, 
                com.plot.core.snap.SnapPriorityEvaluator.SnapType.NONE);
        }
    }

    /**
     * 执行增强的捕捉检测（修改工具上下文版本）
     * @param screenPoint 屏幕坐标点
     * @param context 修改工具上下文
     * @return 捕捉结果
     */
    public SnapResult performEnhancedSnap(Vec2d screenPoint,
                                          com.plot.ui.tools.impl.modify.strategy.IModifyStrategy.ModifyToolContext context) {
        if (context == null || context.getSnapHandler() == null) {
            return new SnapResult(screenPoint, false,
                com.plot.core.snap.SnapPriorityEvaluator.SnapType.NONE);
        }

        try {
            Vec2d originalWorldPoint = context.getCamera() != null ?
                context.getCamera().screenToWorld(screenPoint) : screenPoint;
            Vec2d snappedWorldPoint = context.getSnapHandler().getSnappedWorldPoint(screenPoint, context.getCamera());

            LOGGER.debug("{}: SnapEnhancer(M) - 原始点={}, 捕捉点={}",
                        toolName, originalWorldPoint, snappedWorldPoint);

            double distance = originalWorldPoint.distance(snappedWorldPoint);
            boolean actuallySnapped = distance > 0.001;

            if (actuallySnapped) {
                var snapType = SnapManager.getInstance().getLastResolvedSnapType();
                if (snapType == null || snapType == com.plot.core.snap.SnapPriorityEvaluator.SnapType.NONE) {
                    snapType = inferSnapType(originalWorldPoint, snappedWorldPoint);
                }
                this.isSnapping = true;
                this.snapPoint = snappedWorldPoint;
                this.currentSnapType = snapType;
                return new SnapResult(snappedWorldPoint, true, snapType);
            } else {
                this.isSnapping = false;
                this.snapPoint = null;
                this.currentSnapType = com.plot.core.snap.SnapPriorityEvaluator.SnapType.NONE;
                return new SnapResult(snappedWorldPoint, false,
                    com.plot.core.snap.SnapPriorityEvaluator.SnapType.NONE);
            }
        } catch (Exception e) {
            LOGGER.warn("{}: 增强捕捉检测失败(修改工具): {}", toolName, e.getMessage());
            return new SnapResult(screenPoint, false,
                com.plot.core.snap.SnapPriorityEvaluator.SnapType.NONE);
        }
    }
    
    /**
     * 渲染捕捉指示器 - DrawContext版本
     * @param context 绘制上下文
     */
    public void renderSnapIndicator(DrawContext context) {
        if (!isSnapping || snapPoint == null) {
            return;
        }
        
        // 根据捕捉类型使用不同的视觉效果（统一样式）
        Color indicatorColor = SnapVisualStyle.colorFor(currentSnapType);
        float indicatorSize = SnapVisualStyle.ringSizeFor(currentSnapType) * getMarkerScale();

        renderConstraintGuide(context);

        // 渲染主要的吸附指示器
        context.drawCircle(snapPoint, indicatorSize, indicatorColor);
        
        // 渲染捕捉类型的特殊标记
        renderSnapTypeIndicator(context, snapPoint, currentSnapType);
    }
    
    /**
     * 渲染捕捉指示器 - ImDrawList版本
     * @param drawList ImGui绘制列表
     * @param camera 相机引用
     */
    public void renderSnapIndicator(ImDrawList drawList, CanvasCamera camera) {
        if (!isSnapping || snapPoint == null || camera == null) {
            return;
        }
        
        // 根据捕捉类型使用不同的颜色和大小（统一样式）
        Color indicatorColor = SnapVisualStyle.colorFor(currentSnapType);
        float indicatorSize = SnapVisualStyle.ringSizeFor(currentSnapType) * getMarkerScale();
        
        int snapColor = ImColor.rgba(indicatorColor.getRed(), indicatorColor.getGreen(), 
                                   indicatorColor.getBlue(), 200);

        renderConstraintGuideImGui(drawList, camera);

        // 渲染当前吸附点的指示器环
        Vec2d screenSnapPoint = camera.worldToScreen(snapPoint);
        drawList.addCircle((float) screenSnapPoint.x, (float) screenSnapPoint.y, 
                          indicatorSize * 0.7f, snapColor, 16, 2.0f);
        
        // 渲染捕捉类型的特殊标记（ImGui版本）
        renderSnapTypeIndicatorImGui(drawList, camera, snapPoint, currentSnapType);
    }
    
    /**
     * 重置捕捉状态
     */
    public void reset() {
        isSnapping = false;
        snapPoint = null;
        currentSnapType = com.plot.core.snap.SnapPriorityEvaluator.SnapType.NONE;
        LOGGER.debug("{}: 捕捉状态已重置", toolName);
    }

    // ====== 私有辅助方法 ======
    
    /**
     * 推断捕捉类型（简化版本）
     */
    private com.plot.core.snap.SnapPriorityEvaluator.SnapType inferSnapType(Vec2d original, Vec2d snapped) {
        // 这里可以根据实际情况进行更精确的类型推断
        // 暂时使用启发式方法
        double distance = original.distance(snapped);
        if (distance < 5.0) {
            return com.plot.core.snap.SnapPriorityEvaluator.SnapType.NEAREST_POINT;
        } else if (distance < 15.0) {
            return com.plot.core.snap.SnapPriorityEvaluator.SnapType.END_POINT;
        } else {
            return com.plot.core.snap.SnapPriorityEvaluator.SnapType.GRID_POINT;
        }
    }
    
    /**
     * 根据捕捉类型获取指示器颜色
     */
    @SuppressWarnings("unused")
    private Color getSnapIndicatorColor(com.plot.core.snap.SnapPriorityEvaluator.SnapType snapType) {
        return SnapVisualStyle.colorFor(snapType);
    }
    
    /**
     * 根据捕捉类型获取指示器大小
     */
    @SuppressWarnings("unused")
    private float getSnapIndicatorSize(com.plot.core.snap.SnapPriorityEvaluator.SnapType snapType) {
        return SnapVisualStyle.ringSizeFor(snapType);
    }
    
    /**
     * 渲染捕捉类型的特殊标记 - DrawContext版本
     */
    private void renderSnapTypeIndicator(DrawContext context, Vec2d point, 
                                       com.plot.core.snap.SnapPriorityEvaluator.SnapType snapType) {
        float size = SnapVisualStyle.MARKER_SIZE * getMarkerScale();
        Color markerColor = SnapVisualStyle.colorFor(snapType);
        switch (snapType) {
            case END_POINT -> context.drawCircleFilled(point, size, markerColor);
            case MID_POINT -> context.drawCircleFilled(point, size * 0.8f, markerColor);
            case CENTER_POINT, CENTROID -> {
                // 十字标记
                context.drawLine(new Vec2d(point.x - size, point.y), 
                               new Vec2d(point.x + size, point.y), markerColor);
                context.drawLine(new Vec2d(point.x, point.y - size), 
                               new Vec2d(point.x, point.y + size), markerColor);
            }
            case INTERSECTION, VERTEX -> {
                // X标记
                context.drawLine(new Vec2d(point.x - size, point.y - size), 
                               new Vec2d(point.x + size, point.y + size), markerColor);
                context.drawLine(new Vec2d(point.x - size, point.y + size), 
                               new Vec2d(point.x + size, point.y - size), markerColor);
            }
            case PERPENDICULAR, HORIZONTAL, VERTICAL -> {
                // 垂直线标记
                context.drawLine(new Vec2d(point.x, point.y - size), 
                               new Vec2d(point.x, point.y + size), markerColor);
                context.drawLine(new Vec2d(point.x - size * 0.5, point.y + size * 0.5), 
                               new Vec2d(point.x + size * 0.5, point.y + size * 0.5), markerColor);
            }
            case TANGENT -> context.drawCircle(point, size * 0.6f, markerColor);
            case QUADRANT, CONTROL_POINT -> context.drawCircleFilled(point, size * 0.5f, markerColor);
            case NEAREST_POINT, GRID_POINT -> context.drawCircleFilled(point, size * 0.3f, markerColor);
            case EXTENSION, PARALLEL -> context.drawLine(new Vec2d(point.x - size * 0.5, point.y),
                           new Vec2d(point.x + size * 0.5, point.y), markerColor);
            case NONE -> { /* 无标记 */ }
            default -> context.drawCircleFilled(point, size * 0.4f, SnapVisualStyle.colorFor(com.plot.core.snap.SnapPriorityEvaluator.SnapType.NONE));
        }
    }
    
    /**
     * 渲染捕捉类型的特殊标记 - ImDrawList版本
     */
    private void renderSnapTypeIndicatorImGui(ImDrawList drawList, CanvasCamera camera, Vec2d point, 
                                            com.plot.core.snap.SnapPriorityEvaluator.SnapType snapType) {
        Vec2d screenPoint = camera.worldToScreen(point);
        float x = (float) screenPoint.x;
        float y = (float) screenPoint.y;
        float size = SnapVisualStyle.MARKER_SIZE * getMarkerScale();
        
        switch (snapType) {
            case END_POINT -> {
                int color = SnapVisualStyle.imGuiColorFor(com.plot.core.snap.SnapPriorityEvaluator.SnapType.END_POINT, 255);
                drawList.addRectFilled(x - size, y - size, x + size, y + size, color);
            }
            case MID_POINT -> {
                int color = SnapVisualStyle.imGuiColorFor(com.plot.core.snap.SnapPriorityEvaluator.SnapType.MID_POINT, 255);
                drawList.addTriangleFilled(x, y - size, x - size, y + size, x + size, y + size, color);
            }
            case CENTER_POINT, CENTROID -> {
                int color = SnapVisualStyle.imGuiColorFor(com.plot.core.snap.SnapPriorityEvaluator.SnapType.CENTER_POINT, 255);
                drawList.addLine(x - size, y, x + size, y, color, 2.0f);
                drawList.addLine(x, y - size, x, y + size, color, 2.0f);
            }
            case INTERSECTION, VERTEX -> {
                int color = SnapVisualStyle.imGuiColorFor(com.plot.core.snap.SnapPriorityEvaluator.SnapType.INTERSECTION, 255);
                drawList.addLine(x - size, y - size, x + size, y + size, color, 2.0f);
                drawList.addLine(x - size, y + size, x + size, y - size, color, 2.0f);
            }
            case PERPENDICULAR, HORIZONTAL, VERTICAL -> {
                int color = SnapVisualStyle.imGuiColorFor(com.plot.core.snap.SnapPriorityEvaluator.SnapType.PERPENDICULAR, 255);
                drawList.addLine(x, y - size, x, y + size, color, 2.0f);
                drawList.addLine(x - size * 0.5f, y + size * 0.5f, x + size * 0.5f, y + size * 0.5f, color, 2.0f);
            }
            case TANGENT -> {
                int color = SnapVisualStyle.imGuiColorFor(com.plot.core.snap.SnapPriorityEvaluator.SnapType.TANGENT, 255);
                drawList.addCircle(x, y, size * 0.6f, color, 16, 2.0f);
            }
            case QUADRANT, CONTROL_POINT -> {
                int color = SnapVisualStyle.imGuiColorFor(com.plot.core.snap.SnapPriorityEvaluator.SnapType.CONTROL_POINT, 255);
                drawList.addRectFilled(x - size * 0.5f, y - size * 0.5f, x + size * 0.5f, y + size * 0.5f, color);
            }
            case NEAREST_POINT, GRID_POINT -> {
                int color = SnapVisualStyle.imGuiColorFor(com.plot.core.snap.SnapPriorityEvaluator.SnapType.GRID_POINT, 255);
                drawList.addCircleFilled(x, y, size * 0.3f, color);
            }
            case EXTENSION, PARALLEL -> {
                int color = SnapVisualStyle.imGuiColorFor(com.plot.core.snap.SnapPriorityEvaluator.SnapType.EXTENSION, 255);
                drawList.addLine(x - size * 0.5f, y, x + size * 0.5f, y, color, 2.0f);
            }
            case NONE -> { /* 无标记 */ }
            default -> {
                int color = ImColor.rgba(255, 255, 0, 255);
                drawList.addCircleFilled(x, y, size * 0.4f, color);
            }
        }
    }

    private void renderConstraintGuide(DrawContext context) {
        if (currentSnapType != com.plot.core.snap.SnapPriorityEvaluator.SnapType.EXTENSION) {
            return;
        }
        Vec2d[] guide = resolveExtensionGuide();
        if (guide == null) {
            return;
        }
        context.drawDashedLine(guide[0], guide[1], withAlpha(SnapVisualStyle.colorFor(currentSnapType), 180));
    }

    private void renderConstraintGuideImGui(ImDrawList drawList, CanvasCamera camera) {
        if (currentSnapType != com.plot.core.snap.SnapPriorityEvaluator.SnapType.EXTENSION || drawList == null || camera == null) {
            return;
        }
        Vec2d[] guide = resolveExtensionGuide();
        if (guide == null) {
            return;
        }

        Vec2d screenStart = camera.worldToScreen(guide[0]);
        Vec2d screenEnd = camera.worldToScreen(guide[1]);
        int color = SnapVisualStyle.imGuiColorFor(currentSnapType, 180);
        drawDashedLineImGui(drawList, screenStart, screenEnd, color, 8.0f, 4.0f, 1.8f);
    }

    private Vec2d[] resolveExtensionGuide() {
        if (snapPoint == null) {
            return null;
        }

        Shape sourceShape = SnapManager.getInstance().getLastResolvedSnapSourceShape();
        if (!(sourceShape instanceof LineShape lineShape)) {
            return null;
        }

        Vec2d start = lineShape.getStart();
        Vec2d end = lineShape.getEnd();
        if (start == null || end == null) {
            return null;
        }

        Vec2d anchor = start.distance(snapPoint) <= end.distance(snapPoint) ? start : end;
        if (anchor.distance(snapPoint) <= 1.0e-6) {
            return null;
        }
        return new Vec2d[] { anchor, snapPoint };
    }

    private void drawDashedLineImGui(ImDrawList drawList, Vec2d start, Vec2d end, int color,
                                     float dashLength, float gapLength, float thickness) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length <= 1.0e-6) {
            return;
        }

        double ux = dx / length;
        double uy = dy / length;
        boolean drawing = true;
        double current = 0.0;

        while (current < length) {
            double next = Math.min(length, current + (drawing ? dashLength : gapLength));
            if (drawing) {
                float x1 = (float) (start.x + ux * current);
                float y1 = (float) (start.y + uy * current);
                float x2 = (float) (start.x + ux * next);
                float y2 = (float) (start.y + uy * next);
                drawList.addLine(x1, y1, x2, y2, color, thickness);
            }
            current = next;
            drawing = !drawing;
        }
    }

    private Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private float getMarkerScale() {
        try {
            float configured = SnapManager.getInstance().getMarkerSize();
            float scale = configured / 5.0f;
            return Math.max(0.8f, Math.min(2.0f, scale));
        } catch (Exception e) {
            ExceptionDebug.log("SnapEnhancer: compute marker scale", e);
            return 1.0f;
        }
    }
    
    /**
     * 捕捉结果封装类
     */
    public static class SnapResult {
        public final Vec2d point;
        public final boolean snapped;
        public final com.plot.core.snap.SnapPriorityEvaluator.SnapType snapType;
        
        public SnapResult(Vec2d point, boolean snapped, 
                         com.plot.core.snap.SnapPriorityEvaluator.SnapType snapType) {
            this.point = point;
            this.snapped = snapped;
            this.snapType = snapType;
        }
    }
}