package com.masterplanner.ui.tools.impl.drawing;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.state.IAppState;
import com.masterplanner.api.snap.ISnapManager;
import com.masterplanner.core.geometry.shapes.ArcShape;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.model.Shape;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.tool.ToolConfigEvent;
import com.masterplanner.ui.canvas.CanvasCamera;
import com.masterplanner.ui.component.Icons;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import imgui.ImDrawList;
import imgui.ImColor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 弧形工具 - 优化的策略模式版本
 * 
 * 支持三种绘制模式：
 * 1. 起点-终点-圆弧点：点击第一个点作为圆弧起点，点击第二个点为圆弧的终点，点击第三个点为圆弧上的点完成圆弧绘制
 * 2. 经过点：点击第一个点作为圆弧起点，点击第二个点为圆弧上的点，点击第三个点为圆弧的终点完成绘制
 * 3. 圆心-起点-终点：点击第一个点作为圆弧所在圆心点，第二个点作为圆弧起点，第三个点为圆弧终点完成绘制
 */
public class ArcTool extends DrawingTool {
    // 统一捕捉可视化
    private final com.masterplanner.ui.tools.snap.SnapEnhancer snapEnhancer =
            new com.masterplanner.ui.tools.snap.SnapEnhancer("ArcTool");
    private static final Logger LOGGER = LoggerFactory.getLogger(ArcTool.class);

    // ====== 绘制模式枚举 ======
    public enum ArcMode {
        START_END_DIRECTION("起点-终点-圆弧点", "点击设置起点、终点、圆弧上的点"),
        THROUGH_POINT("经过点", "点击设置起点、经过点、终点"),
        CENTER_START_END("圆心-起点-终点", "点击设置圆心、起点、终点");
        
        private final String displayName;
        private final String description;
        
        ArcMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    // ====== 配置常量 ======
    public static final String CONFIG_KEY_MODE = "mode";
    public static final String CONFIG_MODE_START_END_DIRECTION = "start_end_direction";
    public static final String CONFIG_MODE_THROUGH_POINT = "through_point";
    public static final String CONFIG_MODE_CENTER_START_END = "center_start_end";
    public static final String CONFIG_KEY_SEGMENTS = "segments";
    public static final String CONFIG_KEY_DIRECTION = "direction";
    public static final String CONFIG_KEY_ANGLE_SNAP = "angle_snap";
    public static final String CONFIG_KEY_SNAP_ANGLE = "snap_angle";

    // ====== 渲染常量 ======
    private static final double MIN_RADIUS = 0.1;
    private static final int DEFAULT_SEGMENTS = 32;
    private static final int MIN_SEGMENTS = 8;
    private static final int MAX_SEGMENTS = 72;
    private static final double ANGLE_SNAP_TOLERANCE = 1e-10;
    
    // ====== 渲染颜色常量 ======
    private static final Color PREVIEW_COLOR = new Color(255, 255, 255, 200);
    private static final Color CONTROL_POINT_COLOR = Color.YELLOW;
    private static final Color CONNECTOR_LINE_COLOR = new Color(128, 128, 255);
    private static final float CONTROL_POINT_SIZE = 6.0f;
    
    // ====== 状态消息映射 ======
    private static final Map<ArcMode, List<String>> MODE_STATUS_MESSAGES = Map.of(
        ArcMode.START_END_DIRECTION, List.of("点击设置起点", "点击设置终点", "点击设置圆弧上的点"),
        ArcMode.THROUGH_POINT, List.of("点击设置起点", "点击设置经过点", "点击设置终点"),
        ArcMode.CENTER_START_END, List.of("点击设置圆心", "点击设置起点", "点击设置终点")
    );

    // ====== 核心状态 ======
    private ArcMode currentMode = ArcMode.START_END_DIRECTION;
    private List<Vec2d> controlPoints = new ArrayList<>();
    private ArcShape previewArc;
    private int segments = DEFAULT_SEGMENTS;
    private boolean clockwise = true;
    private boolean angleSnap = false;
    private float snapAngle = 45.0f;
    private final EventBus eventBus = EventBus.getInstance();
    private Vec2d currentMousePoint; // 当前鼠标位置，用于辅助线渲染

