package com.masterplanner.ui.tools.impl.drawing;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.component.Icons;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import imgui.ImDrawList;
import com.masterplanner.ui.canvas.CanvasCamera;
import java.util.List;
import java.util.ArrayList;
import com.masterplanner.core.state.AppState;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.tool.ToolConfigEvent;
import com.masterplanner.core.geometry.shapes.PolylineShape;
import com.masterplanner.core.geometry.shapes.ArcShape;
import com.masterplanner.core.snap.SnapManager;
import java.util.Map;
import java.lang.reflect.Field;
import com.masterplanner.ui.tools.impl.drawing.strategy.IInteractionStrategy;

/**
 * 半圆工具 - 策略模式版本
 *
 * <p>支持两种绘制模式：</p>
 * <ul>
 *   <li>两点模式：第一点确定圆心，第二点确定半径和方向</li>
 *   <li>三点模式：第一点和第二点确定直径，第三点确定半圆的方向</li>
 * </ul>
 *
 * <p>功能特色：</p>
 * <ul>
 *   <li>完整的策略模式集成</li>
 *   <li>实时预览和吸附支持</li>
 *   <li>多种模式的配置管理</li>
 *   <li>详细的几何计算和角度处理</li>
 * </ul>
 *
 * @author MasterPlanner Team
 * @version 2.0 - 策略模式版本
 */
public class SemicircleTool extends DrawingTool {
    // 统一捕捉可视化
    private final com.masterplanner.ui.tools.snap.SnapEnhancer snapEnhancer =
            new com.masterplanner.ui.tools.snap.SnapEnhancer("SemicircleTool");
    private static final Logger LOGGER = LoggerFactory.getLogger(SemicircleTool.class);

    // ====== 枚举定义 ======

    /**
     * 半圆绘制模式
     */
    public enum SemicircleMode {
        TWO_POINTS("两点模式", "第一点确定圆心，第二点确定半径和方向"),
        THREE_POINTS("三点模式", "两点确定直径，第三点确定半圆方向");

        private final String displayName;
        private final String description;

        SemicircleMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    // ====== 配置常量 ======

    public static final String CONFIG_KEY_MODE = "mode";
    public static final String CONFIG_KEY_SEGMENTS = "segments";
    public static final String CONFIG_KEY_CLOCKWISE = "clockwise";

    public static final String CONFIG_MODE_TWO_POINTS = "two_points";
    public static final String CONFIG_MODE_THREE_POINTS = "three_points";

    // ====== 渲染常量 ======

    private static final int PREVIEW_COLOR = 0x80FFFFFF; // 白色半透明
    private static final int CENTER_POINT_COLOR = 0xFF0000FF; // 蓝色
    private static final int CONTROL_POINT_COLOR = 0xFF00FF00; // 绿色
    private static final int DIRECTION_POINT_COLOR = 0xFFFF0000; // 红色

    private static final float LINE_THICKNESS = 2.0f;
    private static final float POINT_SIZE = 5.0f;

    // ====== 状态字段 ======

    private SemicircleMode currentMode = SemicircleMode.TWO_POINTS;
    private int segments = 32;
    private boolean clockwise = true;

    // 绘制状态
    private final List<Vec2d> controlPoints = new ArrayList<>();
    private Vec2d currentMousePoint;
    private int currentStep = 0;
    private PolylineShape previewSemicircle;

    // 状态消息映射
    private static final Map<SemicircleMode, List<String>> MODE_STATUS_MESSAGES = Map.of(
        SemicircleMode.TWO_POINTS, List.of(
            "两点模式：点击确定半圆中心点",
            "点击确定半径和方向"
        ),
        SemicircleMode.THREE_POINTS, List.of(
            "三点模式：点击确定直径的第一个点",
            "点击确定直径的第二个点",
            "点击确定半圆的方向"
        )
    );

    // ====== 构造函数 ======

    /**
     * 依赖注入构造函数（推荐方式）
     */
    public SemicircleTool(AppState appState, SnapManager snapManager) {
        super("semicircle", "半圆", Icons.SEMICIRCLE_IDENTIFIER,
              "绘制半圆形状，支持多种绘制模式", appState, snapManager, InteractionType.CLICK_AND_CLICK);

        initializeSemicircleTool();
    }

    /**
     * 兼容性构造函数
     * @deprecated 推荐使用依赖注入构造函数
     */
    @Deprecated
    public SemicircleTool() {
        super("semicircle", "半圆", Icons.SEMICIRCLE_IDENTIFIER,
              "绘制半圆形状，支持多种绘制模式");

        initializeSemicircleTool();
    }

