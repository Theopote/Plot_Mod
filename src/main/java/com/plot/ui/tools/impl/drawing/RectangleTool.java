package com.plot.ui.tools.impl.drawing;

import com.plot.api.geometry.Vec2d;
import com.plot.api.state.IAppState;
import com.plot.api.snap.ISnapManager;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.graphics.DrawContext;
import com.plot.core.model.Shape;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import com.plot.ui.component.Icons;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.theme.ThemeManager;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.utils.PlotI18n;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy;
import com.plot.ui.tools.snap.SnapEnhancer;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.api.graphics.IShapeStyle;

/**
 * 矩形工具类 - 现代化策略模式实现
 *
 * <p>支持四种绘制模式：</p>
 * <ul>
 *   <li>两点模式：通过对角两点确定矩形</li>
 *   <li>三点模式：通过底边两点和高度点确定矩形</li>
 *   <li>中心点模式：通过中心点和角点确定矩形</li>
 *   <li>圆角模式：在两点模式基础上支持圆角设置</li>
 * </ul>
 *
 * @author Plot Team
 * @version 4.0 - 策略模式集成版本
 */
public class RectangleTool extends DrawingTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(RectangleTool.class);

    // 配置键常量
    private static class ConfigKeys {
        static final String TYPE = "type";
        static final String FORCE_SQUARE = "force_square";
        static final String SHOW_GRID = "show_grid";
        static final String SHOW_AXES = "show_axes";
    }

    // 矩形绘制模式枚举
    public enum RectangleMode {
        TWO_POINTS("two_points", "mode.plot.two_points", "mode.plot.rect.two_points.desc"),
        THREE_POINTS("three_points", "mode.plot.three_points", "mode.plot.rect.three_points.desc"),
        CENTER_POINT("center_point", "mode.plot.center_point", "mode.plot.rect.center_point.desc"),
        ROUNDED("rounded", "mode.plot.rounded_corner", "mode.plot.rect.rounded.desc");

        public final String id;
        private final String nameKey;
        private final String descKey;

        RectangleMode(String id, String nameKey, String descKey) {
            this.id = id;
            this.nameKey = nameKey;
            this.descKey = descKey;
        }

        public String getDisplayName() { return PlotI18n.modeLabel(nameKey); }
        public String getDescription() { return PlotI18n.modeLabel(descKey); }

        public static RectangleMode fromId(String id) {
            for (RectangleMode mode : values()) {
                if (mode.id.equals(id)) return mode;
            }
            return TWO_POINTS;
        }
    }

    // 当前配置
    private RectangleMode currentMode = RectangleMode.TWO_POINTS;
    private boolean forceSquare = false;
    private static final int SHIFT_KEY = 16;
    private volatile boolean shiftPressed = false;

    // 绘制状态
    private List<Vec2d> controlPoints = new ArrayList<>();
    private RectangleShape previewRectangle;
    private Vec2d currentMousePoint;

    // 捕捉增强器
    private final SnapEnhancer snapEnhancer = new SnapEnhancer("RectangleTool");

    /**
     * 推荐构造函数（依赖注入）
     */
    public RectangleTool(IAppState appState, ISnapManager snapManager) {
        super("rectangle", "矩形工具", Icons.RECTANGLE_IDENTIFIER, "绘制矩形",
                appState, snapManager, InteractionType.CLICK_AND_CLICK);

        // 监听配置事件
        eventBus.subscribe(ToolConfigEvent.class, this::handleConfigEvent);

        initializeRectangleTool();
        LOGGER.debug("矩形工具已初始化，当前模式: {}", currentMode.getDisplayName());
    }

    /**
     * 兼容构造函数
     */
    @Deprecated
    public RectangleTool() {
        super("rectangle", "矩形工具", Icons.RECTANGLE_IDENTIFIER, "绘制矩形");

        // 监听配置事件
        eventBus.subscribe(ToolConfigEvent.class, this::handleConfigEvent);

        initializeRectangleTool();
        LOGGER.debug("矩形工具已初始化（兼容模式），当前模式: {}", currentMode.getDisplayName());
    }

    /**
     * 通用初始化逻辑
     */
    private void initializeRectangleTool() {
        resetDrawingState();
        updateStatusMessage();
    }

    private boolean isSquareConstraintActive() {
        return forceSquare || shiftPressed;
    }

    // ====== 配置事件处理 ======

    /**
     * 处理配置事件
     */
    private void handleConfigEvent(Object eventObj) {
        ToolConfigEvent event = (ToolConfigEvent) eventObj;
        if (!"rectangle".equals(event.getToolId())) return;

        boolean showGrid = false;
        boolean showAxes = false;
        switch (event.getOptionName()) {
            case ConfigKeys.TYPE -> {
                RectangleMode newMode = RectangleMode.fromId(String.valueOf(event.getValue()));
                setMode(newMode);
            }

            case ConfigKeys.FORCE_SQUARE -> setForceSquare(Boolean.parseBoolean(String.valueOf(event.getValue())));
            case ConfigKeys.SHOW_GRID -> showGrid = Boolean.parseBoolean(String.valueOf(event.getValue()));
            case ConfigKeys.SHOW_AXES -> showAxes = Boolean.parseBoolean(String.valueOf(event.getValue()));
        }
    }

    // ====== 模式和配置设置 ======

    public void setMode(RectangleMode mode) {
        if (this.currentMode != mode) {
            this.currentMode = mode;
            resetDrawingState();
            updateStatusMessage();
            LOGGER.debug("矩形工具模式已切换为: {}", mode.getDisplayName());
        }
    }



    public void setForceSquare(boolean forceSquare) {
        this.forceSquare = forceSquare;
        updatePreview();
    }

    @Override
    public boolean onKeyDown(int keyCode) {
        if (keyCode == SHIFT_KEY) {
            shiftPressed = true;
            updatePreview();
            return true;
        }
        return super.onKeyDown(keyCode);
    }

    @Override
    public boolean onKeyUp(int keyCode) {
        if (keyCode == SHIFT_KEY) {
            shiftPressed = false;
            updatePreview();
            return true;
        }
        return super.onKeyUp(keyCode);
    }

    private void updateStatusMessage() {
        String message = switch (currentMode) {
            case TWO_POINTS -> controlPoints.isEmpty() ? "status.plot.draw.rect.click_first_corner" : "status.plot.draw.rect.click_second_corner";
            case THREE_POINTS -> switch (controlPoints.size()) {
                case 0 -> "status.plot.draw.rect.click_base_first";
                case 1 -> "status.plot.draw.rect.click_base_second";
                case 2 -> "status.plot.draw.rect.click_height";
                default -> currentMode.getDescription();
            };
            case CENTER_POINT -> controlPoints.isEmpty() ? "status.plot.draw.rect.click_center" : "status.plot.draw.rect.click_corner";
            case ROUNDED -> switch (controlPoints.size()) {
                case 0 -> "status.plot.draw.rect.click_first_corner";
                case 1 -> "status.plot.draw.rect.click_second_corner";
                case 2 -> "status.plot.draw.rect.drag_rounded";
                default -> currentMode.getDescription();
            };
        };
        setStatusMessage(message);
    }

    // ====== 绘制状态管理 ======

    private void resetDrawingState() {
        controlPoints.clear();
        previewRectangle = null;
        currentMousePoint = null;
        snapEnhancer.reset(); // 重置捕捉状态
        updateStatusMessage();
        markDirty();
    }

    // ====== DrawingTool基类抽象方法实现 ======

    // ====== 无填充样式支持 ======

    /**
     * 创建无填充的样式（符合用户偏好：只绘制轮廓不填充）
     * @param isPreview 是否为预览样式
     * @return 无填充的形状样式
     */
    private ShapeStyle createNoFillStyle(boolean isPreview) {
        try {
            // 获取基础样式（预览或最终样式）
            ShapeStyle baseStyle = isPreview ? getStyleHandler().getPreviewStyle() : getStyleHandler().getFinalStyle();

            // 克隆样式以避免修改原始样式
            ShapeStyle noFillStyle = (ShapeStyle) baseStyle.clone();

            // 设置无填充：透明颜色且不可见
            noFillStyle.setFillColor(new java.awt.Color(0, 0, 0, 0)); // 完全透明
            if (noFillStyle.getFillStyle() != null) {
                noFillStyle.getFillStyle().setOpacity(0.0f);  // 透明度为0
                noFillStyle.getFillStyle().setVisible(false); // 填充不可见
            }

            // 确保轮廓可见（用户希望看到轮廓）
            if (noFillStyle.getLineStyle() != null) {
                noFillStyle.getLineStyle().setVisible(true);
            }

            LOGGER.debug("RectangleTool: 创建无填充样式 - 预览模式: {}, 填充可见性: {}",
                    isPreview, noFillStyle.getFillStyle() != null ? noFillStyle.getFillStyle().isVisible() : "null");
            return noFillStyle;

        } catch (Exception e) {
            LOGGER.error("RectangleTool: 创建无填充样式失败，使用fallback样式", e);
            // fallback：创建完全自定义的无填充样式
            return createFallbackNoFillStyle();
        }
    }

    /**
     * 创建fallback无填充样式（完全自定义）
     */
    private ShapeStyle createFallbackNoFillStyle() {
        ShapeStyle style = new ShapeStyle();

        // 设置线条样式
        com.plot.core.graphics.style.LineStyle lineStyle =
                new com.plot.core.graphics.style.LineStyle();
        lineStyle.setColor(toColor(ThemeManager.getInstance().getCurrentTheme().accent, 255));
        lineStyle.setWidth(2.0f);
        lineStyle.setVisible(true);
        style.setLineStyle((com.plot.api.graphics.ILineStyle) lineStyle);

        // 设置无填充样式
        com.plot.core.graphics.style.FillStyle fillStyle =
                new com.plot.core.graphics.style.FillStyle();
        fillStyle.setColor(new java.awt.Color(0, 0, 0, 0)); // 完全透明
        fillStyle.setOpacity(0.0f);
        fillStyle.setVisible(false); // 关键：填充不可见
        style.setFillStyle((com.plot.api.graphics.IFillStyle) fillStyle);

        LOGGER.debug("RectangleTool: 创建fallback无填充样式");
        return style;
    }

    /**
     * 为图形应用无填充样式
     * @param shape 要应用样式的图形
     * @param isPreview 是否为预览模式
     */
    private void applyNoFillStyle(Shape shape, boolean isPreview) {
        if (shape == null) return;

        try {
            // 创建并应用无填充样式
            ShapeStyle noFillStyle = createNoFillStyle(isPreview);
            shape.setStyle(noFillStyle);

            // 双重保险：直接检查和修正图形的样式
            IShapeStyle currentStyle = shape.getStyle();
            if (currentStyle instanceof ShapeStyle shapeStyle) {
                // 强制设置填充不可见
                if (shapeStyle.getFillStyle() != null) {
                    shapeStyle.getFillStyle().setVisible(false);
                    shapeStyle.getFillStyle().setOpacity(0.0f);
                    shapeStyle.setFillColor(new java.awt.Color(0, 0, 0, 0));
                }

                LOGGER.debug("RectangleTool: 强制应用无填充样式 - 图形: {}, 预览: {}, 填充可见: {}",
                        shape.getClass().getSimpleName(), isPreview,
                        shapeStyle.getFillStyle() != null ? shapeStyle.getFillStyle().isVisible() : "null");
            }

        } catch (Exception e) {
            LOGGER.error("RectangleTool: 应用无填充样式失败", e);
            // 最后的fallback：直接创建新样式
            forceNoFillStyle(shape);
        }
    }

    /**
     * 强制设置无填充样式（最终fallback）
     */
    private void forceNoFillStyle(Shape shape) {
        if (shape == null) return;

        ShapeStyle forcedStyle = createFallbackNoFillStyle();
        shape.setStyle(forcedStyle);

        LOGGER.warn("RectangleTool: 强制设置fallback无填充样式 - {}", shape.getClass().getSimpleName());
    }

    /**
     * 强制应用无填充样式（最高优先级）
     * 确保图形绝对不会有填充，无论其他任何设置
     */
    private void forceApplyNoFillStyle(Shape shape, boolean isPreview) {
        if (shape == null) return;

        // 第一步：先应用正常的无填充样式
        applyNoFillStyle(shape, isPreview);

        // 第二步：强制验证和修正
        IShapeStyle currentStyle = shape.getStyle();
        if (currentStyle instanceof ShapeStyle shapeStyle) {
            // 绝对确保填充不可见
            if (shapeStyle.getFillStyle() != null) {
                shapeStyle.getFillStyle().setVisible(false);
                shapeStyle.getFillStyle().setOpacity(0.0f);
                shapeStyle.setFillColor(new java.awt.Color(0, 0, 0, 0));
            } else {
                LOGGER.warn("RectangleTool: 图形样式没有填充样式对象 - {}", shape.getClass().getSimpleName());
            }

            // 关键：标记为不跟随图层样式，防止图层覆盖我们的设置
            shapeStyle.setFollowsLayerStyle(false);

            LOGGER.info("RectangleTool: 强制确保无填充 - 图形: {}, 填充可见: {}, 跟随图层: {}",
                    shape.getClass().getSimpleName(),
                    shapeStyle.getFillStyle() != null ? shapeStyle.getFillStyle().isVisible() : "null",
                    shapeStyle.doesFollowLayerStyle());
        } else {
            LOGGER.error("RectangleTool: 图形样式不是ShapeStyle类型 - {}, 实际类型: {}",
                    shape.getClass().getSimpleName(),
                    currentStyle != null ? currentStyle.getClass().getSimpleName() : "null");
        }
    }

    @Override
    protected Shape createShape(Vec2d startPoint, Vec2d endPoint) {
        Shape shape = null;

        if (currentMode == RectangleMode.THREE_POINTS) {
            // 三点模式：创建Polygon对象
            Vec2d[] vertices = calculateThreePointVertices();
            if (vertices != null) {
                shape = new Polygon(Arrays.asList(vertices));
            }
        } else if (currentMode == RectangleMode.ROUNDED) {
            // 圆角模式：需要三个点，这里只处理前两个点的情况
            // 实际的圆角矩形创建在交互策略中处理
            return null;
        } else {
            // 其他模式：创建RectangleShape对象
            double x1 = Math.min(startPoint.x, endPoint.x);
            double y1 = Math.min(startPoint.y, endPoint.y);
            double x2 = Math.max(startPoint.x, endPoint.x);
            double y2 = Math.max(startPoint.y, endPoint.y);

            // 应用正方形约束（支持Shift临时启用）
            if (isSquareConstraintActive()) {
                double size = Math.max(x2 - x1, y2 - y1);
                x2 = x1 + size;
                y2 = y1 + size;
            }

            Vec2d corner = new Vec2d(x1, y1);
            double width = x2 - x1;
            double height = y2 - y1;

            // 创建矩形
            shape = new RectangleShape(corner, width, height, 0.0);
        }

        // 为所有图形统一强制设置无填充样式
        if (shape != null) {
            forceApplyNoFillStyle(shape, false);

            // 确保图形不跟随图层样式，防止被图层覆盖
            if (shape.getStyle() instanceof ShapeStyle shapeStyle) {
                shapeStyle.setFollowsLayerStyle(false);
                LOGGER.debug("RectangleTool.createShape: 设置图形不跟随图层样式 - 模式: {}", currentMode.getDisplayName());
            }
        }

        LOGGER.debug("RectangleTool.createShape: 创建矩形 - 模式: {}, 图形类型: {}",
                currentMode.getDisplayName(), shape != null ? shape.getClass().getSimpleName() : "null");

        return shape;
    }

    @Override
    public void updateConfig(String key, String value) {
        switch (key) {
            case ConfigKeys.TYPE -> {
                RectangleMode newMode = RectangleMode.fromId(value);
                setMode(newMode);
            }

            case ConfigKeys.FORCE_SQUARE -> setForceSquare(Boolean.parseBoolean(value));
            default -> LOGGER.debug("矩形工具收到未知配置: {} = {}", key, value);
        }
    }

    @Override
    public void renderPreview(DrawContext context) {
        renderControlPoints(context);

        // 三点模式：绘制辅助线
        if (currentMode == RectangleMode.THREE_POINTS && controlPoints.size() == 2 && currentMousePoint != null) {
            var theme = ThemeManager.getInstance().getCurrentTheme();
            Vec2d baseStart = controlPoints.get(0);
            Vec2d baseEnd = controlPoints.get(1);
            context.drawLine(baseStart, baseEnd, toColor(theme.mutedText, 220));
            Vec2d projectedPoint = projectPointToBase(baseStart, baseEnd, currentMousePoint);
            context.drawLine(currentMousePoint, projectedPoint, toColor(theme.infoText, 230));
        }

        // 圆角模式：绘制圆角半径指示线
        if (currentMode == RectangleMode.ROUNDED && controlPoints.size() == 2 && currentMousePoint != null) {
            var theme = ThemeManager.getInstance().getCurrentTheme();
            Vec2d p1 = controlPoints.get(0);
            Vec2d p2 = controlPoints.get(1);
            Vec2d center = new Vec2d((p1.x + p2.x) / 2, (p1.y + p2.y) / 2);

            // 绘制从中心到第三点的连线，表示圆角半径
            context.drawLine(center, currentMousePoint, toColor(theme.warningText, 230));

            // 绘制圆角半径文本
            double radius = calculateCornerRadiusFromThirdPoint(p1, p2, currentMousePoint);
            context.drawText(PlotI18n.status("status.plot.draw.radius_label", radius), currentMousePoint, toColor(theme.warningText, 230));
        }

        if (previewShape != null) {
            // 确保预览图形强制应用无填充样式
            forceApplyNoFillStyle(previewShape, true);
            previewShape.render(context);
        }

        // 渲染捕捉指示器
        snapEnhancer.renderSnapIndicator(context);
    }

    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (previewRectangle == null || camera == null) return;
        var theme = ThemeManager.getInstance().getCurrentTheme();

        // 获取矩形顶点并转换为屏幕坐标
        Vec2d[] vertices = getRectangleVertices();
        if (vertices.length >= 4) {
            Vec2d[] screenVertices = new Vec2d[4];
            for (int i = 0; i < 4; i++) {
                screenVertices[i] = camera.worldToScreen(vertices[i]);
            }

            // 绘制矩形轮廓
                int color = withAlpha(theme.accent, 0xAA);
            drawList.addQuad(
                    (float) screenVertices[0].x, (float) screenVertices[0].y,
                    (float) screenVertices[1].x, (float) screenVertices[1].y,
                    (float) screenVertices[2].x, (float) screenVertices[2].y,
                    (float) screenVertices[3].x, (float) screenVertices[3].y,
                    color, 2.0f
            );

            // 绘制控制点
            for (Vec2d point : controlPoints) {
                Vec2d screenPoint = camera.worldToScreen(point);
                drawList.addCircleFilled(
                        (float) screenPoint.x, (float) screenPoint.y, 4.0f,
                    withAlpha(theme.errorText, 0xCC)
                );
            }
        }

        // 渲染捕捉指示器
        snapEnhancer.renderSnapIndicator(drawList, camera);
    }

    // ====== 内部辅助方法 ======

    // ====== 预览逻辑修正 ======
    private void updatePreview() {
        if (controlPoints.isEmpty() || currentMousePoint == null) {
            this.previewShape = null;
            markDirty();
            return;
        }
        if (currentMode == RectangleMode.THREE_POINTS) {
            if (controlPoints.size() == 1) {
                // 创建两点连线的预览
                this.previewShape = new Polygon(List.of(controlPoints.getFirst(), currentMousePoint));
            } else {
                // 计算三点矩形的四个顶点
                Vec2d[] vertices = calculateThreePointVertices();
                if (vertices != null) {
                    this.previewShape = new Polygon(Arrays.asList(vertices));
                } else {
                    this.previewShape = null;
                }
            }
        } else if (currentMode == RectangleMode.ROUNDED) {
            if (controlPoints.size() == 1) {
                // 创建两点连线的预览
                this.previewShape = new Polygon(List.of(controlPoints.getFirst(), currentMousePoint));
            } else if (controlPoints.size() == 2) {
                // 计算圆角矩形，第三点决定圆角大小
                Vec2d[] bounds = calculateRoundedRectangleBounds();
                if (bounds != null) {
                    Vec2d corner = bounds[0];
                    double width = bounds[1].x - bounds[0].x;
                    double height = bounds[1].y - bounds[0].y;
                    double radius = bounds[2].x; // 使用第三个点的x坐标作为圆角半径

                    if (this.previewShape instanceof RectangleShape rect) {
                        rect.setCorner(corner);
                        rect.setWidth(width);
                        rect.setHeight(height);
                        rect.setCornerRadius(radius);
                    } else {
                        this.previewShape = new RectangleShape(corner, width, height, radius);
                    }
                } else {
                    this.previewShape = null;
                }
            }
        } else {
            Vec2d[] bounds = calculateRectangleBounds();
            if (bounds != null) {
                Vec2d corner = bounds[0];
                double width = bounds[1].x - bounds[0].x;
                double height = bounds[1].y - bounds[0].y;
                if (this.previewShape instanceof RectangleShape rect) {
                    rect.setCorner(corner);
                    rect.setWidth(width);
                    rect.setHeight(height);
                    rect.setCornerRadius(0.0);
                } else {
                    this.previewShape = new RectangleShape(corner, width, height, 0.0);
                }
            } else {
                this.previewShape = null;
            }
        }

        // 为所有预览图形统一强制应用无填充样式
        if (this.previewShape != null) {
            forceApplyNoFillStyle(this.previewShape, true);

            // 确保预览图形不跟随图层样式
            if (this.previewShape.getStyle() instanceof ShapeStyle shapeStyle) {
                shapeStyle.setFollowsLayerStyle(false);
            }
        }
        markDirty();
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static Color toColor(int color, int alpha) {
        return new Color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, alpha);
    }

    private Vec2d[] calculateRectangleBounds() {
        if (controlPoints.isEmpty() || currentMousePoint == null) return null;

        return switch (currentMode) {
            case TWO_POINTS -> calculateTwoPointBounds();
            case THREE_POINTS -> calculateThreePointBounds();
            case CENTER_POINT -> calculateCenterPointBounds();
            case ROUNDED -> calculateRoundedRectangleBounds();
        };
    }

    private Vec2d[] calculateRoundedRectangleBounds() {
        if (controlPoints.size() < 2 || currentMousePoint == null) return null;

        // 前两点确定矩形大小
        Vec2d p1 = controlPoints.get(0);
        Vec2d p2 = controlPoints.get(1);

        double x1 = Math.min(p1.x, p2.x);
        double y1 = Math.min(p1.y, p2.y);
        double x2 = Math.max(p1.x, p2.x);
        double y2 = Math.max(p1.y, p2.y);

        // 应用正方形约束（支持Shift临时启用）
        if (isSquareConstraintActive()) {
            double size = Math.max(x2 - x1, y2 - y1);
            x2 = x1 + size;
            y2 = y1 + size;
        }

        // 第三点决定圆角大小
        double radius = calculateCornerRadiusFromThirdPoint(p1, p2, currentMousePoint);

        return new Vec2d[]{
                new Vec2d(x1, y1),  // 左下角
                new Vec2d(x2, y2),  // 右上角
                new Vec2d(radius, 0) // 圆角半径
        };
    }

    private double calculateCornerRadiusFromThirdPoint(Vec2d p1, Vec2d p2, Vec2d thirdPoint) {
        // 计算矩形的宽度和高度
        double width = Math.abs(p2.x - p1.x);
        double height = Math.abs(p2.y - p1.y);

        // 计算第三点到矩形中心的距离
        Vec2d center = new Vec2d((p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
        double distance = thirdPoint.distance(center);

        // 根据距离计算圆角半径
        // 距离越大，圆角半径越大
        double maxRadius = Math.min(width, height) / 2; // 最大圆角半径为短边的一半
        double minRadius = 0; // 最小圆角半径

        // 将距离映射到圆角半径范围
        double normalizedDistance = Math.min(distance / (Math.max(width, height) / 2), 1.0);
        double radius = minRadius + normalizedDistance * maxRadius;

        return Math.max(0, radius);
    }

    private Vec2d[] calculateTwoPointBounds() {
        if (controlPoints.isEmpty()) return null;

        Vec2d p1 = controlPoints.getFirst();
        Vec2d p2 = currentMousePoint;

        double x1 = Math.min(p1.x, p2.x);
        double y1 = Math.min(p1.y, p2.y);
        double x2 = Math.max(p1.x, p2.x);
        double y2 = Math.max(p1.y, p2.y);

        if (isSquareConstraintActive()) {
            double size = Math.min(x2 - x1, y2 - y1);
            x2 = x1 + size;
            y2 = y1 + size;
        }

        return new Vec2d[]{new Vec2d(x1, y1), new Vec2d(x2, y2)};
    }

    private Vec2d[] calculateThreePointBounds() {
        if (controlPoints.size() < 2) return null;

        if (controlPoints.size() == 2 && currentMousePoint != null) {
            // 计算三点矩形
            Vec2d p1 = controlPoints.get(0);
            Vec2d p2 = controlPoints.get(1);
            Vec2d p3 = currentMousePoint;

            // 计算基础向量和高度
            Vec2d baseVector = new Vec2d(p2.x - p1.x, p2.y - p1.y);
            double baseLength = Math.sqrt(baseVector.x * baseVector.x + baseVector.y * baseVector.y);

            if (baseLength < 1e-6) return null;

            Vec2d baseUnit = new Vec2d(baseVector.x / baseLength, baseVector.y / baseLength);
            Vec2d perpUnit = new Vec2d(-baseUnit.y, baseUnit.x);

            Vec2d toP3 = new Vec2d(p3.x - p1.x, p3.y - p1.y);
            double height = toP3.x * perpUnit.x + toP3.y * perpUnit.y;

            // 计算矩形的四个顶点
            Vec2d v3 = new Vec2d(p2.x + perpUnit.x * height, p2.y + perpUnit.y * height);
            Vec2d v4 = new Vec2d(p1.x + perpUnit.x * height, p1.y + perpUnit.y * height);

            // 计算边界框
            double minX = Math.min(Math.min(p1.x, p2.x), Math.min(v3.x, v4.x));
            double minY = Math.min(Math.min(p1.y, p2.y), Math.min(v3.y, v4.y));
            double maxX = Math.max(Math.max(p1.x, p2.x), Math.max(v3.x, v4.x));
            double maxY = Math.max(Math.max(p1.y, p2.y), Math.max(v3.y, v4.y));

            return new Vec2d[]{new Vec2d(minX, minY), new Vec2d(maxX, maxY)};
        }

        return null;
    }

    private Vec2d[] calculateCenterPointBounds() {
        if (controlPoints.isEmpty()) return null;

        Vec2d center = controlPoints.getFirst();
        Vec2d corner = currentMousePoint;

        double dx = Math.abs(corner.x - center.x);
        double dy = Math.abs(corner.y - center.y);

        if (forceSquare) {
            double size = Math.max(dx, dy);
            dx = size;
            dy = size;
        }

        return new Vec2d[]{
                new Vec2d(center.x - dx, center.y - dy),
                new Vec2d(center.x + dx, center.y + dy)
        };
    }

    private Vec2d[] getRectangleVertices() {
        if (previewRectangle == null) return new Vec2d[0];

        Vec2d[] bounds = calculateRectangleBounds();
        if (bounds == null || bounds.length < 2) return new Vec2d[0];

        return new Vec2d[]{
                new Vec2d(bounds[0].x, bounds[0].y), // 左下
                new Vec2d(bounds[1].x, bounds[0].y), // 右下
                new Vec2d(bounds[1].x, bounds[1].y), // 右上
                new Vec2d(bounds[0].x, bounds[1].y)  // 左上
        };
    }

    private void renderControlPoints(DrawContext context) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        for (int i = 0; i < controlPoints.size(); i++) {
            Vec2d point = controlPoints.get(i);
            java.awt.Color color = switch (i) {
                case 0 -> toColor(theme.errorText, 255);
                case 1 -> toColor(theme.successText, 255);
                case 2 -> toColor(theme.infoText, 255);
                default -> toColor(theme.mutedText, 255);
            };
            context.drawCircle(point, 4.0, color);
        }
    }

    /**
     * 获取控制点颜色（预留方法，可能在未来版本中使用）
     */
    @SuppressWarnings("unused")
    private Color getControlPointColor(int index) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        return switch (currentMode) {
            case CENTER_POINT -> index == 0 ? toColor(theme.errorText, 255) : toColor(theme.successText, 255);
            default -> toColor(theme.infoText, 255);
        };
    }

    // ====== Getter 方法 ======

    public RectangleMode getCurrentMode() { return currentMode; }

    // ====== 自定义交互策略 (修改后版本) ======
    private class RectangleInteractionStrategy implements IInteractionStrategy {
        private Shape finalShape;

        @Override
        public String getStrategyName() { return "RectangleInteractionStrategy"; }

        @Override
        public String getStrategyDescription() { return PlotI18n.modeLabel("strategy.plot.draw.rectangle"); }

        @Override
        public void reset() {
            RectangleTool.this.resetDrawingState();
            this.finalShape = null;
        }

        @Override
        public Shape getFinalShape() {
            // 返回已创建的最终图形
            return this.finalShape;
        }

        @Override
        public InteractionResult onMouseDown(Vec2d pos, int button, DrawingToolContext context) {
            if (button != MOUSE_LEFT) return InteractionResult.IGNORED;

            // 使用增强的捕捉检测
            SnapEnhancer.SnapResult snapResult = snapEnhancer.performEnhancedSnap(pos, context);
            Vec2d snappedPoint = snapResult.point;
            controlPoints.add(snappedPoint);

            LOGGER.debug("RectangleTool: 添加控制点 - 位置={}, 捕捉={}, 类型={}",
                    snappedPoint, snapResult.snapped, snapResult.snapType);

            if (controlPoints.size() == 1) {
                context.setToolState(ToolState.DRAWING); // 关键：进入绘制状态
            }

            int requiredPoints = switch (currentMode) {
                case TWO_POINTS, CENTER_POINT -> 2;
                case THREE_POINTS, ROUNDED -> 3;
            };

            if (controlPoints.size() >= requiredPoints) {
                // 创建最终图形
                if (currentMode == RectangleMode.ROUNDED) {
                    // 圆角模式：创建带圆角的矩形
                    Vec2d[] bounds = calculateRoundedRectangleBounds();
                    if (bounds != null) {
                        Vec2d corner = bounds[0];
                        double width = bounds[1].x - bounds[0].x;
                        double height = bounds[1].y - bounds[0].y;
                        double radius = bounds[2].x;

                        this.finalShape = new RectangleShape(corner, width, height, radius);
                        LOGGER.debug("RectangleInteractionStrategy: 创建圆角矩形 - 圆角半径: {}", radius);
                    }
                } else if (previewShape != null) {
                    // 其他模式：使用预览图形
                    this.finalShape = previewShape;
                }

                LOGGER.debug("RectangleInteractionStrategy: 完成绘制 - 模式: {}, 控制点数: {}",
                        currentMode.getDisplayName(), controlPoints.size());
                return InteractionResult.COMPLETE;
            } else {
                updateStatusMessage();
                return InteractionResult.CONTINUE;
            }
        }

        @Override
        public InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context) {
            // 总是进行捕捉检测，即使在绘制开始前
            SnapEnhancer.SnapResult snapResult = snapEnhancer.performEnhancedSnap(pos, context);
            currentMousePoint = snapResult.point;

            // 如果还没有控制点，只显示捕捉效果，不更新预览
            if (controlPoints.isEmpty()) {
                LOGGER.trace("RectangleTool.onMouseMove: 预绘制捕捉 - 位置={}, 捕捉={}, 类型={}",
                        currentMousePoint, snapResult.snapped, snapResult.snapType);
                return InteractionResult.CONTINUE; // 返回CONTINUE以触发重绘
            }

            updatePreview();
            context.setPreviewShape(previewShape); // 关键：每次同步
            return InteractionResult.CONTINUE;
        }

        @Override
        public InteractionResult onMouseUp(Vec2d pos, int button, DrawingToolContext context) {
            return InteractionResult.IGNORED;
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
    protected IInteractionStrategy createStrategy(InteractionType type) {
        return new RectangleInteractionStrategy();
    }

    // 辅助方法：投影点
    private Vec2d projectPointToBase(Vec2d baseStart, Vec2d baseEnd, Vec2d point) {
        double dx = baseEnd.x - baseStart.x;
        double dy = baseEnd.y - baseStart.y;
        double len2 = dx*dx + dy*dy;
        if (len2 < 1e-8) return baseStart;
        double t = ((point.x - baseStart.x) * dx + (point.y - baseStart.y) * dy) / len2;
        double px = baseStart.x + t * dx;
        double py = baseStart.y + t * dy;
        return new Vec2d(px, py);
    }

    private Vec2d[] calculateThreePointVertices() {
        if (controlPoints.size() < 2 || currentMousePoint == null) return null;
        Vec2d p1 = controlPoints.get(0);
        Vec2d p2 = controlPoints.get(1);
        Vec2d p3 = currentMousePoint;
        Vec2d baseVector = p2.subtract(p1);
        double len2 = baseVector.dot(baseVector);
        if (len2 < 1e-12) return null;
        Vec2d baseUnit = baseVector.multiply(1.0 / Math.sqrt(len2));
        Vec2d perpUnit = new Vec2d(-baseUnit.y, baseUnit.x);
        Vec2d toMouseVector = p3.subtract(p1);
        double height = toMouseVector.dot(perpUnit);
        Vec2d heightVector = perpUnit.multiply(height);
        Vec2d v3 = p2.add(heightVector);
        Vec2d v4 = p1.add(heightVector);
        return new Vec2d[]{p1, p2, v3, v4};
    }
}