    /**
     * 依赖注入构造函数（推荐）
     */
    public ArcTool(IAppState appState, ISnapManager snapManager) {
        super("arc", "弧形", Icons.ARC_IDENTIFIER, "绘制弧形", 
              appState, snapManager, InteractionType.CLICK_AND_CLICK);
        initializeArcTool();
    }

    /**
     * 默认构造函数（兼容性）
     * @deprecated 使用依赖注入构造函数
     */
    @Deprecated
    public ArcTool() {
        super("arc", "弧形", Icons.ARC_IDENTIFIER, "绘制弧形");
        initializeArcTool();
    }
    
    /**
     * 通用初始化逻辑
     */
    private void initializeArcTool() {
        config.setValue(CONFIG_KEY_MODE, CONFIG_MODE_START_END_DIRECTION);
        config.setValue(CONFIG_KEY_SEGMENTS, String.valueOf(segments));
        
        // 使用反射设置自定义交互策略
        try {
            java.lang.reflect.Field strategyField = DrawingTool.class.getDeclaredField("interactionStrategy");
            strategyField.setAccessible(true);
            strategyField.set(this, new ArcInteractionStrategy());
            LOGGER.debug("ArcTool: 自定义交互策略设置成功");
        } catch (Exception e) {
            LOGGER.error("ArcTool: 无法设置自定义交互策略", e);
        }
        
        // 订阅工具配置事件
        eventBus.subscribe(ToolConfigEvent.class, event -> {
            if (event instanceof ToolConfigEvent toolConfigEvent && 
                getId().equals(toolConfigEvent.getToolId())) {
                updateConfig(toolConfigEvent.getOptionName(), String.valueOf(toolConfigEvent.getValue()));
            }
        });
        
        // 更新状态消息
        updateStatusMessageForCurrentMode();
        
        LOGGER.debug("ArcTool 初始化完成，当前模式: {}", currentMode.getDisplayName());
    }

    // ====== 状态管理方法 ======
    
    /**
     * 更新当前模式的状态消息
     */
    private void updateStatusMessageForCurrentMode() {
        List<String> messages = MODE_STATUS_MESSAGES.get(currentMode);
        if (messages != null) {
            int index = Math.min(controlPoints.size(), messages.size() - 1);
            String message = messages.get(index);
            setStatusMessage(message);
            LOGGER.debug("弧形工具状态消息已更新: {}", message);
        } else {
            setStatusMessage("弧形工具就绪");
        }
    }
    
    /**
     * 重置绘制状态
     */
    private void resetDrawingState() {
        controlPoints.clear();
        previewArc = null;
        currentMousePoint = null;
        updateStatusMessageForCurrentMode();
        LOGGER.debug("ArcTool: 重置绘制状态");
    }

    // ====== 实现抽象方法 ======
    
    @Override
    protected Shape createShape(Vec2d startPoint, Vec2d endPoint) {
        // 根据当前模式和控制点创建弧形
        if (previewArc != null) {
            // 使用预览弧的参数创建最终弧形
            ArcShape arc = new ArcShape(previewArc.getCenter(), previewArc.getRadius(), 
                                       previewArc.getStartAngle(), previewArc.getEndAngle());
            arc.setSegments(segments);
            // 移除 setClockwise 调用，方向在创建时确定
            
            // 应用样式
            ShapeStyle style = getStyleHandler().getFinalStyle();
            if (style != null) {
                arc.setStyle(style);
            }
            
            LOGGER.debug("ArcTool 创建弧形: 中心={}, 半径={:.2f}, 起始角度={:.2f}, 结束角度={:.2f}", 
                        previewArc.getCenter(), previewArc.getRadius(), 
                        Math.toDegrees(previewArc.getStartAngle()), Math.toDegrees(previewArc.getEndAngle()));
            return arc;
        }
        
        // 如果没有预览弧，根据模式计算
        if (controlPoints.size() < 3) {
            LOGGER.warn("ArcTool.createShape: 控制点不足，需要至少3个控制点");
            return null;
        }
        
        return createShapeFromControlPoints();
    }
    