    // ====== 初始化方法 ======

    /**
     * 初始化半圆工具
     */
    private void initializeSemicircleTool() {
        // 使用反射设置自定义交互策略
        try {
            Field strategyField = DrawingTool.class.getDeclaredField("interactionStrategy");
            strategyField.setAccessible(true);
            strategyField.set(this, new SemicircleInteractionStrategy());
            LOGGER.debug("SemicircleTool: 自定义交互策略设置成功");
        } catch (Exception e) {
            LOGGER.error("SemicircleTool: 无法设置自定义交互策略", e);
        }

        // 订阅配置事件
        EventBus.getInstance().subscribe(ToolConfigEvent.class, event -> {
            if (event instanceof ToolConfigEvent toolConfigEvent &&
                getId().equals(toolConfigEvent.getToolId())) {
                updateConfig(toolConfigEvent.getOptionName(), String.valueOf(toolConfigEvent.getValue()));
            }
        });

        // 更新状态消息
        updateStatusMessageForCurrentMode();

        LOGGER.debug("SemicircleTool 初始化完成，模式: {}, 分段数: {}", currentMode, segments);
    }

    // ====== 状态管理方法 ======

    /**
     * 更新当前模式的状态消息
     */
    private void updateStatusMessageForCurrentMode() {
        List<String> messages = MODE_STATUS_MESSAGES.get(currentMode);
        if (messages != null) {
            int index = Math.min(controlPoints.size(), messages.size() - 1);
            String baseMessage = messages.get(index);

            // 添加实时信息
            String enhancedMessage = enhanceStatusMessage(baseMessage);
            setStatusMessage(enhancedMessage);
            LOGGER.debug("半圆工具状态消息已更新: {}", enhancedMessage);
        } else {
            setStatusMessage("半圆工具就绪");
        }
    }

    /**
     * 增强状态消息，添加实时反馈信息
     */
    private String enhanceStatusMessage(String baseMessage) {
        if (currentMousePoint == null) {
            return baseMessage;
        }

        StringBuilder enhanced = new StringBuilder(baseMessage);

        switch (currentMode) {
            case TWO_POINTS:
                if (controlPoints.size() == 1) {
                    Vec2d center = controlPoints.getFirst();
                    double radius = center.distance(currentMousePoint);
                    enhanced.append(String.format(" | 半径: %.2f", radius));
                }
                break;

            case THREE_POINTS:
                if (controlPoints.size() == 1) {
                    double distance = controlPoints.getFirst().distance(currentMousePoint);
                    enhanced.append(String.format(" | 距离: %.2f", distance));
                } else if (controlPoints.size() == 2) {
                    Vec2d p1 = controlPoints.get(0);
                    Vec2d p2 = controlPoints.get(1);
                    Vec2d center = new Vec2d((p1.x + p2.x) / 2.0, (p1.y + p2.y) / 2.0);
                    double radius = center.distance(p1);
                    enhanced.append(String.format(" | 半径: %.2f", radius));
                }
                break;
        }

        return enhanced.toString();
    }

    /**
     * 重置绘制状态
     */
    private void resetDrawingState() {
        controlPoints.clear();
        currentMousePoint = null;
        currentStep = 0;
        previewSemicircle = null;
        previewShape = null; // 清除预览形状
        updateStatusMessageForCurrentMode();
        LOGGER.debug("半圆工具状态已重置");
    }

    // ====== 基类重写方法 ======

    @Override
    public void onActivate() {
        LOGGER.debug("SemicircleTool activated");
        resetDrawingState();
        updateStatusMessageForCurrentMode();
    }

    @Override
    public void onDeactivate() {
        LOGGER.debug("SemicircleTool deactivated");
        resetDrawingState();
        setStatusMessage("");
    }

    @Override
    protected Shape createShape(Vec2d startPoint, Vec2d endPoint) {
        // 策略模式下，图形创建在策略中完成
        // 这里提供向后兼容的实现
        if (startPoint == null || endPoint == null) return null;

        // 两点模式：第一个点是圆心，第二个点是半径点
        double radius = startPoint.distance(endPoint);
        double midAngle = Math.atan2(endPoint.y - startPoint.y, endPoint.x - startPoint.x);

        // 计算半圆的起始角度和结束角度（中点角度 ± 90度）

        return getArcShape(startPoint, midAngle, radius);
    }

