package com.plot.ui.tools.impl.drawing;

import com.plot.api.geometry.Vec2d;
import com.plot.api.state.IAppState;
import com.plot.api.snap.ISnapManager;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.graphics.DrawContext;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.model.Shape;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.component.Icons;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.tools.snap.SnapEnhancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import imgui.ImDrawList;
import imgui.ImColor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 圆形工具 - 优化的策略模式版本
 * <p>
 * 支持三种绘制模式：
 * 1. 中心-半径模式：第一点为圆心，移动确定半径
 * 2. 两点模式：两点确定直径
 * 3. 三点模式：三点确定圆
 */
public class CircleTool extends DrawingTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(CircleTool.class);

    // ====== 绘制模式枚举 ======
    enum DrawMode {
        CENTER_RADIUS, // 中心和半径模式
        TWO_POINTS,    // 两点模式
        THREE_POINTS   // 三点模式
    }

    // ====== 配置常量 ======
    private static final String CONFIG_KEY_TYPE = "type";
    private static final String CONFIG_KEY_SEGMENTS = "segments";
    
    // ====== 配置值常量 ======
    private static final String CONFIG_TYPE_RADIUS = "radius";
    private static final String CONFIG_TYPE_TWO_POINTS = "twoPoints";
    private static final String CONFIG_TYPE_THREE_POINTS = "threePoints";
    
    // ====== 渲染常量 ======
    private static final int DEFAULT_SEGMENTS = 32;
    private static final int MIN_SEGMENTS = 8;
    private static final int MAX_SEGMENTS = 72;
    private static final float MIN_RADIUS = 0.1f;
    private static final double COLLINEAR_TOLERANCE = 1e-10;
    
    private static final float CONTROL_POINT_SIZE = 6.0f;
    private static final float SNAP_INDICATOR_SIZE = 8.0f; // 吸附指示器大小
    
    // ====== 状态消息映射 ======
    private static final Map<DrawMode, List<String>> MODE_STATUS_MESSAGES = Map.of(
        DrawMode.CENTER_RADIUS, List.of("点击设置圆心", "点击或拖动设置半径"),
        DrawMode.TWO_POINTS, List.of("点击设置第一个点", "点击设置第二个点以确定直径"),
        DrawMode.THREE_POINTS, List.of("点击设置第一个点", "点击设置第二个点", "点击设置第三个点以确定圆")
    );
    
    // ====== 核心状态 ======
    private DrawMode currentMode = DrawMode.CENTER_RADIUS;
    private List<Vec2d> controlPoints = new ArrayList<>();
    private CircleShape previewCircle;
    private int segments = DEFAULT_SEGMENTS;
    private Vec2d currentMousePoint; // 当前鼠标位置，用于辅助线渲染
    
    // ====== 吸附状态 ======
    private boolean isSnapping = false; // 当前是否正在吸附
    private Vec2d snapPoint = null; // 当前吸附点（如果有的话）
    private com.plot.core.snap.SnapPriorityEvaluator.SnapType currentSnapType = 
        com.plot.core.snap.SnapPriorityEvaluator.SnapType.NONE; // 当前捕捉类型
    
    // ====== 捕捉增强器 ======
    private final SnapEnhancer snapEnhancer = new SnapEnhancer("CircleTool");

    /**
     * 依赖注入构造函数（推荐）
     */
    public CircleTool(IAppState appState, ISnapManager snapManager) {
        super("circle", "圆形", Icons.CIRCLE_IDENTIFIER, "绘制圆形", 
              appState, snapManager, InteractionType.CLICK_AND_CLICK);
        initializeCircleTool();
    }

    /**
     * 默认构造函数（兼容性）
     * @deprecated 使用依赖注入构造函数
     */
    @Deprecated
    public CircleTool() {
        super("circle", "圆形", Icons.CIRCLE_IDENTIFIER, "绘制圆形");
        initializeCircleTool();
    }
    
    /**
     * 通用初始化逻辑
     */
    private void initializeCircleTool() {
        config.setValue(CONFIG_KEY_TYPE, CONFIG_TYPE_RADIUS); // 默认中心-半径模式
        config.setValue(CONFIG_KEY_SEGMENTS, String.valueOf(segments));
        
        // 使用反射设置自定义交互策略
        try {
            java.lang.reflect.Field strategyField = DrawingTool.class.getDeclaredField("interactionStrategy");
            strategyField.setAccessible(true);
            strategyField.set(this, new CircleInteractionStrategy());
            LOGGER.debug("CircleTool: 自定义交互策略设置成功");
        } catch (Exception e) {
            LOGGER.error("CircleTool: 无法设置自定义交互策略", e);
        }
        
        // 订阅配置事件
        eventBus.subscribe(ToolConfigEvent.class, event -> {
            if (event instanceof ToolConfigEvent toolConfigEvent && 
                getId().equals(toolConfigEvent.getToolId())) {
                updateConfig(toolConfigEvent.getOptionName(), String.valueOf(toolConfigEvent.getValue()));
            }
        });
        
        // 更新状态消息
        updateStatusMessageForCurrentMode();
        
        LOGGER.debug("CircleTool 初始化完成，模式: {}, 分段数: {}", currentMode, segments);
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
            LOGGER.debug("圆形工具状态消息已更新: {}", message);
        } else {
            setStatusMessage("圆形工具就绪");
        }
    }
    
    /**
     * 重置绘制状态
     */
    private void resetDrawingState() {
        controlPoints.clear();
        previewCircle = null;
        currentMousePoint = null;
        isSnapping = false;
        snapPoint = null;
        currentSnapType = com.plot.core.snap.SnapPriorityEvaluator.SnapType.NONE;
        snapEnhancer.reset(); // 重置SnapEnhancer状态
        updateStatusMessageForCurrentMode();
        LOGGER.debug("CircleTool: 重置绘制状态");
    }

    // ====== 实现抽象方法 ======
    
    @Override
    protected Shape createShape(Vec2d startPoint, Vec2d endPoint) {
        // 根据当前模式和控制点创建圆形
        if (previewCircle != null) {
            // 使用预览圆的参数创建最终圆形
            CircleShape circle = new CircleShape(previewCircle.getCenter(), previewCircle.getRadius());
            
            // 应用样式
            ShapeStyle style = getStyleHandler().getFinalStyle();
            if (style != null) {
                circle.setStyle(style);
            }
            
            LOGGER.debug("CircleTool 创建圆形: 中心={}, 半径={:.2f}", 
                        previewCircle.getCenter(), previewCircle.getRadius());
            return circle;
        }
        
        // 如果没有预览圆，根据模式计算
        switch (currentMode) {
            case CENTER_RADIUS -> {
                if (startPoint != null && endPoint != null) {
        double radius = Math.max(startPoint.distance(endPoint), MIN_RADIUS);
                    return createCircleWithStyle(startPoint, radius);
                }
            }
            case TWO_POINTS -> {
                if (controlPoints.size() >= 2) {
                    Vec2d p1 = controlPoints.get(0);
                    Vec2d p2 = controlPoints.get(1);
                    Vec2d center = new Vec2d((p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
                    double radius = Math.max(p1.distance(center), MIN_RADIUS);
                    return createCircleWithStyle(center, radius);
                }
            }
            case THREE_POINTS -> {
                if (controlPoints.size() >= 3) {
                    Vec2d center = calculateCircleCenter(controlPoints.get(0), 
                                                        controlPoints.get(1), 
                                                        controlPoints.get(2));
                    if (center != null) {
                        double radius = Math.max(center.distance(controlPoints.getFirst()), MIN_RADIUS);
                        return createCircleWithStyle(center, radius);
                    }
                }
            }
        }
        
        LOGGER.warn("CircleTool.createShape: 无法创建圆形，参数不足");
        return null;
    }
    
    /**
     * 创建带样式的圆形
     */
    private CircleShape createCircleWithStyle(Vec2d center, double radius) {
        CircleShape circle = new CircleShape(center, radius);
        ShapeStyle style = getStyleHandler().getFinalStyle();
        if (style != null) {
            circle.setStyle(style);
        }
        return circle;
    }

    @Override
    protected void renderPreview(DrawContext context) {
        // 根据模式渲染不同的预览内容（只在有控制点时）
        if (!controlPoints.isEmpty()) {
            switch (currentMode) {
                case CENTER_RADIUS -> renderCenterRadiusPreview(context);
                case TWO_POINTS -> renderTwoPointsPreview(context);
                case THREE_POINTS -> renderThreePointsPreview(context);
            }
            
            // 渲染吸附指示器（旧版本）
            renderSnapIndicator(context);
        }
        
        // 总是渲染SnapEnhancer指示器（包括预绘制时的捕捉）
        snapEnhancer.renderSnapIndicator(context);
    }

    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (camera == null) return;
        
        // 根据模式渲染不同的预览内容（只在有控制点时）
        if (!controlPoints.isEmpty()) {
            switch (currentMode) {
                case CENTER_RADIUS -> renderCenterRadiusPreview(drawList, camera);
                case TWO_POINTS -> renderTwoPointsPreview(drawList, camera);
                case THREE_POINTS -> renderThreePointsPreview(drawList, camera);
            }
            
            // 渲染吸附指示器（旧版本）
            renderSnapIndicator(drawList, camera);
        }
        
        // 总是渲染SnapEnhancer指示器（包括预绘制时的捕捉）
        snapEnhancer.renderSnapIndicator(drawList, camera);
    }
    
    // ====== DrawContext 预览渲染方法 ======
    
    private void renderCenterRadiusPreview(DrawContext context) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        Color centerPointColor = toColor(theme.errorText, 0xCC);
        Color radiusPointColor = toColor(theme.successText, 0xCC);
        Color connectorLineColor = toColor(theme.warningText, 0x99);

        if (!controlPoints.isEmpty()) {
            Vec2d center = controlPoints.get(0);
            
            // 绘制圆心
            context.drawCircleFilled(center, CONTROL_POINT_SIZE, centerPointColor);
            
            if (previewCircle != null) {
                // 绘制预览圆 - 使用当前图层颜色
                Color previewColor = getPreviewColor();
                context.drawCircle(center, previewCircle.getRadius(), previewColor);
                
                // 如果有第二个点，绘制半径线
                if (controlPoints.size() >= 2) {
                    Vec2d radiusPoint = controlPoints.get(1);
                    context.drawCircleFilled(radiusPoint, CONTROL_POINT_SIZE, radiusPointColor);
                    context.drawLine(center, radiusPoint, connectorLineColor);
                } else if (currentMousePoint != null) {
                    // 绘制到鼠标位置的半径线
                    context.drawLine(center, currentMousePoint, connectorLineColor);
                }
            }
        }
    }
    
    private void renderTwoPointsPreview(DrawContext context) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        Color controlPointColor = toColor(theme.infoText, 0xCC);
        Color centerPointColor = toColor(theme.errorText, 0xCC);
        Color connectorLineColor = toColor(theme.warningText, 0x99);

        if (!controlPoints.isEmpty()) {
            Vec2d p1 = controlPoints.get(0);
            context.drawCircleFilled(p1, CONTROL_POINT_SIZE, controlPointColor);
            
            if (controlPoints.size() >= 2) {
                Vec2d p2 = controlPoints.get(1);
                context.drawCircleFilled(p2, CONTROL_POINT_SIZE, controlPointColor);
                context.drawLine(p1, p2, connectorLineColor);
                
                if (previewCircle != null) {
                    Vec2d center = previewCircle.getCenter();
                    Color previewColor = getPreviewColor();
                    context.drawCircle(center, previewCircle.getRadius(), previewColor);
                    context.drawCircleFilled(center, CONTROL_POINT_SIZE, centerPointColor);
                }
            } else if (currentMousePoint != null) {
                // 绘制到鼠标位置的线
                context.drawLine(p1, currentMousePoint, connectorLineColor);
                
                if (previewCircle != null) {
                    Vec2d center = previewCircle.getCenter();
                    Color previewColor = getPreviewColor();
                    context.drawCircle(center, previewCircle.getRadius(), previewColor);
                    context.drawCircleFilled(center, CONTROL_POINT_SIZE, centerPointColor);
                }
            }
        }
    }
    
    private void renderThreePointsPreview(DrawContext context) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        Color controlPointColor = toColor(theme.infoText, 0xCC);
        Color centerPointColor = toColor(theme.errorText, 0xCC);
        Color connectorLineColor = toColor(theme.warningText, 0x99);

        // 绘制已有的控制点
        for (Vec2d point : controlPoints) {
            context.drawCircleFilled(point, CONTROL_POINT_SIZE, controlPointColor);
        }
        
        // 绘制连线
        if (controlPoints.size() >= 2) {
            Vec2d p1 = controlPoints.get(0);
            Vec2d p2 = controlPoints.get(1);
            context.drawLine(p1, p2, connectorLineColor);
            
            if (controlPoints.size() >= 3) {
                Vec2d p3 = controlPoints.get(2);
                context.drawLine(p2, p3, connectorLineColor);
                context.drawLine(p3, p1, connectorLineColor);
            } else if (currentMousePoint != null) {
                // 绘制到鼠标位置的预览线
                context.drawLine(p2, currentMousePoint, connectorLineColor);
                context.drawLine(currentMousePoint, p1, connectorLineColor);
            }
        } else if (controlPoints.size() == 1 && currentMousePoint != null) {
            // 只有一个点时，绘制到鼠标的线
            context.drawLine(controlPoints.getFirst(), currentMousePoint, connectorLineColor);
        }
        
        // 绘制预览圆
        if (previewCircle != null) {
            Vec2d center = previewCircle.getCenter();
            Color previewColor = getPreviewColor();
            context.drawCircle(center, previewCircle.getRadius(), previewColor);
            context.drawCircleFilled(center, CONTROL_POINT_SIZE, centerPointColor);
        }
    }
    
    // ====== ImDrawList 预览渲染方法 ======
    
    private void renderCenterRadiusPreview(ImDrawList drawList, CanvasCamera camera) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        if (!controlPoints.isEmpty()) {
            Vec2d center = controlPoints.getFirst();
            Vec2d screenCenter = camera.worldToScreen(center);
            
            // 绘制圆心
            int centerColor = withAlpha(theme.errorText, 0xCC);
            drawList.addCircleFilled((float)screenCenter.x, (float)screenCenter.y, CONTROL_POINT_SIZE, centerColor);
            
            if (previewCircle != null) {
                // 绘制预览圆
                float screenRadius = (float)(previewCircle.getRadius() * camera.getZoom());
                Color layerPreviewColor = getPreviewColor();
                int previewColor = ImColor.rgba(layerPreviewColor.getRed(), layerPreviewColor.getGreen(), 
                                              layerPreviewColor.getBlue(), layerPreviewColor.getAlpha());
                drawList.addCircle((float)screenCenter.x, (float)screenCenter.y, screenRadius, previewColor, segments, 2.0f);
                
                // 绘制半径线
                if (controlPoints.size() >= 2) {
                    Vec2d radiusPoint = controlPoints.get(1);
                    Vec2d screenRadiusPoint = camera.worldToScreen(radiusPoint);
                    int radiusColor = withAlpha(theme.successText, 0xCC);
                    drawList.addCircleFilled((float)screenRadiusPoint.x, (float)screenRadiusPoint.y, CONTROL_POINT_SIZE, radiusColor);
                    
                    int lineColor = withAlpha(theme.warningText, 0x99);
                    drawList.addLine((float)screenCenter.x, (float)screenCenter.y, 
                                   (float)screenRadiusPoint.x, (float)screenRadiusPoint.y, lineColor, 1.0f);
                } else if (currentMousePoint != null) {
                    Vec2d screenMouse = camera.worldToScreen(currentMousePoint);
                    int lineColor = withAlpha(theme.warningText, 0x99);
                    drawList.addLine((float)screenCenter.x, (float)screenCenter.y, 
                                   (float)screenMouse.x, (float)screenMouse.y, lineColor, 1.0f);
                }
            }
        }
    }
    
    private void renderTwoPointsPreview(ImDrawList drawList, CanvasCamera camera) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        int controlColor = withAlpha(theme.infoText, 0xCC);
        int lineColor = withAlpha(theme.warningText, 0x99);
        
        if (!controlPoints.isEmpty()) {
            Vec2d p1 = controlPoints.getFirst();
            Vec2d screenP1 = camera.worldToScreen(p1);
            drawList.addCircleFilled((float)screenP1.x, (float)screenP1.y, CONTROL_POINT_SIZE, controlColor);
            
            if (controlPoints.size() >= 2) {
                Vec2d p2 = controlPoints.get(1);
                Vec2d screenP2 = camera.worldToScreen(p2);
                drawList.addCircleFilled((float)screenP2.x, (float)screenP2.y, CONTROL_POINT_SIZE, controlColor);
                drawList.addLine((float)screenP1.x, (float)screenP1.y, (float)screenP2.x, (float)screenP2.y, lineColor, 1.0f);
            } else if (currentMousePoint != null) {
                Vec2d screenMouse = camera.worldToScreen(currentMousePoint);
                drawList.addLine((float)screenP1.x, (float)screenP1.y, (float)screenMouse.x, (float)screenMouse.y, lineColor, 1.0f);
            }
            
            if (previewCircle != null) {
                Vec2d center = previewCircle.getCenter();
                Vec2d screenCenter = camera.worldToScreen(center);
                float screenRadius = (float)(previewCircle.getRadius() * camera.getZoom());
                
                Color layerPreviewColor = getPreviewColor();
                int previewColor = ImColor.rgba(layerPreviewColor.getRed(), layerPreviewColor.getGreen(), 
                                              layerPreviewColor.getBlue(), layerPreviewColor.getAlpha());
                drawList.addCircle((float)screenCenter.x, (float)screenCenter.y, screenRadius, previewColor, segments, 2.0f);
                
                int centerColor = withAlpha(theme.errorText, 0xCC);
                drawList.addCircleFilled((float)screenCenter.x, (float)screenCenter.y, CONTROL_POINT_SIZE, centerColor);
            }
        }
    }
    
    private void renderThreePointsPreview(ImDrawList drawList, CanvasCamera camera) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        int controlColor = withAlpha(theme.infoText, 0xCC);
        int lineColor = withAlpha(theme.warningText, 0x99);
        
        // 绘制控制点
        for (Vec2d point : controlPoints) {
            Vec2d screenPoint = camera.worldToScreen(point);
            drawList.addCircleFilled((float)screenPoint.x, (float)screenPoint.y, CONTROL_POINT_SIZE, controlColor);
        }
        
        // 绘制连线
        if (controlPoints.size() >= 2) {
            Vec2d screenP1 = camera.worldToScreen(controlPoints.get(0));
            Vec2d screenP2 = camera.worldToScreen(controlPoints.get(1));
            drawList.addLine((float)screenP1.x, (float)screenP1.y, (float)screenP2.x, (float)screenP2.y, lineColor, 1.0f);
            
            if (controlPoints.size() >= 3) {
                Vec2d screenP3 = camera.worldToScreen(controlPoints.get(2));
                drawList.addLine((float)screenP2.x, (float)screenP2.y, (float)screenP3.x, (float)screenP3.y, lineColor, 1.0f);
                drawList.addLine((float)screenP3.x, (float)screenP3.y, (float)screenP1.x, (float)screenP1.y, lineColor, 1.0f);
            } else if (currentMousePoint != null) {
                Vec2d screenMouse = camera.worldToScreen(currentMousePoint);
                drawList.addLine((float)screenP2.x, (float)screenP2.y, (float)screenMouse.x, (float)screenMouse.y, lineColor, 1.0f);
                drawList.addLine((float)screenMouse.x, (float)screenMouse.y, (float)screenP1.x, (float)screenP1.y, lineColor, 1.0f);
            }
        } else if (controlPoints.size() == 1 && currentMousePoint != null) {
            Vec2d screenP1 = camera.worldToScreen(controlPoints.getFirst());
            Vec2d screenMouse = camera.worldToScreen(currentMousePoint);
            drawList.addLine((float)screenP1.x, (float)screenP1.y, (float)screenMouse.x, (float)screenMouse.y, lineColor, 1.0f);
        }
        
        // 绘制预览圆
        if (previewCircle != null) {
            Vec2d center = previewCircle.getCenter();
            Vec2d screenCenter = camera.worldToScreen(center);
            float screenRadius = (float)(previewCircle.getRadius() * camera.getZoom());
            
            Color layerPreviewColor = getPreviewColor();
            int previewColor = ImColor.rgba(layerPreviewColor.getRed(), layerPreviewColor.getGreen(), 
                                          layerPreviewColor.getBlue(), layerPreviewColor.getAlpha());
            drawList.addCircle((float)screenCenter.x, (float)screenCenter.y, screenRadius, previewColor, segments, 2.0f);
            
            int centerColor = withAlpha(theme.errorText, 0xCC);
            drawList.addCircleFilled((float)screenCenter.x, (float)screenCenter.y, CONTROL_POINT_SIZE, centerColor);
        }
    }

    // ====== 几何计算方法 ======
    
    /**
     * 计算三点确定的圆的圆心坐标
     */
    private Vec2d calculateCircleCenter(Vec2d p1, Vec2d p2, Vec2d p3) {
        LOGGER.debug("calculateCircleCenter: p1={}, p2={}, p3={}", p1, p2, p3);
        
        // 使用行列式计算圆心坐标
        double d = 2 * (p1.x * (p2.y - p3.y) + p2.x * (p3.y - p1.y) + p3.x * (p1.y - p2.y));

        // 检查三点是否共线
        if (Math.abs(d) < COLLINEAR_TOLERANCE) {
            LOGGER.warn("三点共线，无法计算圆心，判别式={}", d);
            return null;
        }

        // 计算圆心坐标
        double x = ((p1.x * p1.x + p1.y * p1.y) * (p2.y - p3.y) +
                   (p2.x * p2.x + p2.y * p2.y) * (p3.y - p1.y) +
                   (p3.x * p3.x + p3.y * p3.y) * (p1.y - p2.y)) / d;
        double y = ((p1.x * p1.x + p1.y * p1.y) * (p3.x - p2.x) +
                   (p2.x * p2.x + p2.y * p2.y) * (p1.x - p3.x) +
                   (p3.x * p3.x + p3.y * p3.y) * (p2.x - p1.x)) / d;

        Vec2d center = new Vec2d(x, y);
        LOGGER.debug("calculateCircleCenter: 计算圆心={}", center);
        return center;
    }
    
    /**
     * 更新预览圆
     */
    private void updatePreview(Vec2d center, double radius) {
        if (center == null) {
            LOGGER.error("无法更新预览：中心点为null");
            return;
        }

        // 确保半径为正值
        radius = Math.max(radius, MIN_RADIUS);

        // 创建或更新预览圆
        if (previewCircle != null) {
            previewCircle.setCenter(center);
            previewCircle.setRadius(radius);
        } else {
            previewCircle = new CircleShape(center, radius);
        }

        // 应用预览样式
        ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
        if (previewStyle != null) {
            previewCircle.setStyle(previewStyle);
        }

        // 设置为当前预览图形
        previewShape = previewCircle;

        LOGGER.debug("CircleTool.updatePreview: 中心={}, 半径={:.2f}", center, radius);
    }

    /**
     * Shift 约束：将第二点锁定到与第一点水平或垂直对齐
     */
    private Vec2d constrainSecondPointToAxis(Vec2d anchor, Vec2d candidate) {
        if (!isShiftDown || anchor == null || candidate == null) {
            return candidate;
        }
        double dx = Math.abs(candidate.x - anchor.x);
        double dy = Math.abs(candidate.y - anchor.y);
        if (dx >= dy) {
            // 水平优先
            return new Vec2d(candidate.x, anchor.y);
        }
        return new Vec2d(anchor.x, candidate.y);
    }

    /**
     * 获取预览颜色（使用当前图层颜色）
     */
    private Color getPreviewColor() {
        try {
            ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
            if (previewStyle != null && previewStyle.getLineColor() != null) {
                Color layerColor = previewStyle.getLineColor();
                // 添加半透明效果用于预览
                return new Color(layerColor.getRed(), layerColor.getGreen(), 
                               layerColor.getBlue(), 200);
            }
        } catch (Exception e) {
            LOGGER.warn("CircleTool: 获取预览颜色失败: {}", e.getMessage());
        }
        
        return toColor(ThemeManager.getInstance().getCurrentTheme().accent, 200);
    }

    // 统一使用 SnapEnhancer 提供的增强捕捉与可视化

    /**
     * 渲染吸附指示器 - DrawContext版本
     */
    private void renderSnapIndicator(DrawContext context) {
        if (!isSnapping || snapPoint == null) {
            return;
        }
        
        // 根据捕捉类型使用不同的视觉效果
        Color indicatorColor = getSnapIndicatorColor(currentSnapType);
        float indicatorSize = getSnapIndicatorSize(currentSnapType);
        
        // 渲染主要的吸附指示器
        context.drawCircle(snapPoint, indicatorSize, indicatorColor);
        
        // 渲染捕捉类型的特殊标记
        renderSnapTypeIndicator(context, snapPoint, currentSnapType);
        
        // 已确定的控制点也显示吸附指示器
        for (Vec2d controlPoint : controlPoints) {
            context.drawCircle(controlPoint, SNAP_INDICATOR_SIZE, toColor(ThemeManager.getInstance().getCurrentTheme().warningText, 200));
        }
    }

    /**
     * 渲染吸附指示器 - ImDrawList版本
     */
    private void renderSnapIndicator(ImDrawList drawList, CanvasCamera camera) {
        if (!isSnapping || snapPoint == null || camera == null) {
            return;
        }
        
        // 根据捕捉类型使用统一样式
        Color indicatorColor = com.plot.ui.tools.snap.SnapVisualStyle.colorFor(currentSnapType);
        float indicatorSize = com.plot.ui.tools.snap.SnapVisualStyle.ringSizeFor(currentSnapType);
        
        int snapColor = ImColor.rgba(indicatorColor.getRed(), indicatorColor.getGreen(), 
                                   indicatorColor.getBlue(), 200);
        
        // 渲染当前吸附点的指示器环
        Vec2d screenSnapPoint = camera.worldToScreen(snapPoint);
        drawList.addCircle((float) screenSnapPoint.x, (float) screenSnapPoint.y, 
                          indicatorSize * 0.7f, snapColor, 16, 2.0f);
        
        // 渲染捕捉类型的特殊标记（ImGui版本）
        renderSnapTypeIndicatorImGui(drawList, camera, snapPoint, currentSnapType);
        
        // 已确定的控制点也显示吸附指示器
        int controlSnapColor = withAlpha(ThemeManager.getInstance().getCurrentTheme().warningText, 200);
        for (Vec2d controlPoint : controlPoints) {
            Vec2d screenControlPoint = camera.worldToScreen(controlPoint);
            drawList.addCircle((float) screenControlPoint.x, (float) screenControlPoint.y, 
                              SNAP_INDICATOR_SIZE, controlSnapColor, 16, 2.0f);
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
        float size = 4.0f;
        
        switch (snapType) {
            case END_POINT -> {
                // 端点：实心正方形
                int color = com.plot.ui.tools.snap.SnapVisualStyle.imGuiColorFor(com.plot.core.snap.SnapPriorityEvaluator.SnapType.END_POINT, 255);
                drawList.addRectFilled(x - size, y - size, x + size, y + size, color);
            }
            case MID_POINT -> {
                // 中点：三角形标记
                int color = com.plot.ui.tools.snap.SnapVisualStyle.imGuiColorFor(com.plot.core.snap.SnapPriorityEvaluator.SnapType.MID_POINT, 255);
                drawList.addTriangleFilled(x, y - size, x - size, y + size, x + size, y + size, color);
            }
            case CENTER_POINT, CENTROID -> {
                // 中心点：十字标记
                int color = com.plot.ui.tools.snap.SnapVisualStyle.imGuiColorFor(com.plot.core.snap.SnapPriorityEvaluator.SnapType.CENTER_POINT, 255);
                drawList.addLine(x - size, y, x + size, y, color, 2.0f);
                drawList.addLine(x, y - size, x, y + size, color, 2.0f);
            }
            case INTERSECTION, VERTEX -> {
                // 交点：X标记
                int color = com.plot.ui.tools.snap.SnapVisualStyle.imGuiColorFor(com.plot.core.snap.SnapPriorityEvaluator.SnapType.INTERSECTION, 255);
                drawList.addLine(x - size, y - size, x + size, y + size, color, 2.0f);
                drawList.addLine(x - size, y + size, x + size, y - size, color, 2.0f);
            }
            case PERPENDICULAR, HORIZONTAL, VERTICAL -> {
                // 垂足：垂直线标记
                int color = com.plot.ui.tools.snap.SnapVisualStyle.imGuiColorFor(com.plot.core.snap.SnapPriorityEvaluator.SnapType.PERPENDICULAR, 255);
                drawList.addLine(x, y - size, x, y + size, color, 2.0f);
                drawList.addLine(x - size * 0.5f, y + size * 0.5f, x + size * 0.5f, y + size * 0.5f, color, 2.0f);
            }
            case TANGENT -> {
                // 切点：小圆标记
                int color = com.plot.ui.tools.snap.SnapVisualStyle.imGuiColorFor(com.plot.core.snap.SnapPriorityEvaluator.SnapType.TANGENT, 255);
                drawList.addCircle(x, y, size * 0.6f, color, 16, 2.0f);
            }
            case QUADRANT, CONTROL_POINT -> {
                // 控制点：小正方形
                int color = ThemeManager.getInstance().getCurrentTheme().accent;
                drawList.addRectFilled(x - size * 0.5f, y - size * 0.5f, x + size * 0.5f, y + size * 0.5f, color);
            }
            case NEAREST_POINT, GRID_POINT -> {
                // 网格点：小圆点
                int color = ThemeManager.getInstance().getCurrentTheme().mutedText;
                drawList.addCircleFilled(x, y, size * 0.3f, color);
            }
            case EXTENSION, PARALLEL -> {
                // 延长线：小线段
                int color = ThemeManager.getInstance().getCurrentTheme().mutedText;
                drawList.addLine(x - size * 0.5f, y, x + size * 0.5f, y, color, 2.0f);
            }
            case NONE -> {
                // 无捕捉：不渲染任何标记
            }
            default -> {
                // 其他类型：默认小圆点
                int color = ThemeManager.getInstance().getCurrentTheme().warningText;
                drawList.addCircleFilled(x, y, size * 0.4f, color);
            }
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static Color toColor(int color, int alpha) {
        return new Color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, alpha);
    }
    
    /**
     * 根据捕捉类型获取指示器颜色
     */
    private Color getSnapIndicatorColor(com.plot.core.snap.SnapPriorityEvaluator.SnapType snapType) {
        return com.plot.ui.tools.snap.SnapVisualStyle.colorFor(snapType);
    }
    
    /**
     * 根据捕捉类型获取指示器大小
     */
    private float getSnapIndicatorSize(com.plot.core.snap.SnapPriorityEvaluator.SnapType snapType) {
        // 以全局样式的环尺寸为基准，与本地 SNAP_INDICATOR_SIZE 对齐偏移
        float base = com.plot.ui.tools.snap.SnapVisualStyle.ringSizeFor(snapType);
        // 保持与原有常量的相对一致性：按比例微调
        float scale = SNAP_INDICATOR_SIZE / com.plot.ui.tools.snap.SnapVisualStyle.DEFAULT_RING_SIZE;
        return base * scale;
    }
    
    /**
     * 渲染捕捉类型的特殊标记
     */
    private void renderSnapTypeIndicator(DrawContext context, Vec2d point, 
                                       com.plot.core.snap.SnapPriorityEvaluator.SnapType snapType) {
        float size = 4.0f;
        Color markerColor = getSnapIndicatorColor(snapType);
        switch (snapType) {
            case END_POINT -> // 端点：实心正方形
                context.drawCircleFilled(point, size, markerColor);
            case MID_POINT -> // 中点：三角形标记
                context.drawCircleFilled(point, size * 0.8f, markerColor);
            case CENTER_POINT, CENTROID -> {
                // 中心点：十字标记
                context.drawLine(new Vec2d(point.x - size, point.y), 
                       new Vec2d(point.x + size, point.y), markerColor);
                context.drawLine(new Vec2d(point.x, point.y - size), 
                       new Vec2d(point.x, point.y + size), markerColor);
            }
            case INTERSECTION, VERTEX -> {
                // 交点：X标记
                context.drawLine(new Vec2d(point.x - size, point.y - size), 
                       new Vec2d(point.x + size, point.y + size), markerColor);
                context.drawLine(new Vec2d(point.x - size, point.y + size), 
                       new Vec2d(point.x + size, point.y - size), markerColor);
            }
            case PERPENDICULAR, HORIZONTAL, VERTICAL -> {
                // 垂足：垂直线标记
                context.drawLine(new Vec2d(point.x, point.y - size), 
                       new Vec2d(point.x, point.y + size), markerColor);
                context.drawLine(new Vec2d(point.x - size * 0.5, point.y + size * 0.5), 
                       new Vec2d(point.x + size * 0.5, point.y + size * 0.5), markerColor);
            }
            case TANGENT -> // 切点：圆形标记
                context.drawCircle(point, size * 0.6f, markerColor);
            case QUADRANT, CONTROL_POINT -> // 控制点：小正方形
                context.drawCircleFilled(point, size * 0.5f, markerColor);
            case NEAREST_POINT, GRID_POINT -> // 网格点：小圆点
                context.drawCircleFilled(point, size * 0.3f, markerColor);
            case EXTENSION, PARALLEL -> // 延长线：虚线标记 (简化为小线段)
                    context.drawLine(new Vec2d(point.x - size * 0.5, point.y),
                       new Vec2d(point.x + size * 0.5, point.y), markerColor);
            case NONE -> {
                // 无捕捉：不渲染任何标记
            }
            default -> // 其他类型：默认小圆点
                context.drawCircleFilled(point, size * 0.4f, toColor(ThemeManager.getInstance().getCurrentTheme().warningText, 200));
        }
    }

    // ====== 配置管理 ======
    
    @Override
    public void updateConfig(String key, String value) {
        switch (key) {
            case CONFIG_KEY_TYPE -> updateDrawMode(value);
            case CONFIG_KEY_SEGMENTS -> updateSegments(value);
            default -> LOGGER.debug("CircleTool: 未知配置键: {}", key);
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

        DrawMode previousMode = currentMode;
        switch (value) {
            case CONFIG_TYPE_RADIUS -> currentMode = DrawMode.CENTER_RADIUS;
            case CONFIG_TYPE_TWO_POINTS -> currentMode = DrawMode.TWO_POINTS;
            case CONFIG_TYPE_THREE_POINTS -> currentMode = DrawMode.THREE_POINTS;
            default -> {
                LOGGER.warn("未知的圆形工具类型: {}", value);
                return;
            }
        }

        if (previousMode != currentMode) {
            LOGGER.debug("圆形工具模式已更改为: {}", currentMode);
            resetDrawingState();
            config.setValue(CONFIG_KEY_TYPE, value);
        }
    }
    
    private void updateSegments(String value) {
        try {
            int newSegments = Integer.parseInt(value);
            if (newSegments >= MIN_SEGMENTS && newSegments <= MAX_SEGMENTS) {
                segments = newSegments;
                config.setValue(CONFIG_KEY_SEGMENTS, String.valueOf(segments));
                LOGGER.debug("CircleTool 分段数更新为: {}", segments);
            } else {
                LOGGER.warn("CircleTool 分段数超出范围 [{}, {}]: {}", 
                          MIN_SEGMENTS, MAX_SEGMENTS, newSegments);
            }
        } catch (NumberFormatException e) {
            LOGGER.warn("CircleTool 无效的分段数配置: {}", value, e);
        }
    }

    // ====== 预览控制 ======
    
    @Override
    protected boolean shouldShowPreview() {
        return !controlPoints.isEmpty() || previewShape != null;
    }

    // ====== 工具信息 ======

    // ====== 获取器 ======
    
    public int getSegments() {
        return segments;
    }
    
    public DrawMode getCurrentMode() {
        return currentMode;
    }
    
    // ====== 自定义交互策略 ======
    
    /**
     * 圆形工具专用交互策略
     * 支持三种绘制模式的多点点击交互
     */
    private class CircleInteractionStrategy implements com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy {
        
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
            
            // 使用SnapEnhancer进行捕捉检测
            SnapEnhancer.SnapResult snapResult = snapEnhancer.performEnhancedSnap(pos, context);
            Vec2d worldPoint = snapResult.point;
            if (controlPoints.size() == 1) {
                worldPoint = constrainSecondPointToAxis(controlPoints.getFirst(), worldPoint);
            }
            
            // 更新捕捉状态
            isSnapping = snapResult.snapped;
            snapPoint = snapResult.snapped ? worldPoint : null;
            currentSnapType = snapResult.snapType;
            
            LOGGER.debug("CircleTool.onMouseDown: 点击位置={}, 转换后={}, 捕捉={}, 类型={}", 
                        pos, worldPoint, snapResult.snapped, snapResult.snapType);
            
            // 根据当前模式处理点击
            switch (currentMode) {
                case CENTER_RADIUS -> {
                    if (controlPoints.isEmpty()) {
                        // 添加圆心
                        controlPoints.add(worldPoint);
                        updateStatusMessageForCurrentMode();
                        context.setToolState(DrawingTool.ToolState.DRAWING);
                        return InteractionResult.CONTINUE;
                    } else if (controlPoints.size() == 1) {
                        // 第二次点击完成绘制
                        controlPoints.add(worldPoint);
                        Vec2d center = controlPoints.getFirst();
                        double radius = center.distance(worldPoint);
                        updatePreview(center, radius);
                        return InteractionResult.COMPLETE;
                    }
                }
                case TWO_POINTS -> {
                    if (controlPoints.size() < 2) {
                        controlPoints.add(worldPoint);
                        updateStatusMessageForCurrentMode();
                        context.setToolState(DrawingTool.ToolState.DRAWING);
                        
                        if (controlPoints.size() == 2) {
                            // 两点模式完成
                            Vec2d p1 = controlPoints.get(0);
                            Vec2d p2 = controlPoints.get(1);
                            Vec2d center = new Vec2d((p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
                            double radius = p1.distance(center);
                            updatePreview(center, radius);
                            return InteractionResult.COMPLETE;
                        }
                        return InteractionResult.CONTINUE;
                    }
                }
                case THREE_POINTS -> {
                    if (controlPoints.size() < 3) {
                        controlPoints.add(worldPoint);
                        updateStatusMessageForCurrentMode();
                        context.setToolState(DrawingTool.ToolState.DRAWING);
                        
                        if (controlPoints.size() == 3) {
                            // 三点模式完成
                            Vec2d center = calculateCircleCenter(
                                controlPoints.get(0), 
                                controlPoints.get(1), 
                                controlPoints.get(2)
                            );
                            
                            if (center != null) {
                                double radius = center.distance(controlPoints.getFirst());
                                updatePreview(center, radius);
                                return InteractionResult.COMPLETE;
                                                         } else {
                                 // 三点共线，重置
                                 setStatusMessage("三点共线，无法绘制圆形。请重新选择三个不共线的点。");
                                 resetDrawingState();
                                 context.resetDrawing("三点共线");
                                 return InteractionResult.CANCEL;
                             }
                        }
                        return InteractionResult.CONTINUE;
                    }
                }
            }
            
            return InteractionResult.IGNORED;
        }
        
        @Override
        public InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context) {
            // 添加调试日志
            LOGGER.debug("CircleTool.onMouseMove: 接收到鼠标移动事件 - 屏幕位置={}", pos);
            
            // 总是进行捕捉检测，即使在绘制开始前
            SnapEnhancer.SnapResult snapResult = snapEnhancer.performEnhancedSnap(pos, context);
            currentMousePoint = snapResult.point;
            if (controlPoints.size() == 1) {
                currentMousePoint = constrainSecondPointToAxis(controlPoints.getFirst(), currentMousePoint);
            }
            
            // 如果还没有控制点，只显示捕捉效果，不更新预览
            if (controlPoints.isEmpty()) {
                // 更新捕捉状态以显示捕捉指示器
                isSnapping = snapResult.snapped;
                snapPoint = snapResult.snapped ? currentMousePoint : null;
                currentSnapType = snapResult.snapType;
                
                if (snapResult.snapped) {
                    LOGGER.trace("CircleTool.onMouseMove: 预绘制捕捉 - 位置={}, 类型={}", 
                               currentMousePoint, snapResult.snapType);
                }
                
                return InteractionResult.CONTINUE; // 返回CONTINUE以触发重绘
            }
            
            // 更新捕捉状态
            isSnapping = snapResult.snapped;
            snapPoint = snapResult.snapped ? currentMousePoint : null;
            currentSnapType = snapResult.snapType;
            
            if (snapResult.snapped) {
                LOGGER.trace("CircleTool.onMouseMove: 鼠标捕捉 - 位置={}, 类型={}", 
                           currentMousePoint, snapResult.snapType);
            }
            
            // 根据当前模式更新预览
            switch (currentMode) {
                case CENTER_RADIUS -> {
                    if (controlPoints.size() == 1) {
                        Vec2d center = controlPoints.getFirst();
                        double radius = center.distance(currentMousePoint);
                        updatePreview(center, radius);
                        return InteractionResult.CONTINUE;
                    }
                }
                case TWO_POINTS -> {
                    if (controlPoints.size() == 1) {
                        Vec2d p1 = controlPoints.getFirst();
                        Vec2d center = new Vec2d((p1.x + currentMousePoint.x) / 2, (p1.y + currentMousePoint.y) / 2);
                        double radius = p1.distance(center);
                        updatePreview(center, radius);
                        return InteractionResult.CONTINUE;
                    }
                }
                case THREE_POINTS -> {
                    if (controlPoints.size() == 2) {
                        Vec2d center = calculateCircleCenter(
                            controlPoints.get(0), 
                            controlPoints.get(1), 
                            currentMousePoint
                        );
                        
                        if (center != null) {
                            double radius = center.distance(controlPoints.getFirst());
                            updatePreview(center, radius);
                            return InteractionResult.CONTINUE;
                        }
                    }
                }
            }
            
            return InteractionResult.IGNORED;
        }
        
        @Override
        public InteractionResult onMouseUp(Vec2d pos, int button, DrawingToolContext context) {
            // 圆形工具使用点击模式，不需要处理鼠标释放
            return InteractionResult.IGNORED;
        }
        
        @Override
        public com.plot.core.model.Shape getFinalShape() {
            if (previewCircle != null) {
                // 使用预览圆的参数创建最终圆形
                CircleShape circle = new CircleShape(previewCircle.getCenter(), previewCircle.getRadius());
                
                // 应用样式
                com.plot.core.graphics.style.ShapeStyle style = getStyleHandler().getFinalStyle();
                if (style != null) {
                    circle.setStyle(style);
                }
                
                LOGGER.debug("CircleTool 创建圆形: 中心={}, 半径={:.2f}", 
                            previewCircle.getCenter(), previewCircle.getRadius());
                return circle;
            }
            
            return null;
        }
        
        @Override
        public void reset() {
            resetDrawingState();
        }
        
        @Override
        public String getStrategyName() {
            return "CircleInteractionStrategy";
        }
        
        @Override
        public String getStrategyDescription() {
            return "圆形工具多点点击交互策略，支持三种绘制模式";
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

    @Override
    protected com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy createStrategy(InteractionType type) {
        return new CircleInteractionStrategy();
    }
} 