    /**
     * 从控制点创建弧形
     */
    private ArcShape createShapeFromControlPoints() {
        if (controlPoints.size() < 3) {
            return null;
        }
        
        try {
            return switch (currentMode) {
                case START_END_DIRECTION -> createStartEndDirectionArc();
                case THROUGH_POINT -> createThroughPointArc();
                case CENTER_START_END -> createCenterStartEndArc();
            };
        } catch (Exception e) {
            LOGGER.error("创建弧形时出错: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 创建起点-终点-圆弧点模式的弧形
     * 使用三点确定圆的几何原理，确保第三个点确实在圆弧上
     */
    private ArcShape createStartEndDirectionArc() {
        // This mode is equivalent to THROUGH_POINT, using the 3rd point as the through point.
        // We can reuse the same robust logic.
        return createThroughPointArc(controlPoints.get(0), controlPoints.get(2), controlPoints.get(1));
    }

    /**
     * 创建经过点模式的弧形
     */
    private ArcShape createThroughPointArc() {
        return createThroughPointArc(controlPoints.get(0), controlPoints.get(1), controlPoints.get(2));
    }

    private ArcShape createThroughPointArc(Vec2d startPoint, Vec2d throughPoint, Vec2d endPoint) {
        double x1 = startPoint.x, y1 = startPoint.y;
        double x2 = throughPoint.x, y2 = throughPoint.y;
        double x3 = endPoint.x, y3 = endPoint.y;
        
        double D = 2 * (x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2));
        
        if (Math.abs(D) < ANGLE_SNAP_TOLERANCE) {
            LOGGER.warn("三点共线，无法确定圆");
            return null;
        }
        
        double centerX = ((x1 * x1 + y1 * y1) * (y2 - y3) + (x2 * x2 + y2 * y2) * (y3 - y1) + (x3 * x3 + y3 * y3) * (y1 - y2)) / D;
        double centerY = ((x1 * x1 + y1 * y1) * (x3 - x2) + (x2 * x2 + y2 * y2) * (x1 - x3) + (x3 * x3 + y3 * y3) * (x2 - x1)) / D;
        Vec2d center = new Vec2d(centerX, centerY);
        
        double radius = Math.max(MIN_RADIUS, center.distance(startPoint));
        double startAngle = Math.atan2(startPoint.y - centerY, startPoint.x - centerX);
        double endAngle = Math.atan2(endPoint.y - centerY, endPoint.x - centerX);
        double throughAngle = Math.atan2(throughPoint.y - centerY, throughPoint.x - centerX);

        double tempStartAngle = (startAngle % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
        double tempEndAngle = (endAngle % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
        double tempThroughAngle = (throughAngle % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);

        boolean isBetween = isAngleBetween(tempThroughAngle, tempStartAngle, tempEndAngle);
        
        if ((!isBetween && clockwise) || (isBetween && !clockwise)) {
            double temp = startAngle;
            startAngle = endAngle;
            endAngle = temp;
        }

        if (angleSnap) {
            endAngle = applyAngleSnap(startAngle, endAngle);
        }
        
        return createArcWithStyle(center, radius, startAngle, endAngle);
    }

    /**
     * 【已修正】创建圆心-起点-终点模式的弧形
     * 方向由鼠标动态决定，忽略全局clockwise设置。
     */
    private ArcShape createCenterStartEndArc() {
        Vec2d center = controlPoints.get(0);
        Vec2d startPoint = controlPoints.get(1);
        Vec2d endPoint = controlPoints.get(2);
        
        double radius = Math.max(MIN_RADIUS, center.distance(startPoint));
        double startRad = Math.atan2(startPoint.y - center.y, startPoint.x - center.x);
        double endRad = Math.atan2(endPoint.y - center.y, endPoint.x - center.x);
        
        // --- 核心逻辑 ---
        // 1. 计算叉积以确定几何方向
        // V1 = 向量(起点 -> 终点)
        Vec2d vec_start_to_end = endPoint.subtract(startPoint);
        // V2 = 向量(起点 -> 圆心)
        Vec2d vec_start_to_center = center.subtract(startPoint);
        double crossProduct = vec_start_to_end.x * vec_start_to_center.y - vec_start_to_end.y * vec_start_to_center.x;

        // 2. 根据叉积符号判断用户意图
        //    - crossProduct > 0: 圆心在弦的左侧，短弧是逆时针 (CCW)
        //    - crossProduct < 0: 圆心在弦的右侧，短弧是顺时针 (CW)
        boolean wantsCounterClockwise = crossProduct > 0;

        // 3. 操纵角度以匹配ArcShape的行为
        //    ArcShape总是逆时针绘制。要让它画顺时针的短弧，就等同于让它画逆时针的长弧。
        //    通过交换起点和终点角度，可以使其绘制长弧。
        if (wantsCounterClockwise) {
            // 意图是CCW（短弧），直接传递角度，ArcShape会正确处理。
        } else {
            // 意图是CW（短弧），等同于CCW（长弧）。
            // 交换角度，让ArcShape绘制长弧。
            double temp = startRad;
            startRad = endRad;
            endRad = temp;
        }
        // --- 逻辑结束 ---

        if (angleSnap) {
            endRad = applyAngleSnap(startRad, endRad);
        }
        
        return createArcWithStyle(center, radius, startRad, endRad);
    }
    
    /**
     * 创建带样式的弧形
     */
    private ArcShape createArcWithStyle(Vec2d center, double radius, double startAngle, double endAngle) {
        ArcShape arc = new ArcShape(center, radius, startAngle, endAngle);
        arc.setSegments(segments);
        // 移除 setClockwise 调用，方向在创建时确定
        
        ShapeStyle style = getStyleHandler().getFinalStyle();
        if (style != null) {
            arc.setStyle(style);
        }
        
        return arc;
    }

    // ====== 预览渲染 ======
    
    @Override
    protected void renderPreview(DrawContext context) {
        // 始终渲染捕捉指示器（即使尚未开始绘制）
        snapEnhancer.renderSnapIndicator(context);

        if (!shouldShowPreview()) {
            return;
        }
        
        // 渲染预览弧形
        if (previewArc != null) {
            context.drawArc(previewArc.getCenter(), previewArc.getRadius(), 
                          previewArc.getStartAngle(), previewArc.getEndAngle(), PREVIEW_COLOR);
        }
        
        // 渲染控制点和辅助线
        renderControlPointsAndGuides(context);
    }

    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        // 始终渲染捕捉指示器（即使尚未开始绘制）
        snapEnhancer.renderSnapIndicator(drawList, camera);

        if (!shouldShowPreview() || camera == null) {
            return;
        }
        
        // 渲染预览弧形
        if (previewArc != null) {
            renderArcImGui(drawList, camera, previewArc);
        }
        
        // 渲染控制点和辅助线
        renderControlPointsAndGuidesImGui(drawList, camera);
    }
    
    // ====== 渲染辅助方法 ======
    
    private void renderControlPointsAndGuides(DrawContext context) {
        // 渲染已有的控制点
        for (Vec2d point : controlPoints) {
            context.drawCircleFilled(point, CONTROL_POINT_SIZE, CONTROL_POINT_COLOR);
        }
        
        // 渲染连接线（根据模式不同而不同）
        if (controlPoints.size() >= 2) {
            renderConnectionLines(context);
        }
        
        // 渲染到鼠标位置的预览线
        if (currentMousePoint != null && !controlPoints.isEmpty() && controlPoints.size() < 3) {
            Vec2d lastPoint = controlPoints.getLast();
            context.drawLine(lastPoint, currentMousePoint, CONNECTOR_LINE_COLOR);
        }
    }
    
    private void renderControlPointsAndGuidesImGui(ImDrawList drawList, CanvasCamera camera) {
        int controlColor = ImColor.rgba(CONTROL_POINT_COLOR.getRed(), CONTROL_POINT_COLOR.getGreen(), 
                                       CONTROL_POINT_COLOR.getBlue(), 200);
        int lineColor = ImColor.rgba(CONNECTOR_LINE_COLOR.getRed(), CONNECTOR_LINE_COLOR.getGreen(), 
                                    CONNECTOR_LINE_COLOR.getBlue(), 150);
        
        // 渲染已有的控制点
        for (Vec2d point : controlPoints) {
            Vec2d screenPoint = camera.worldToScreen(point);
            drawList.addCircleFilled((float)screenPoint.x, (float)screenPoint.y, CONTROL_POINT_SIZE, controlColor);
        }
        
        // 渲染连接线
        if (controlPoints.size() >= 2) {
            renderConnectionLinesImGui(drawList, camera, lineColor);
        }
        
        // 渲染到鼠标位置的预览线
        if (currentMousePoint != null && !controlPoints.isEmpty() && controlPoints.size() < 3) {
            Vec2d lastPoint = controlPoints.getLast();
            Vec2d screenLast = camera.worldToScreen(lastPoint);
            Vec2d screenMouse = camera.worldToScreen(currentMousePoint);
            drawList.addLine((float)screenLast.x, (float)screenLast.y, 
                           (float)screenMouse.x, (float)screenMouse.y, lineColor, 1.0f);
        }
    }
    
    private void renderConnectionLines(DrawContext context) {
        switch (currentMode) {
            case START_END_DIRECTION:
                if (controlPoints.size() >= 2) {
                    // 起点到终点的弦
                    context.drawLine(controlPoints.get(0), controlPoints.get(1), CONNECTOR_LINE_COLOR);
                }
                break;
            case THROUGH_POINT:
                if (controlPoints.size() >= 2) {
                    // 起点到经过点
                    context.drawLine(controlPoints.get(0), controlPoints.get(1), CONNECTOR_LINE_COLOR);
                }
                if (controlPoints.size() >= 3) {
                    // 经过点到终点
                    context.drawLine(controlPoints.get(1), controlPoints.get(2), CONNECTOR_LINE_COLOR);
                }
                break;
            case CENTER_START_END:
                if (controlPoints.size() >= 2) {
                    // 圆心到起点
                    context.drawLine(controlPoints.get(0), controlPoints.get(1), CONNECTOR_LINE_COLOR);
                }
                if (controlPoints.size() >= 3) {
                    // 圆心到终点
                    context.drawLine(controlPoints.get(0), controlPoints.get(2), CONNECTOR_LINE_COLOR);
                }
                break;
        }
    }
    
    private void renderConnectionLinesImGui(ImDrawList drawList, CanvasCamera camera, int lineColor) {
        switch (currentMode) {
            case START_END_DIRECTION:
                if (controlPoints.size() >= 2) {
                    Vec2d screen1 = camera.worldToScreen(controlPoints.get(0));
                    Vec2d screen2 = camera.worldToScreen(controlPoints.get(1));
                    drawList.addLine((float)screen1.x, (float)screen1.y, 
                                   (float)screen2.x, (float)screen2.y, lineColor, 1.0f);
                }
                break;
            case THROUGH_POINT:
                if (controlPoints.size() >= 2) {
                    Vec2d screen1 = camera.worldToScreen(controlPoints.get(0));
                    Vec2d screen2 = camera.worldToScreen(controlPoints.get(1));
                    drawList.addLine((float)screen1.x, (float)screen1.y, 
                                   (float)screen2.x, (float)screen2.y, lineColor, 1.0f);
                }
                if (controlPoints.size() >= 3) {
                    Vec2d screen2 = camera.worldToScreen(controlPoints.get(1));
                    Vec2d screen3 = camera.worldToScreen(controlPoints.get(2));
                    drawList.addLine((float)screen2.x, (float)screen2.y, 
                                   (float)screen3.x, (float)screen3.y, lineColor, 1.0f);
                }
                break;
            case CENTER_START_END:
                if (controlPoints.size() >= 2) {
                    Vec2d screenCenter = camera.worldToScreen(controlPoints.get(0));
                    Vec2d screenStart = camera.worldToScreen(controlPoints.get(1));
                    drawList.addLine((float)screenCenter.x, (float)screenCenter.y, 
                                   (float)screenStart.x, (float)screenStart.y, lineColor, 1.0f);
                }
                if (controlPoints.size() >= 3) {
                    Vec2d screenCenter = camera.worldToScreen(controlPoints.get(0));
                    Vec2d screenEnd = camera.worldToScreen(controlPoints.get(2));
                    drawList.addLine((float)screenCenter.x, (float)screenCenter.y, 
                                   (float)screenEnd.x, (float)screenEnd.y, lineColor, 1.0f);
                }
                break;
        }
    }
    
    private void renderArcImGui(ImDrawList drawList, CanvasCamera camera, ArcShape arc) {
        Vec2d center = arc.getCenter();
        double radius = arc.getRadius();
        double startAngle = arc.getStartAngle();
        double endAngle = arc.getEndAngle();
        
        Vec2d screenCenter = camera.worldToScreen(center);
        float screenRadius = (float)(radius * camera.getZoom());
        
        int arcColor = ImColor.rgba(ArcTool.PREVIEW_COLOR.getRed(), ArcTool.PREVIEW_COLOR.getGreen(), ArcTool.PREVIEW_COLOR.getBlue(), ArcTool.PREVIEW_COLOR.getAlpha());
        
        // 使用PathArcTo绘制弧形
        drawList.pathArcTo((float)screenCenter.x, (float)screenCenter.y, screenRadius, 
                          (float)startAngle, (float)endAngle, segments);
        drawList.pathStroke(arcColor, 0, 2.0f);
    }

    // ====== 几何计算方法 ======
    
    /**
     * 更新预览弧形
     */
    private void updatePreview() {
        if (controlPoints.size() < 3) {
            previewArc = null;
            previewShape = null;
            return;
        }
        
        ArcShape arc = createShapeFromControlPoints();
        if (arc != null) {
            // 应用预览样式
            ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
            if (previewStyle != null) {
                arc.setStyle(previewStyle);
            }
            
            previewArc = arc;
            previewShape = arc;
            
            LOGGER.debug("ArcTool.updatePreview: 中心={}, 半径={:.2f}, 起始角度={:.2f}, 结束角度={:.2f}", 
                        arc.getCenter(), arc.getRadius(), 
                        Math.toDegrees(arc.getStartAngle()), Math.toDegrees(arc.getEndAngle()));
        }
    }
    
    /**
     * 带当前鼠标位置的预览更新
     */
    private void updatePreviewWithMouse(Vec2d mousePoint) {
        if (controlPoints.size() < 2) {
            return;
        }
        
        // 创建临时控制点列表
        List<Vec2d> tempPoints = new ArrayList<>(controlPoints);
        tempPoints.add(mousePoint);
        
        if (tempPoints.size() >= 3) {
            // 临时保存当前控制点
            List<Vec2d> savedPoints = new ArrayList<>(controlPoints);
            controlPoints = tempPoints;
            
            // 更新预览
            updatePreview();
            
            // 恢复控制点
            controlPoints = savedPoints;
        }
    }
    
    /**
     * 判断一个角度是否在两个角度之间
     */
    private boolean isAngleBetween(double angle, double start, double end) {
        // 标准化角度到 [0, 2π)
        start = (start % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
        end = (end % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
        angle = (angle % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);

        if (start < end) {
            // 标准情况，没有跨越 0
            return angle >= start && angle <= end;
        } else {
            // 跨越 0 的情况 (例如从 330° 到 30°)
            return angle >= start || angle <= end;
        }
    }
    
    /**
     * 应用角度限制
     */
    private double applyAngleSnap(double startAngle, double endAngle) {
        if (!angleSnap) {
            return endAngle;
        }
        
        double sweepAngle = endAngle - startAngle;
        if (sweepAngle < 0) {
            sweepAngle += 2 * Math.PI;
        }
        
        double sweepDegrees = Math.toDegrees(sweepAngle);
        double snappedSweepDegrees = Math.round(sweepDegrees / snapAngle) * snapAngle;
        
        if (snappedSweepDegrees < 1e-6) {
            snappedSweepDegrees = snapAngle;
        }
        
        double snappedSweepRadians = Math.toRadians(snappedSweepDegrees);
        return startAngle + snappedSweepRadians;
    }

    // ====== 配置管理 ======
    
    @Override
    public void updateConfig(String key, String value) {
        if (key == null) {
            LOGGER.warn("配置键为null，无法处理配置");
            return;
        }
        
        switch (key) {
            case CONFIG_KEY_MODE:
                updateDrawMode(value);
                break;
            case CONFIG_KEY_SEGMENTS:
                updateSegments(value);
                break;
            case CONFIG_KEY_DIRECTION:
                updateDirection(value);
                break;
            case CONFIG_KEY_ANGLE_SNAP:
                updateAngleSnap(value);
                break;
            case CONFIG_KEY_SNAP_ANGLE:
                updateSnapAngle(value);
                break;
            default:
                LOGGER.debug("ArcTool: 未知配置键: {}", key);
                break;
        }
    }

    /**
     * 更新绘制模式
     */
    private void updateDrawMode(String value) {
        if (value == null || value.isEmpty()) {
            LOGGER.warn("绘制模式值为空，保持当前模式: {}", currentMode);
            return;
        }
        
        ArcMode previousMode = currentMode;
        
        switch (value) {
            case CONFIG_MODE_START_END_DIRECTION:
                currentMode = ArcMode.START_END_DIRECTION;
                break;
            case CONFIG_MODE_THROUGH_POINT:
                currentMode = ArcMode.THROUGH_POINT;
                break;
            case CONFIG_MODE_CENTER_START_END:
                currentMode = ArcMode.CENTER_START_END;
                break;
            default:
                LOGGER.warn("未知的弧形绘制模式: {}", value);
                return;
        }
        
        if (previousMode != currentMode) {
            LOGGER.debug("弧形工具模式已从 {} 更改为 {}", 
                        previousMode.getDisplayName(), currentMode.getDisplayName());
            resetDrawingState();
            config.setValue(CONFIG_KEY_MODE, value);
        }
    }
    
    private void updateSegments(String value) {
        try {
            int newSegments = Integer.parseInt(value);
            if (newSegments >= MIN_SEGMENTS && newSegments <= MAX_SEGMENTS) {
                segments = newSegments;
                config.setValue(CONFIG_KEY_SEGMENTS, String.valueOf(segments));
                LOGGER.debug("ArcTool 分段数更新为: {}", segments);
            } else {
                LOGGER.warn("ArcTool 分段数超出范围 [{}, {}]: {}", 
                          MIN_SEGMENTS, MAX_SEGMENTS, newSegments);
            }
        } catch (NumberFormatException e) {
            LOGGER.warn("ArcTool 无效的分段数配置: {}", value, e);
        }
    }
    
    private void updateDirection(String value) {
        try {
            clockwise = "clockwise".equalsIgnoreCase(value);
            config.setValue(CONFIG_KEY_DIRECTION, clockwise ? "clockwise" : "counterclockwise");
            LOGGER.debug("ArcTool 绘制方向更新为: {}", clockwise ? "顺时针" : "逆时针");
        } catch (Exception e) {
            LOGGER.error("ArcTool 无效的方向值: {}", value);
        }
    }
    
    private void updateAngleSnap(String value) {
        try {
            angleSnap = Boolean.parseBoolean(value);
            config.setValue(CONFIG_KEY_ANGLE_SNAP, String.valueOf(angleSnap));
            LOGGER.debug("ArcTool 角度限制更新为: {}", angleSnap);
        } catch (Exception e) {
            LOGGER.error("ArcTool 无效的角度限制值: {}", value);
        }
    }
    
    private void updateSnapAngle(String value) {
        try {
            float newSnapAngle = Float.parseFloat(value);
            if (newSnapAngle >= 5.0f && newSnapAngle <= 90.0f) {
                snapAngle = newSnapAngle;
                config.setValue(CONFIG_KEY_SNAP_ANGLE, String.valueOf(snapAngle));
                LOGGER.debug("ArcTool 角度限制值更新为: {}", snapAngle);
            } else {
                LOGGER.warn("ArcTool 角度限制值超出范围 [5.0, 90.0]: {}", newSnapAngle);
            }
        } catch (NumberFormatException e) {
            LOGGER.warn("ArcTool 无效的角度值: {}", value, e);
        }
    }

    // ====== 预览控制 ======
    
    @Override
    protected boolean shouldShowPreview() {
        return !controlPoints.isEmpty() || previewShape != null;
    }

    @Override
    protected com.masterplanner.ui.tools.impl.drawing.strategy.IInteractionStrategy createStrategy(InteractionType type) {
        return new ArcInteractionStrategy();
    }

    // ====== 工具信息 ======

    // ====== 获取器 ======
    
    public ArcMode getCurrentMode() {
        return currentMode;
    }
    
    public List<Vec2d> getControlPoints() {
        return new ArrayList<>(controlPoints);
    }
    
    public int getSegments() {
        return segments;
    }

    // ====== 自定义交互策略 ======
    
    /**
     * 弧形工具专用交互策略
     * 支持三种绘制模式的多点点击交互
     */
    private class ArcInteractionStrategy implements com.masterplanner.ui.tools.impl.drawing.strategy.IInteractionStrategy {
        
        @Override
        public InteractionResult onMouseDown(Vec2d pos, int button, DrawingToolContext context) {
            if (button != 0) { // 非左键
                if (button == 1) { // 右键取消
                    resetDrawingState();
                    context.resetDrawing("右键取消");
                    return InteractionResult.CANCEL;
                }
                return InteractionResult.IGNORED;
            }
            
            var snap = snapEnhancer.performEnhancedSnap(pos, context);
            Vec2d worldPoint = snap.point;
            LOGGER.debug("ArcTool.onMouseDown: 点击位置={}, 转换后={}", pos, worldPoint);
            
            // 添加控制点
            controlPoints.add(worldPoint);
            updateStatusMessageForCurrentMode();
            
            // 检查是否完成绘制
            if (controlPoints.size() >= 3) {
                // 创建最终图形
                updatePreview();
                if (previewArc != null) {
                    context.setToolState(DrawingTool.ToolState.DRAWING);
                    return InteractionResult.COMPLETE;
                } else {
                    LOGGER.warn("无法创建最终弧形，重置状态");
                    resetDrawingState();
                    return InteractionResult.CANCEL;
                }
            } else {
                context.setToolState(DrawingTool.ToolState.DRAWING);
                return InteractionResult.CONTINUE;
            }
        }
        
        @Override
        public InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context) {
            // 始终进行增强捕捉：未开始绘制前也更新指示器
            var snap = snapEnhancer.performEnhancedSnap(pos, context);
            currentMousePoint = snap.point;
            
            if (controlPoints.isEmpty()) {
                return InteractionResult.CONTINUE; // 触发重绘显示吸附指示
            }
            
            // 更新预览
            if (controlPoints.size() >= 2) {
                updatePreviewWithMouse(currentMousePoint);
            }
            
            return InteractionResult.CONTINUE;
        }
        
        @Override
        public InteractionResult onMouseUp(Vec2d pos, int button, DrawingToolContext context) {
            // 弧形工具使用点击模式，不需要处理鼠标释放
            return InteractionResult.IGNORED;
        }
        
        @Override
        public com.masterplanner.core.model.Shape getFinalShape() {
            // 直接使用外部类的统一逻辑，确保逻辑一致性
            if (previewArc != null) {
                // clone() 确保返回的是一个新实例
                ArcShape finalArc = (ArcShape) previewArc.clone(); 
                finalArc.setStyle(getStyleHandler().getFinalStyle()); // 应用最终样式
                return finalArc;
            }
            return null;
        }
        
        @Override
        public void reset() {
            resetDrawingState();
        }
        
        @Override
        public String getStrategyName() {
            return "ArcInteractionStrategy";
        }
        
        @Override
        public String getStrategyDescription() {
            return "弧形工具多点点击交互策略，支持三种绘制模式";
        }
        
        @Override
        public List<Vec2d> getControlPoints() {
            return new ArrayList<>(controlPoints);
        }
        
        @Override
        public Vec2d getCurrentMousePoint() {
            return currentMousePoint;
        }
    }
} 