    private @NotNull ArcShape getArcShape(Vec2d startPoint, double midAngle, double radius) {
        double startAngle = midAngle - Math.PI / 2;
        double endAngle = midAngle + Math.PI / 2;

        // 如果是逆时针，交换角度
        if (!clockwise) {
            double temp = startAngle;
            startAngle = endAngle;
            endAngle = temp;
        }

        ArcShape semicircle = new ArcShape(startPoint, radius, startAngle, endAngle);

        ShapeStyle style = getStyleHandler().getFinalStyle();
        if (style != null) {
            semicircle.setStyle(style);
        }
        return semicircle;
    }

    @Override
    protected IInteractionStrategy createStrategy(InteractionType type) {
        // 如果有自定义 SemicircleInteractionStrategy 则返回，否则用多步策略
        return new SemicircleInteractionStrategy();
    }

    // ====== 配置管理 ======

    /**
     * 更新工具配置
     */
    @Override
    public void updateConfig(String key, String value) {
        switch (key) {
            case CONFIG_KEY_MODE -> {
                SemicircleMode newMode = parseMode(value);
                if (newMode != null && newMode != currentMode) {
                    currentMode = newMode;
                    resetDrawingState();
                    LOGGER.info("半圆工具模式切换到: {}", currentMode.getDisplayName());
                }
            }
            case CONFIG_KEY_SEGMENTS -> {
                try {
                    int newSegments = Integer.parseInt(value);
                    segments = Math.max(8, Math.min(72, newSegments));
                    LOGGER.debug("半圆工具段数更新为: {}", segments);
                } catch (NumberFormatException e) {
                    LOGGER.warn("无效的段数值: {}", value);
                }
            }
            case CONFIG_KEY_CLOCKWISE -> {
                try {
                    clockwise = Boolean.parseBoolean(value);
                    LOGGER.debug("半圆工具方向更新为: {}", clockwise ? "顺时针" : "逆时针");
                } catch (Exception e) {
                    LOGGER.warn("无效的方向值: {}", value);
                }
            }
            default -> LOGGER.debug("未知的配置键: {}", key);
        }
    }

    /**
     * 解析模式字符串
     */
    private SemicircleMode parseMode(String modeStr) {
        return switch (modeStr) {
            case CONFIG_MODE_TWO_POINTS -> SemicircleMode.TWO_POINTS;
            case CONFIG_MODE_THREE_POINTS -> SemicircleMode.THREE_POINTS;
            default -> {
                LOGGER.warn("未知的半圆模式: {}", modeStr);
                yield null;
            }
        };
    }

    // ====== 预览更新方法 ======

    /**
     * 更新预览
     */
    private void updatePreview() {
        switch (currentMode) {
            case TWO_POINTS -> updateTwoPointsPreview();
            case THREE_POINTS -> updateThreePointsPreview();
        }
    }

    /**
     * 更新预览形状（关键方法）
     */
    private void updatePreviewShape(PolylineShape semicircle) {
        if (semicircle != null) {
            // 应用预览样式
            com.masterplanner.core.graphics.style.ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
            if (previewStyle != null) {
                semicircle.setStyle(previewStyle);
            }

            // 设置为当前预览图形（这是关键！）
            previewShape = semicircle;
            previewSemicircle = semicircle;

            LOGGER.debug("SemicircleTool.updatePreviewShape: 更新预览形状，点数={}", semicircle.getPoints().size());
        } else {
            previewShape = null;
            previewSemicircle = null;
        }
    }

    /**
     * 更新两点模式预览
     */
    private void updateTwoPointsPreview() {
        if (controlPoints.isEmpty() || currentMousePoint == null) return;

        Vec2d center = controlPoints.getFirst();
        List<Vec2d> points = generateSemicirclePointsForTwoPoints(center, currentMousePoint);

        if (!points.isEmpty()) {
            PolylineShape semicircle = new PolylineShape(points, false);
            updatePreviewShape(semicircle);
        }
    }

    /**
     * 更新三点模式预览
     */
    private void updateThreePointsPreview() {
        if (controlPoints.size() < 2 || currentMousePoint == null) return;

        Vec2d p1 = controlPoints.get(0);
        Vec2d p2 = controlPoints.get(1);

        List<Vec2d> points = generateSemicirclePointsForThreePoints(p1, p2, currentMousePoint);

        if (!points.isEmpty()) {
            PolylineShape semicircle = new PolylineShape(points, false);
            updatePreviewShape(semicircle);
        }
    }

    // ====== 半圆生成方法 ======

