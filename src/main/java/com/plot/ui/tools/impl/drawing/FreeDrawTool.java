package com.plot.ui.tools.impl.drawing;

import com.plot.api.geometry.Vec2d;
import com.plot.api.state.IAppState;
import com.plot.api.snap.ISnapManager;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.graphics.DrawContext;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.model.Shape;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.component.Icons;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import com.plot.ui.tools.snap.SnapEnhancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import imgui.ImDrawList;
import imgui.ImColor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自由绘制工具 - 优化的策略模式版本
 * <p>
 * 支持自由绘制路径，使用拖动交互模式收集路径点
 * 包含RDP算法优化的路径简化功能
 * <p>
 * 核心改进：
 * - 正确使用DRAG_AND_DROP交互策略
 * - 自定义路径收集逻辑
 * - 优化的预览渲染
 * - 智能路径简化
 */
public class FreeDrawTool extends DrawingTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(FreeDrawTool.class);

    // ====== 配置常量 ======
    private static final String TOOL_ID = "freedraw";
    private static final float DEFAULT_SMOOTHING = 0.3f;
    private static final float DEFAULT_SIMPLIFY_TOLERANCE = 2.0f;
    private static final float MIN_DISTANCE_BASE = 1.0f;
    private static final float MIN_DISTANCE_FACTOR = 19.0f;
    private static final double RDP_TOLERANCE_SCALE_FACTOR = 0.5;
    private static final int MIN_POINTS_FOR_RDP = 50;
    
    // ====== 配置状态 ======
    private float smoothing = DEFAULT_SMOOTHING;
    private float simplifyTolerance = DEFAULT_SIMPLIFY_TOLERANCE;
    
    // ====== 核心状态 ======
    private final List<Vec2d> currentPath = new ArrayList<>();
    // 统一捕捉可视化
    private final SnapEnhancer snapEnhancer = new SnapEnhancer("FreeDrawTool");

    /**
     * 依赖注入构造函数（推荐）
     */
    public FreeDrawTool(IAppState appState, ISnapManager snapManager) {
        super("freedraw", "自由绘制", Icons.FREEDRAW_IDENTIFIER, "自由绘制路径", 
              appState, snapManager, InteractionType.DRAG_AND_DROP);
        
        // 订阅工具配置事件
        eventBus.subscribe(ToolConfigEvent.class, event -> {
            ToolConfigEvent configEvent = (ToolConfigEvent) event;
            if (TOOL_ID.equals(configEvent.getToolId())) {
                handleToolConfig(configEvent);
            }
        });
        
        initializeFreeDrawStrategy();
    }



    /**
     * 初始化自由绘制专用策略
     */
    private void initializeFreeDrawStrategy() {
        // 创建自定义的自由绘制策略来替换默认的拖放策略
        FreeDrawInteractionStrategy customStrategy = new FreeDrawInteractionStrategy();
        // 直接替换内部策略（hack方式，但为了保持架构一致性）
        try {
            java.lang.reflect.Field strategyField = DrawingTool.class.getDeclaredField("interactionStrategy");
            strategyField.setAccessible(true);
            strategyField.set(this, customStrategy);
        } catch (Exception e) {
            LOGGER.warn("无法替换交互策略，将使用默认行为: {}", e.getMessage());
        }
    }

    // ====== 实现抽象方法 ======
    
    @Override
    protected Shape createShape(Vec2d startPoint, Vec2d endPoint) {
        if (currentPath.isEmpty()) {
            LOGGER.warn("FreeDrawTool.createShape: 路径为空");
            return null;
        }

        // 复制路径点
        List<Vec2d> pathPoints = new ArrayList<>(currentPath);
        
        // 应用平滑处理
        if (smoothing > 0 && pathPoints.size() >= 3) {
            pathPoints = smoothPoints(pathPoints);
            LOGGER.debug("应用平滑后点数: {}", pathPoints.size());
        }
        
        // 应用简化处理
        if (simplifyTolerance > 0.1f && pathPoints.size() >= MIN_POINTS_FOR_RDP) {
            pathPoints = simplifyPointsByDistance(pathPoints);
            LOGGER.debug("应用简化后点数: {}", pathPoints.size());
        }
        
        // 最后应用RDP算法优化
        if (pathPoints.size() >= MIN_POINTS_FOR_RDP) {
            double tolerance = getAdjustedTolerance();
            pathPoints = simplifyPath(pathPoints, tolerance);
            LOGGER.debug("应用RDP算法后点数: {}", pathPoints.size());
        }
        
        if (pathPoints.size() < 2) {
            LOGGER.warn("FreeDrawTool: 简化后路径点不足");
            return null;
        }
        
        FreeDrawPath path = new FreeDrawPath(pathPoints);
        
        // 应用样式
        ShapeStyle style = getStyleHandler().getFinalStyle();
        if (style != null) {
            path.setStyle(style);
        }
        
        return path;
    }

    /**
     * 获取动态调整的容差值
     */
    private double getAdjustedTolerance() {
        // 根据相机缩放调整容差
        double zoom = getCamera() != null ? getCamera().getZoom() : 1.0;
        return simplifyTolerance * RDP_TOLERANCE_SCALE_FACTOR / Math.max(0.1, zoom);
    }

    /**
     * 使用RDP算法简化路径
     */
    private List<Vec2d> simplifyPath(List<Vec2d> points, double tolerance) {
        if (points.size() <= 2) return new ArrayList<>(points);
        
        Set<Integer> keepIndices = new HashSet<>();
        keepIndices.add(0);
        keepIndices.add(points.size() - 1);
        
        rdpRecursive(points, 0, points.size() - 1, tolerance, keepIndices);
        
        List<Vec2d> simplified = new ArrayList<>();
        List<Integer> sortedIndices = keepIndices.stream().sorted().toList();
        for (int index : sortedIndices) {
            simplified.add(points.get(index));
        }
        
        return simplified;
    }

    /**
     * RDP算法递归实现
     */
    private void rdpRecursive(List<Vec2d> points, int start, int end, double tolerance, Set<Integer> keepIndices) {
        if (end - start <= 1) return;
        
        double maxDistance = 0;
        int maxIndex = -1;
        
        Vec2d startPoint = points.get(start);
        Vec2d endPoint = points.get(end);
        
        for (int i = start + 1; i < end; i++) {
            double distance = perpendicularDistance(points.get(i), startPoint, endPoint);
            if (distance > maxDistance) {
                maxDistance = distance;
                maxIndex = i;
            }
        }
        
        if (maxDistance > tolerance && maxIndex != -1) {
            keepIndices.add(maxIndex);
            rdpRecursive(points, start, maxIndex, tolerance, keepIndices);
            rdpRecursive(points, maxIndex, end, tolerance, keepIndices);
        }
    }

    /**
     * 计算点到线段的垂直距离
     */
    private double perpendicularDistance(Vec2d point, Vec2d lineStart, Vec2d lineEnd) {
        double dx = lineEnd.x - lineStart.x;
        double dy = lineEnd.y - lineStart.y;
        
        if (Math.abs(dx) < 1e-10 && Math.abs(dy) < 1e-10) {
            return point.distance(lineStart);
        }
        
        double t = ((point.x - lineStart.x) * dx + (point.y - lineStart.y) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));
        
        double projX = lineStart.x + t * dx;
        double projY = lineStart.y + t * dy;
        
        return Math.sqrt((point.x - projX) * (point.x - projX) + (point.y - projY) * (point.y - projY));
    }

    /**
     * 平滑路径点
     * 使用移动平均算法对点进行平滑处理
     */
    private List<Vec2d> smoothPoints(List<Vec2d> input) {
        if (input.size() < 3) return new ArrayList<>(input);
        
        List<Vec2d> smoothed = new ArrayList<>(input.size());
        smoothed.add(input.getFirst()); // 添加第一个点
        
        int window = 1 + (int)(smoothing * 4);
        for (int i = 1; i < input.size() - 1; i++) {
            double sumX = 0, sumY = 0;
            int start = Math.max(0, i - window);
            int end = Math.min(input.size(), i + window + 1);
            int count = end - start;
            
            for (int j = start; j < end; j++) {
                Vec2d p = input.get(j);
                sumX += p.x;
                sumY += p.y;
            }
            smoothed.add(new Vec2d(sumX / count, sumY / count));
        }
        smoothed.add(input.getLast()); // 添加最后一个点
        return smoothed;
    }

    /**
     * 基于距离阈值简化路径点
     */
    private List<Vec2d> simplifyPointsByDistance(List<Vec2d> input) {
        if (input.size() < 3) return new ArrayList<>(input);
        
        List<Vec2d> simplified = new ArrayList<>();
        simplified.add(input.getFirst()); // 添加第一个点
        
        Vec2d prevPoint = input.getFirst();
        for (int i = 1; i < input.size() - 1; i++) {
            Vec2d currentPoint = input.get(i);
            if (prevPoint.distance(currentPoint) >= simplifyTolerance) {
                simplified.add(currentPoint);
                prevPoint = currentPoint;
            }
        }
        
        simplified.add(input.getLast()); // 添加最后一个点
        
        return simplified;
    }

    @Override
    protected void renderPreview(DrawContext context) {
        // 始终渲染捕捉指示器（即使尚未开始绘制）
        snapEnhancer.renderSnapIndicator(context);
        if (!shouldShowPreview() || currentPath.isEmpty()) return;
        
        LOGGER.debug("FreeDrawTool: 渲染预览路径，点数: {}", currentPath.size());
        
        // 使用预览样式渲染
        ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
        Color lineColor = previewStyle != null ? previewStyle.getLineColor() :
            toColor(ThemeManager.getInstance().getCurrentTheme().accent, 255);
        
        // 绘制路径
        for (int i = 0; i < currentPath.size() - 1; i++) {
            context.drawLine(currentPath.get(i), currentPath.get(i + 1), lineColor);
        }
    }

    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        // 始终渲染捕捉指示器（即使尚未开始绘制）
        snapEnhancer.renderSnapIndicator(drawList, camera);
        if (!shouldShowPreview() || currentPath.isEmpty() || camera == null) return;
        
        LOGGER.debug("FreeDrawTool: ImGui渲染预览路径，点数: {}", currentPath.size());
        
        // 使用预览样式渲染
        ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
        Color lineColor = previewStyle != null ? previewStyle.getLineColor() :
            toColor(ThemeManager.getInstance().getCurrentTheme().accent, 255);
        int imguiColor = ImColor.rgba(lineColor.getRed(), lineColor.getGreen(), 
                                    lineColor.getBlue(), lineColor.getAlpha());
        
        // 绘制路径
        for (int i = 0; i < currentPath.size() - 1; i++) {
            Vec2d start = camera.worldToScreen(currentPath.get(i));
            Vec2d end = camera.worldToScreen(currentPath.get(i + 1));
            
            drawList.addLine((float)start.x, (float)start.y, 
                           (float)end.x, (float)end.y, imguiColor, 2.0f);
        }
    }

    /**
     * 重写shouldShowPreview方法
     * 自由绘制的预览不依赖previewShape，而是直接渲染currentPath
     */
    @Override
    protected boolean shouldShowPreview() {
        boolean shouldShow = getCurrentState() == ToolState.DRAWING && !currentPath.isEmpty();
        LOGGER.debug("FreeDrawTool: shouldShowPreview() = {}, state: {}, pathSize: {}", 
                    shouldShow, getCurrentState(), currentPath.size());
        return shouldShow;
    }

    /**
     * 处理工具配置事件
     */
    private void handleToolConfig(ToolConfigEvent event) {
                        updateConfig(event.getOptionName(), String.valueOf(event.getValue()));
    }

    private static Color toColor(int color, int alpha) {
        return new Color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, alpha);
    }

    @Override
    public void updateConfig(String key, String value) {
        try {
            switch (key) {
                case "smoothing" -> {
                    smoothing = Float.parseFloat(value);
                    LOGGER.debug("更新平滑度: {}", smoothing);
                }
                case "simplify" -> {
                    simplifyTolerance = Float.parseFloat(value);
                    LOGGER.debug("更新简化阈值: {}", simplifyTolerance);
                }
            }
        } catch (NumberFormatException e) {
            LOGGER.error("无效的配置值: key={}, value={}", key, value);
        }
    }

    @Override
    protected IInteractionStrategy createStrategy(InteractionType type) {
        // 返回自定义自由绘制策略
        return new FreeDrawInteractionStrategy();
    }

    // ====== 内部自由绘制交互策略 ======
    
    /**
     * 自由绘制专用交互策略
     * 扩展标准拖放策略以支持路径收集
     */
    private class FreeDrawInteractionStrategy implements IInteractionStrategy {
        private boolean isDrawing = false;
        private Vec2d lastPoint = null;
        private Shape finalShape = null;

        @Override
        public InteractionResult onMouseDown(Vec2d pos, int button, DrawingToolContext context) {
            if (button != 0) { // 只响应左键
                return InteractionResult.IGNORED;
            }
            
            try {
                // 开始新的路径
                currentPath.clear();
                // 使用增强吸附，确保指示器与类型状态一致
                SnapEnhancer.SnapResult snap = snapEnhancer.performEnhancedSnap(pos, context);
                Vec2d snappedPoint = snap.point;
                currentPath.add(snappedPoint);
                lastPoint = snappedPoint;
                isDrawing = true;
                finalShape = null;
                
                // 设置工具状态
                context.setToolState(com.plot.ui.tools.impl.drawing.DrawingTool.ToolState.DRAWING);
                
                // 触发重绘以显示起始点
                FreeDrawTool.this.markDirty();
                
                return InteractionResult.CONTINUE;
                
            } catch (Exception e) {
                LOGGER.error("自由绘制开始失败: {}", e.getMessage(), e);
                reset();
                return InteractionResult.CANCEL;
            }
        }

        @Override
        public InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context) {
            // 始终更新增强吸附状态，未开始绘制时也显示指示器
            try { snapEnhancer.performEnhancedSnap(pos, context); } catch (Exception ignored) {}
            if (!isDrawing) {
                return InteractionResult.CONTINUE; // 触发重绘以显示吸附指示器
            }
            
            try {
                Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
                
                // 避免重复添加相同位置的点（动态最小距离）
                double minDistance = MIN_DISTANCE_BASE + smoothing * MIN_DISTANCE_FACTOR;
                if (lastPoint == null || lastPoint.distance(snappedPoint) > minDistance) {
                    currentPath.add(snappedPoint);
                    lastPoint = snappedPoint;
                    
                    // 触发重绘以显示实时预览
                    FreeDrawTool.this.markDirty();
                }
                
                return InteractionResult.CONTINUE;
                
            } catch (Exception e) {
                LOGGER.error("自由绘制移动失败: {}", e.getMessage(), e);
                return InteractionResult.CANCEL;
            }
        }
    
    @Override
        public InteractionResult onMouseUp(Vec2d pos, int button, DrawingToolContext context) {
            if (button != 0 || !isDrawing) {
                return InteractionResult.IGNORED;
            }
            
            try {
                // 添加最后一个点
                Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
                double minDistance = MIN_DISTANCE_BASE + smoothing * MIN_DISTANCE_FACTOR;
                if (lastPoint == null || lastPoint.distance(snappedPoint) > minDistance) {
                    currentPath.add(snappedPoint);
                }
                
                // 检查路径是否有效
                if (currentPath.size() < 2) {
                    reset();
                    return InteractionResult.CANCEL;
                }
                
                // 创建最终图形（使用第一个和最后一个点作为createShape的参数）
                finalShape = context.getShapeFactory().createShape(
                    currentPath.getFirst(), 
                    currentPath.getLast()
                );
                
                if (finalShape != null) {
                    // 应用最终样式
                    context.getStyleHandler().applyFinalStyle(finalShape);
                    isDrawing = false;
                    return InteractionResult.COMPLETE;
                } else {
                    reset();
                    return InteractionResult.CANCEL;
                }
                
            } catch (Exception e) {
                LOGGER.error("自由绘制完成失败: {}", e.getMessage(), e);
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
            isDrawing = false;
            lastPoint = null;
            finalShape = null;
            currentPath.clear();
        }

        @Override
        public String getStrategyName() {
            return "自由绘制模式";
        }
    
    @Override
        public String getStrategyDescription() {
            return "自由绘制工具拖放交互策略";
        }
        
        @Override
        public List<Vec2d> getControlPoints() {
            return new ArrayList<>(currentPath);
        }
        
        @Override
        public Vec2d getCurrentMousePoint() {
            return lastPoint;
        }
    }
} 