    /**
     * 为两点模式生成半圆点 (已修正)
     * 第一个点是圆心，第二个点确定半径和半圆方向
     */
    private List<Vec2d> generateSemicirclePointsForTwoPoints(Vec2d center, Vec2d radiusPoint) {
        List<Vec2d> points = new ArrayList<>();

        if (center != null && radiusPoint != null) {
            double radius = center.distance(radiusPoint);
            double radiusAngle = Math.atan2(radiusPoint.y - center.y, radiusPoint.x - center.x);

            // 计算半圆的起始角度和结束角度
            // 半圆应该从半径角度的 -90度 到 +90度，或者根据 clockwise 设置调整
            double startAngle, endAngle;
            
            if (clockwise) {
                // 顺时针：从半径角度的 -90度 到 +90度
                startAngle = radiusAngle - Math.PI / 2;
                endAngle = radiusAngle + Math.PI / 2;
            } else {
                // 逆时针：从半径角度的 +90度 到 -90度
                startAngle = radiusAngle + Math.PI / 2;
                endAngle = radiusAngle - Math.PI / 2;
            }

            // 生成半圆点
            for (int i = 0; i <= segments; i++) {
                double t = i / (double) segments;
                double angle = startAngle + (endAngle - startAngle) * t;
                Vec2d point = new Vec2d(
                    center.x + radius * Math.cos(angle),
                    center.y + radius * Math.sin(angle)
                );
                points.add(point);
            }
        }

        return points;
    }

    /**
     * 为三点模式生成半圆点 (重新设计)
     * 前两个点确定半圆的两个端点（直径），第三个点确定半圆的方向
     * 
     * 新逻辑：
     * 1. p1 和 p2 是直径的两个端点
     * 2. directionPoint 确定半圆应该朝向哪一侧
     * 3. 半圆总是从 p1 到 p2，但根据 directionPoint 的位置选择正确的弧
     */
    private List<Vec2d> generateSemicirclePointsForThreePoints(Vec2d p1, Vec2d p2, Vec2d directionPoint) {
        List<Vec2d> points = new ArrayList<>();

        if (p1 != null && p2 != null && directionPoint != null) {
            // 1. 计算圆心和半径
            Vec2d center = new Vec2d((p1.x + p2.x) / 2.0, (p1.y + p2.y) / 2.0);
            double radius = center.distance(p1);

            // 2. 计算 p1 和 p2 的角度
            double angle1 = Math.atan2(p1.y - center.y, p1.x - center.x);
            double angle2 = Math.atan2(p2.y - center.y, p2.x - center.x);

            // 3. 使用叉乘判断方向点相对于直径的位置
            double crossProduct = getCrossProduct(p1, p2, directionPoint);

            double startAngle, endAngle;

            // 4. 修正角度选择逻辑
            // 鼠标在哪一侧，半圆就应该出现在哪一侧
            if (crossProduct > 0) {
                // directionPoint 在直径的左侧，半圆应该出现在左侧
                // 从 p1 到 p2 的顺时针弧（通过交换角度实现）
                startAngle = angle2;
                endAngle = angle1;
            } else {
                // directionPoint 在直径的右侧，半圆应该出现在右侧
                // 从 p1 到 p2 的逆时针弧
                startAngle = angle1;
                endAngle = angle2;
            }
            
            // 确保 endAngle > startAngle 以便绘制
            if (endAngle < startAngle) {
                endAngle += 2 * Math.PI;
            }

            // 5. 生成半圆点 - 从 startAngle 到 endAngle
            double sweepAngle = endAngle - startAngle;
            for (int i = 0; i <= segments; i++) {
                double t = i / (double) segments;
                double angle = startAngle + sweepAngle * t;
                Vec2d point = new Vec2d(
                    center.x + radius * Math.cos(angle),
                    center.y + radius * Math.sin(angle)
                );
                points.add(point);
            }
        }
        return points;
    }


    private static double getCrossProduct(Vec2d p1, Vec2d p2, Vec2d directionPoint) {
        // 1. 定义直径向量 (从 p1 到 p2)
        double dx_diameter = p2.x - p1.x;
        double dy_diameter = p2.y - p1.y;

        // 2. 定义方向向量 (从 p1 到鼠标位置)
        double dx_direction = directionPoint.x - p1.x;
        double dy_direction = directionPoint.y - p1.y;

        // 3. 计算二维叉乘 (z-component of 3D cross product)
        // 叉乘公式: (a × b)z = ax * by - ay * bx
        // 如果结果 > 0，则 directionPoint 在直径向量的"左侧" (逆时针方向)
        // 如果结果 < 0，则 directionPoint 在直径向量的"右侧" (顺时针方向)
        return dx_diameter * dy_direction - dy_diameter * dx_direction;
    }



    // ====== 预览渲染方法 ======

    @Override
    public void renderPreview(DrawContext context) {
        // 只在有控制点时才进行预览渲染
        if (!controlPoints.isEmpty()) {
            switch (currentMode) {
                case TWO_POINTS -> renderTwoPointsPreview(context);
                case THREE_POINTS -> renderThreePointsPreview(context);
            }

            // 渲染吸附指示器
            renderSnapIndicator(context);
        }
    }

    /**
     * 渲染两点模式预览
     */
    private void renderTwoPointsPreview(DrawContext context) {
        if (controlPoints.isEmpty()) return;

        // 获取预览颜色
        java.awt.Color centerColor = new java.awt.Color(0, 0, 255, 200); // 蓝色半透明
        java.awt.Color guideColor = new java.awt.Color(128, 128, 128, 150); // 灰色半透明

        // 绘制中心点
        Vec2d center = controlPoints.getFirst();
        context.drawCircleFilled(center, 5.0f, centerColor);

        // 如果有鼠标位置，绘制半圆预览
        if (currentMousePoint != null) {
            // 绘制半径线（引导线）
            context.drawLine(center, currentMousePoint, guideColor);

            // 绘制预览半圆
            if (previewSemicircle != null) {
                // 手动绘制半圆线段，使用图层颜色
                java.awt.Color layerPreviewColor = getPreviewColor();
                List<Vec2d> points = previewSemicircle.getPoints();
                for (int i = 1; i < points.size(); i++) {
                    context.drawLine(points.get(i - 1), points.get(i), layerPreviewColor);
                }
            }
        }
    }

    /**
     * 渲染三点模式预览
     */
    private void renderThreePointsPreview(DrawContext context) {
        if (controlPoints.isEmpty()) return;

        // 获取预览颜色
        java.awt.Color controlColor = new java.awt.Color(0, 255, 0, 200); // 绿色半透明
        java.awt.Color centerColor = new java.awt.Color(255, 0, 0, 200); // 红色半透明
        java.awt.Color guideColor = new java.awt.Color(128, 128, 128, 150); // 灰色半透明

        // 绘制第一个点
        Vec2d p1 = controlPoints.getFirst();
        context.drawCircleFilled(p1, 5.0f, controlColor);

        if (controlPoints.size() >= 2) {
            // 绘制第二个点和直径线
            Vec2d p2 = controlPoints.get(1);
            context.drawCircleFilled(p2, 5.0f, controlColor);
            context.drawLine(p1, p2, guideColor);

            // 绘制中心点
            Vec2d center = new Vec2d((p1.x + p2.x) / 2.0, (p1.y + p2.y) / 2.0);
            context.drawCircleFilled(center, 5.0f, centerColor);

            // 如果有鼠标位置，绘制半圆预览
            if (currentMousePoint != null) {
                // 绘制方向线
                context.drawLine(center, currentMousePoint, guideColor);

                // 绘制预览半圆
                if (previewSemicircle != null) {
                    // 手动绘制半圆线段，使用图层颜色
                    java.awt.Color layerPreviewColor = getPreviewColor();
                    List<Vec2d> points = previewSemicircle.getPoints();
                    for (int i = 1; i < points.size(); i++) {
                        context.drawLine(points.get(i - 1), points.get(i), layerPreviewColor);
                    }
                }
            }
        } else if (currentMousePoint != null) {
            // 只有一个点时，显示引导线
            context.drawLine(p1, currentMousePoint, guideColor);
        }
    }

    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        // 只在有控制点时才进行预览渲染
        if (!controlPoints.isEmpty()) {
            switch (currentMode) {
                case TWO_POINTS -> renderTwoPointsPreviewImGui(drawList, camera);
                case THREE_POINTS -> renderThreePointsPreviewImGui(drawList, camera);
            }

            // 渲染控制点
            renderControlPoints(drawList, camera);

            // 渲染鼠标位置的吸附指示器
            renderMouseSnapIndicator(drawList, camera);
        }
    }

    /**
     * 渲染两点模式预览（ImGui版本）
     */
    private void renderTwoPointsPreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        if (controlPoints.isEmpty()) return;

        // 绘制中心点
        Vec2d center = controlPoints.getFirst();
        Vec2d screenCenter = camera.worldToScreen(center);
        drawList.addCircleFilled(
            (float) screenCenter.x, (float) screenCenter.y,
            POINT_SIZE, CENTER_POINT_COLOR
        );

        // 如果有鼠标位置，绘制半圆预览
        if (currentMousePoint != null) {
            Vec2d screenMouse = camera.worldToScreen(currentMousePoint);

            // 绘制半径线（引导线）
            int guideColor = 0x96808080; // 灰色半透明
            drawList.addLine(
                (float) screenCenter.x, (float) screenCenter.y,
                (float) screenMouse.x, (float) screenMouse.y,
                guideColor, LINE_THICKNESS * 0.8f
            );

            // 绘制预览半圆
            if (previewSemicircle != null) {
                // 使用图层颜色作为预览颜色
                java.awt.Color layerPreviewColor = getPreviewColor();
                int previewColor = imgui.ImColor.rgba(
                    layerPreviewColor.getRed(),
                    layerPreviewColor.getGreen(),
                    layerPreviewColor.getBlue(),
                    layerPreviewColor.getAlpha()
                );
                renderSemicircleImGui(drawList, camera, previewSemicircle.getPoints(), previewColor);
            }

            // 显示半径信息
            double radius = center.distance(currentMousePoint);
            String info = String.format("半径: %.2f", radius);
            drawList.addText(
                (float) screenCenter.x + 10, (float) screenCenter.y + 10,
                PREVIEW_COLOR, info
            );
        }
    }

    /**
     * 渲染三点模式预览（ImGui版本）
     */
    private void renderThreePointsPreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        if (controlPoints.isEmpty()) return;

        // 绘制第一个点
        Vec2d p1 = controlPoints.getFirst();
        Vec2d screenP1 = camera.worldToScreen(p1);
        drawList.addCircleFilled(
            (float) screenP1.x, (float) screenP1.y,
            POINT_SIZE, CONTROL_POINT_COLOR
        );

        if (controlPoints.size() >= 2) {
            // 绘制第二个点和直径线
            Vec2d p2 = controlPoints.get(1);
            Vec2d screenP2 = camera.worldToScreen(p2);
            drawList.addCircleFilled(
                (float) screenP2.x, (float) screenP2.y,
                POINT_SIZE, CONTROL_POINT_COLOR
            );

            // 绘制直径线（引导线）
            int guideColor = 0x96808080; // 灰色半透明
            drawList.addLine(
                (float) screenP1.x, (float) screenP1.y,
                (float) screenP2.x, (float) screenP2.y,
                guideColor, LINE_THICKNESS * 0.8f
            );

            // 绘制中心点
            Vec2d center = new Vec2d((p1.x + p2.x) / 2.0, (p1.y + p2.y) / 2.0);
            Vec2d screenCenter = camera.worldToScreen(center);
            drawList.addCircleFilled(
                (float) screenCenter.x, (float) screenCenter.y,
                POINT_SIZE, DIRECTION_POINT_COLOR
            );

            // 如果有鼠标位置，绘制半圆预览
            if (currentMousePoint != null) {
                Vec2d screenMouse = camera.worldToScreen(currentMousePoint);

                // 绘制方向线
                drawList.addLine(
                    (float) screenCenter.x, (float) screenCenter.y,
                    (float) screenMouse.x, (float) screenMouse.y,
                    guideColor, LINE_THICKNESS * 0.8f
                );

                // 绘制预览半圆
                if (previewSemicircle != null) {
                    // 使用图层颜色作为预览颜色
                    java.awt.Color layerPreviewColor = getPreviewColor();
                    int previewColor = imgui.ImColor.rgba(
                        layerPreviewColor.getRed(),
                        layerPreviewColor.getGreen(),
                        layerPreviewColor.getBlue(),
                        layerPreviewColor.getAlpha()
                    );
                    renderSemicircleImGui(drawList, camera, previewSemicircle.getPoints(), previewColor);
                }

                // 显示半径信息
                double radius = center.distance(p1);
                String info = String.format("半径: %.2f", radius);
                drawList.addText(
                    (float) screenCenter.x + 10, (float) screenCenter.y + 10,
                    PREVIEW_COLOR, info
                );
            }
        } else if (currentMousePoint != null) {
            // 只有一个点时，显示引导线
            Vec2d screenMouse = camera.worldToScreen(currentMousePoint);
            int guideColor = 0x96808080; // 灰色半透明
            drawList.addLine(
                (float) screenP1.x, (float) screenP1.y,
                (float) screenMouse.x, (float) screenMouse.y,
                guideColor, LINE_THICKNESS * 0.8f
            );
        }
    }

    /**
     * 渲染半圆（ImGui版本）
     */
    private void renderSemicircleImGui(ImDrawList drawList, CanvasCamera camera, List<Vec2d> points, int color) {
        if (points.size() < 2) return;

        for (int i = 1; i < points.size(); i++) {
            Vec2d screenPrev = camera.worldToScreen(points.get(i - 1));
            Vec2d screenCurr = camera.worldToScreen(points.get(i));

            drawList.addLine(
                (float) screenPrev.x, (float) screenPrev.y,
                (float) screenCurr.x, (float) screenCurr.y,
                color, LINE_THICKNESS
            );
        }
    }

    /**
     * 渲染控制点
     */
    private void renderControlPoints(ImDrawList drawList, CanvasCamera camera) {
        for (int i = 0; i < controlPoints.size(); i++) {
            Vec2d point = controlPoints.get(i);
            Vec2d screenPoint = camera.worldToScreen(point);

            // 根据点的角色使用不同颜色
            int pointColor;
            if (currentMode == SemicircleMode.TWO_POINTS && i == 0) {
                pointColor = CENTER_POINT_COLOR; // 中心点为蓝色
            } else {
                pointColor = CONTROL_POINT_COLOR; // 控制点为绿色
            }

            drawList.addCircleFilled(
                (float) screenPoint.x, (float) screenPoint.y,
                POINT_SIZE, pointColor
            );

            // 添加边框以便更清晰地看到点
            drawList.addCircle(
                (float) screenPoint.x, (float) screenPoint.y,
                POINT_SIZE + 1, 0xFF000000, 12, 1.0f // 黑色边框
            );
        }
    }

    /**
     * 渲染鼠标位置的吸附指示器
     */
    private void renderMouseSnapIndicator(ImDrawList drawList, CanvasCamera camera) {
        // 统一渲染捕捉指示器
        snapEnhancer.renderSnapIndicator(drawList, camera);
    }

    /**
     * 渲染吸附指示器（DrawContext版本）
     */
    private void renderSnapIndicator(DrawContext context) {
        // 统一渲染捕捉指示器
        snapEnhancer.renderSnapIndicator(context);
    }

    /**
     * 获取预览颜色（使用当前图层颜色）
     */
    private java.awt.Color getPreviewColor() {
        try {
            com.masterplanner.core.graphics.style.ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
            if (previewStyle != null && previewStyle.getLineColor() != null) {
                java.awt.Color layerColor = previewStyle.getLineColor();
                // 添加半透明效果用于预览
                return new java.awt.Color(layerColor.getRed(), layerColor.getGreen(),
                                       layerColor.getBlue(), 200);
            }
        } catch (Exception e) {
            LOGGER.warn("SemicircleTool: 获取预览颜色失败: {}", e.getMessage());
        }

        // 默认颜色：白色半透明
        return new java.awt.Color(255, 255, 255, 200);
    }

    /**
     * 重写shouldShowPreview方法，确保在有控制点或预览形状时显示预览
     */
    @Override
    protected boolean shouldShowPreview() {
        return !controlPoints.isEmpty() || previewShape != null;
    }

    // ====== 自定义交互策略 ======

    /**
     * 半圆工具专用交互策略
     * 支持两种绘制模式的多点点击交互
     */
    private class SemicircleInteractionStrategy implements com.masterplanner.ui.tools.impl.drawing.strategy.IInteractionStrategy {

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
            LOGGER.debug("SemicircleTool.onMouseDown: 点击位置={}, 转换后={}", pos, worldPoint);

            // 根据当前模式处理点击
            controlPoints.add(worldPoint);
            currentStep++;

            // 检查是否完成绘制
            boolean isComplete = false;

            switch (currentMode) {
                case TWO_POINTS:
                    if (currentStep >= 2) {
                        isComplete = true;
                    }
                    break;
                case THREE_POINTS:
                    if (currentStep >= 3) {
                        isComplete = true;
                    }
                    break;
            }

            if (isComplete) {
                return InteractionResult.COMPLETE;
            } else {
                updateStatusMessageForCurrentMode();
                return InteractionResult.CONTINUE;
            }
        }

        @Override
        public InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context) {
            // 即使没有控制点，也要更新鼠标位置以显示吸附效果
            var snap = snapEnhancer.performEnhancedSnap(pos, context);
            currentMousePoint = snap.point;

            if (controlPoints.isEmpty()) {
                return InteractionResult.CONTINUE;
            }

            // 更新预览和状态消息
            updatePreview();
            updateStatusMessageForCurrentMode();

            return InteractionResult.CONTINUE;
        }

        @Override
        public InteractionResult onMouseUp(Vec2d pos, int button, DrawingToolContext context) {
            // 半圆工具使用点击模式，不需要处理鼠标释放
            return InteractionResult.IGNORED;
        }

    @Override
        public com.masterplanner.core.model.Shape getFinalShape() {
            if (previewSemicircle != null && !previewSemicircle.getPoints().isEmpty()) {
                // 从半圆点计算圆弧参数
                List<Vec2d> points = previewSemicircle.getPoints();
                Vec2d firstPoint = points.getFirst();
                Vec2d lastPoint = points.getLast();

                // 根据模式计算圆弧参数
                Vec2d center;
                double radius;
                double startAngle;
                double endAngle;

                if (currentMode == SemicircleMode.TWO_POINTS && controlPoints.size() >= 2) {
                    // 两点模式：第一个点是圆心，第二个点是半径点
                    center = controlPoints.get(0);
                    Vec2d radiusPoint = controlPoints.get(1);
                    radius = center.distance(radiusPoint);
                    double midAngle = Math.atan2(radiusPoint.y - center.y, radiusPoint.x - center.x);

                    // 计算半圆的起始角度和结束角度（中点角度 ± 90度）
                    startAngle = midAngle - Math.PI / 2;
                    endAngle = midAngle + Math.PI / 2;

                    // 如果是逆时针，交换角度
                    if (!clockwise) {
                        double temp = startAngle;
                        startAngle = endAngle;
                        endAngle = temp;
                    }
                } else if (currentMode == SemicircleMode.THREE_POINTS && controlPoints.size() >= 3) {
                    // 三点模式：前两个点确定半圆的两个端点（直径），第三个点确定半圆方向
                    Vec2d p1 = controlPoints.get(0);
                    Vec2d p2 = controlPoints.get(1);
                    Vec2d directionPoint = controlPoints.get(2);

                    // 计算直径的中点作为圆心
                    center = new Vec2d((p1.x + p2.x) / 2.0, (p1.y + p2.y) / 2.0);
                    radius = center.distance(p1);

                    // 计算 p1 和 p2 的角度
                    double angle1 = Math.atan2(p1.y - center.y, p1.x - center.x);
                    double angle2 = Math.atan2(p2.y - center.y, p2.x - center.x);

                    // 使用叉乘判断第三个点相对于直径的位置
                    double crossProduct = getCrossProduct(p1, p2, directionPoint);

                    // 根据方向确定起始和结束角度
                    // 鼠标在哪一侧，半圆就应该出现在哪一侧
                    if (crossProduct > 0) {
                        // directionPoint 在直径的左侧，半圆应该出现在左侧
                        // 从 p1 到 p2 的顺时针弧（通过交换角度实现）
                        startAngle = angle2;
                        endAngle = angle1;
                    } else {
                        // directionPoint 在直径的右侧，半圆应该出现在右侧
                        // 从 p1 到 p2 的逆时针弧
                        startAngle = angle1;
                        endAngle = angle2;
                    }
                    
                    // 确保 endAngle > startAngle 以便绘制
                    if (endAngle < startAngle) {
                        endAngle += 2 * Math.PI;
                    }
                } else {
                    // 回退到从点计算（用于预览）
                    center = new Vec2d(
                        (firstPoint.x + lastPoint.x) / 2.0,
                        (firstPoint.y + lastPoint.y) / 2.0
                    );
                    radius = center.distance(firstPoint);
                    startAngle = Math.atan2(firstPoint.y - center.y, firstPoint.x - center.x);
                    endAngle = startAngle + Math.PI;
                }

                ArcShape semicircle = new ArcShape(center, radius, startAngle, endAngle);

                ShapeStyle style = getStyleHandler().getFinalStyle();
                if (style != null) {
                    semicircle.setStyle(style);
                }

                LOGGER.debug("SemicircleTool 创建半圆: 模式={}, 中心=({:.2f}, {:.2f}), 半径={:.2f}, 角度={:.2f}-{:.2f}",
                            currentMode.getDisplayName(), center.x, center.y, radius,
                            Math.toDegrees(startAngle), Math.toDegrees(endAngle));
                return semicircle;
            }

            return null;
    }

        @Override
        public void reset() {
            resetDrawingState();
        }

        @Override
        public String getStrategyName() {
            return "SemicircleInteractionStrategy";
        }

        @Override
        public String getStrategyDescription() {
            return "半圆工具多点点击交互策略，支持两种绘制模式";
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