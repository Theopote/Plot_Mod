package com.plot.ui.tools.impl.drawing;

import com.plot.api.geometry.Vec2d;
import com.plot.api.state.IAppState;
import com.plot.api.snap.ISnapManager;
import com.plot.core.geometry.shapes.EllipseShape;
import com.plot.core.graphics.DrawContext;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.model.Shape;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.component.Icons;
import com.plot.ui.theme.ThemeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import imgui.ImDrawList;
import imgui.ImColor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
// import removed: SnapEnhancer referenced via FQN

/**
 * 椭圆工具 - 完整的策略模式实现
 * <p>
 * 支持三种绘制模式：
 * 1. 三点-轴模式：前两点确定长轴，第三点确定短半轴
 * 2. 三点-中心点模式：第一点确定中心点，第二点和第三点分别确定长半轴和短半轴
 * 3. 两点模式：绘制矩形内切椭圆
 */
public class EllipseTool extends DrawingTool implements com.plot.infrastructure.event.EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(EllipseTool.class);

    // ====== 椭圆渲染参数常量 ======
    private static final int MIN_SEGMENTS = 8;              // 最小椭圆段数
    private static final int MAX_SEGMENTS = 72;             // 最大椭圆段数
    private static final int PREVIEW_SEGMENTS = 40;         // 预览时的椭圆段数
    
    // ====== 渲染样式常量 ======
    private static final float PREVIEW_ALPHA = 0.8f;        // 预览透明度
    private static final float PREVIEW_LINE_THICKNESS = 2.0f;// 预览线条粗细
    private static final float AXIS_LINE_THICKNESS = 1.0f;  // 轴线粗细
    private static final float AXIS_POINT_SIZE = 4.0f;      // 轴点大小
    private static final float FOCI_POINT_SIZE = 3.0f;      // 焦点大小
    private static final float CENTER_POINT_SIZE = 5.0f;    // 中心点大小
    private static final float SNAP_INDICATOR_SIZE = 8.0f;  // 吸附指示器大小
    
    // ====== 文本偏移常量 ======
    private static final float TEXT_OFFSET_X = 10.0f;       // 文本X轴偏移
    private static final float TEXT_OFFSET_Y = 10.0f;       // 文本Y轴偏移
    
    // ====== 绘制模式枚举 ======
    public enum EllipseMode {
        THREE_POINTS_AXIS("three_points_axis", "三点-轴模式", "前两点确定长轴，第三点确定短半轴"),
        THREE_POINTS_CENTER("three_points_center", "三点-中心点模式", "第一点确定中心点，第二点和第三点分别确定长半轴和短半轴"),
        TWO_POINTS("two_points", "两点模式", "绘制矩形内切椭圆");
        
        private final String configValue;
        private final String displayName;
        private final String description;
        
        EllipseMode(String configValue, String displayName, String description) {
            this.configValue = configValue;
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        
        public static EllipseMode fromConfigValue(String value) {
            for (EllipseMode mode : values()) {
                if (mode.configValue.equals(value)) {
                    return mode;
                }
            }
            return THREE_POINTS_AXIS; // 默认模式
        }
    }
    
    // ====== 配置键常量 ======
    private static final String CONFIG_TYPE = "mode";  // 与UI面板保持一致
    private static final String CONFIG_SEGMENTS = "segments";
    private static final String CONFIG_SHOW_FOCI = "showFoci";
    private static final String CONFIG_SHOW_AXES = "showAxes";
    
    // ====== 状态提示映射 ======
    private static final Map<EllipseMode, String[]> MODE_STATUS_MESSAGES = Map.of(
        EllipseMode.THREE_POINTS_AXIS, new String[]{
            "三点-轴模式：点击设置长轴第一点",
            "三点-轴模式：点击设置长轴第二点", 
            "三点-轴模式：点击设置短轴长度"
        },
        EllipseMode.THREE_POINTS_CENTER, new String[]{
            "三点-中心点模式：点击设置椭圆中心点",
            "三点-中心点模式：点击设置长半轴终点",
            "三点-中心点模式：点击设置短半轴终点"
        },
        EllipseMode.TWO_POINTS, new String[]{
            "两点模式：点击设置矩形第一个角点",
            "两点模式：点击设置矩形对角点",
            "" // 两点模式只需要两步
        }
    );
    
    // ====== 工具状态 ======
    private EllipseMode currentMode = EllipseMode.THREE_POINTS_AXIS;
    private boolean showFoci = true;
    private boolean showAxes = true;
    
    // ====== 交互状态 ======
    private final List<Vec2d> controlPoints = new ArrayList<>();
    private Vec2d currentMousePoint;
    // 捕捉增强器（统一可视化与类型识别）
    private final com.plot.ui.tools.snap.SnapEnhancer snapEnhancer =
            new com.plot.ui.tools.snap.SnapEnhancer("EllipseTool");
    private EllipseShape previewEllipse;
    private int currentStep = 0;
    
    // ====== 构造函数 ======

    public EllipseTool(IAppState appState, ISnapManager snapManager) {
        super("ellipse", "椭圆", Icons.ELLIPSE_IDENTIFIER, "绘制椭圆", appState, snapManager, InteractionType.CLICK_AND_CLICK);
        
        // 注册类型安全的配置事件监听
        EventBus.getInstance().subscribe(ToolConfigEvent.class, this);
        
        // 设置自定义交互策略
        setupInteractionStrategy();
        
        LOGGER.debug("EllipseTool 初始化完成，当前模式: {}", currentMode.getDisplayName());
    }

    @Deprecated
    public EllipseTool() {
        this(com.plot.core.state.AppState.getInstance(), 
             com.plot.core.snap.SnapManager.getInstance());
    }
    
    // ====== 策略模式集成 ======
    
    /**
     * 设置交互策略为自定义椭圆策略
     */
    private void setupInteractionStrategy() {
        setInteractionStrategy(new EllipseInteractionStrategy());
        LOGGER.debug("成功设置椭圆工具交互策略");
    }
    
    // ====== 基类实现 ======

    @Override
    protected Shape createShape(Vec2d startPoint, Vec2d endPoint) {
        // 对于椭圆工具，这个方法主要用于向后兼容
        // 实际的图形创建在自定义策略中完成
        if (startPoint == null || endPoint == null) return null;
        
        Vec2d center = new Vec2d((startPoint.x + endPoint.x) / 2, (startPoint.y + endPoint.y) / 2);
        double radiusX = Math.abs(endPoint.x - startPoint.x) / 2;
        double radiusY = Math.abs(endPoint.y - startPoint.y) / 2;
        
        EllipseShape ellipse = new EllipseShape(center, radiusX, radiusY, 0.0);
        
        ShapeStyle style = getStyleHandler().getFinalStyle();
        if (style != null) {
            ellipse.setStyle(style);
        }
        
        return ellipse;
    }

    @Override
    protected void renderPreview(DrawContext context) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        Color axisColor = toColor(theme.warningText, (int)(PREVIEW_ALPHA * 255));
        Color centerPointColor = toColor(theme.errorText, 255);
        Color controlPointColor = toColor(theme.infoText, 255);

        // 根据模式渲染不同的预览内容
        switch (currentMode) {
            case THREE_POINTS_AXIS -> {
                if (controlPoints.size() == 1 && currentMousePoint != null) {
                    // 第一个点和鼠标之间显示线段
                    context.drawLine(controlPoints.getFirst(), currentMousePoint, axisColor);
                }
                if (!controlPoints.isEmpty()) {
                    context.drawCircleFilled(controlPoints.getFirst(), 5.0f, centerPointColor);
                }
                if (controlPoints.size() >= 2) {
                    context.drawCircleFilled(controlPoints.get(1), 5.0f, controlPointColor);
                    context.drawLine(controlPoints.get(0), controlPoints.get(1), axisColor);
                } else if (currentMousePoint != null && !controlPoints.isEmpty()) {
                    context.drawLine(controlPoints.getFirst(), currentMousePoint, axisColor);
                }
                if (controlPoints.size() >= 2 && currentMousePoint != null) {
                    Vec2d center = controlPoints.get(0).add(controlPoints.get(1)).multiply(0.5);
                    context.drawLine(center, currentMousePoint, controlPointColor);
                }
            }
            case THREE_POINTS_CENTER -> {
                if (controlPoints.size() == 1 && currentMousePoint != null) {
                    // 显示圆心和预览圆
                    context.drawCircleFilled(controlPoints.getFirst(), CENTER_POINT_SIZE, centerPointColor);
                    double r = controlPoints.getFirst().distance(currentMousePoint);
                    context.drawCircle(controlPoints.getFirst(), r, getPreviewColor());
                }
                if (!controlPoints.isEmpty()) {
                    context.drawCircleFilled(controlPoints.getFirst(), 5.0f, centerPointColor);
                }
                if (controlPoints.size() >= 2) {
                    context.drawCircleFilled(controlPoints.get(1), 5.0f, controlPointColor);
                    context.drawLine(controlPoints.get(0), controlPoints.get(1), axisColor);
                } else if (currentMousePoint != null && !controlPoints.isEmpty()) {
                    context.drawLine(controlPoints.getFirst(), currentMousePoint, axisColor);
                }
                if (controlPoints.size() >= 2 && currentMousePoint != null) {
                    context.drawLine(controlPoints.getFirst(), currentMousePoint, controlPointColor);
                }
            }
            case TWO_POINTS -> {
                if (controlPoints.size() == 1 && currentMousePoint != null) {
                    // 显示矩形框
                    context.drawRect(controlPoints.getFirst(), currentMousePoint, axisColor);
                }
                if (!controlPoints.isEmpty()) {
                    context.drawCircleFilled(controlPoints.getFirst(), 5.0f, controlPointColor);
                }
                if (controlPoints.size() >= 2) {
                    context.drawCircleFilled(controlPoints.get(1), 5.0f, controlPointColor);
                    context.drawLine(controlPoints.get(0), controlPoints.get(1), axisColor);
                } else if (currentMousePoint != null && !controlPoints.isEmpty()) {
                    context.drawLine(controlPoints.getFirst(), currentMousePoint, axisColor);
                }
            }
        }
        
        // 始终尝试绘制预览椭圆
        if (previewEllipse != null) {
            // 使用当前图层颜色绘制椭圆预览
            Color previewColor = getPreviewColor();
            Vec2d center = previewEllipse.getCenter();
            double radiusX = previewEllipse.getRadiusX();
            double radiusY = previewEllipse.getRadiusY();
            double rotation = previewEllipse.getRotation();
            
            // 手动绘制椭圆以确保使用正确的颜色
            drawEllipsePreview(context, center, radiusX, radiusY, rotation, previewColor);
        }

        // 渲染捕捉指示器（DrawContext版本）
        snapEnhancer.renderSnapIndicator(context);
    }

    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (camera == null) return;

        var theme = ThemeManager.getInstance().getCurrentTheme();
        int axisColorStrong = withAlpha(theme.warningText, 180);
        int axisColorSoft = withAlpha(theme.warningText, 120);
        int controlColorSoft = withAlpha(theme.infoText, 120);
        int centerPointColor = withAlpha(theme.errorText, 0xFF);
        int previewColor = toImColor(getPreviewColor());
        
        // 根据模式渲染不同的预览内容
        switch (currentMode) {
            case THREE_POINTS_AXIS -> {
                if (controlPoints.size() == 1 && currentMousePoint != null) {
                    // 第一个点和鼠标之间显示线段
                    Vec2d screenP0 = camera.worldToScreen(controlPoints.getFirst());
                    Vec2d screenMouse = camera.worldToScreen(currentMousePoint);
                    drawList.addLine((float)screenP0.x, (float)screenP0.y, (float)screenMouse.x, (float)screenMouse.y, axisColorStrong, 1.5f);
                }
                if (controlPoints.size() >= 2) {
                    Vec2d screenP1 = camera.worldToScreen(controlPoints.get(1));
                    Vec2d screenP0 = camera.worldToScreen(controlPoints.getFirst());
                    drawList.addLine((float)screenP0.x, (float)screenP0.y, (float)screenP1.x, (float)screenP1.y, axisColorStrong, 1.5f);
                } else if (currentMousePoint != null && !controlPoints.isEmpty()) {
                    Vec2d screenP0 = camera.worldToScreen(controlPoints.getFirst());
                    Vec2d screenMouse = camera.worldToScreen(currentMousePoint);
                    drawList.addLine((float)screenP0.x, (float)screenP0.y, (float)screenMouse.x, (float)screenMouse.y, axisColorSoft, 1.2f);
                }
                if (controlPoints.size() >= 2 && currentMousePoint != null) {
                    Vec2d center = controlPoints.get(0).add(controlPoints.get(1)).multiply(0.5);
                    Vec2d screenCenter = camera.worldToScreen(center);
                    Vec2d screenMouse = camera.worldToScreen(currentMousePoint);
                    drawList.addLine((float)screenCenter.x, (float)screenCenter.y, (float)screenMouse.x, (float)screenMouse.y, controlColorSoft, 1.2f);
                }
            }
            case THREE_POINTS_CENTER -> {
                if (controlPoints.size() == 1 && currentMousePoint != null) {
                    // 显示圆心和预览圆
                    Vec2d screenCenter = camera.worldToScreen(controlPoints.getFirst());
                    drawList.addCircleFilled((float)screenCenter.x, (float)screenCenter.y, CENTER_POINT_SIZE, centerPointColor);
                    double r = controlPoints.getFirst().distance(currentMousePoint);
                    drawList.addCircle((float)screenCenter.x, (float)screenCenter.y, (float)r * camera.getZoom(), previewColor, 0, 2.0f);
                }
                if (controlPoints.size() >= 2) {
                    Vec2d screenP1 = camera.worldToScreen(controlPoints.get(1));
                    Vec2d screenP0 = camera.worldToScreen(controlPoints.getFirst());
                    drawList.addLine((float)screenP0.x, (float)screenP0.y, (float)screenP1.x, (float)screenP1.y, axisColorStrong, 1.5f);
                } else if (currentMousePoint != null && !controlPoints.isEmpty()) {
                    Vec2d screenP0 = camera.worldToScreen(controlPoints.getFirst());
                    Vec2d screenMouse = camera.worldToScreen(currentMousePoint);
                    drawList.addLine((float)screenP0.x, (float)screenP0.y, (float)screenMouse.x, (float)screenMouse.y, axisColorSoft, 1.2f);
                }
                if (controlPoints.size() >= 2 && currentMousePoint != null) {
                    Vec2d screenP0 = camera.worldToScreen(controlPoints.getFirst());
                    Vec2d screenMouse = camera.worldToScreen(currentMousePoint);
                    drawList.addLine((float)screenP0.x, (float)screenP0.y, (float)screenMouse.x, (float)screenMouse.y, controlColorSoft, 1.2f);
                }
            }
            case TWO_POINTS -> {
                if (controlPoints.size() == 1 && currentMousePoint != null) {
                    // 显示矩形框
                    Vec2d p1 = camera.worldToScreen(controlPoints.getFirst());
                    Vec2d p2 = camera.worldToScreen(currentMousePoint);
                    drawList.addRect((float)Math.min(p1.x, p2.x), (float)Math.min(p1.y, p2.y), (float)Math.max(p1.x, p2.x), (float)Math.max(p1.y, p2.y), axisColorStrong, 0, 0, 2.0f);
                }
                if (controlPoints.size() >= 2) {
                    Vec2d screenP1 = camera.worldToScreen(controlPoints.get(1));
                    Vec2d screenP0 = camera.worldToScreen(controlPoints.getFirst());
                    drawList.addLine((float)screenP0.x, (float)screenP0.y, (float)screenP1.x, (float)screenP1.y, axisColorStrong, 1.5f);
                } else if (currentMousePoint != null && !controlPoints.isEmpty()) {
                    Vec2d screenP0 = camera.worldToScreen(controlPoints.getFirst());
                    Vec2d screenMouse = camera.worldToScreen(currentMousePoint);
                    drawList.addLine((float)screenP0.x, (float)screenP0.y, (float)screenMouse.x, (float)screenMouse.y, axisColorSoft, 1.2f);
                }
            }
        }
        
        // 椭圆预览
        if (previewEllipse != null) {
            renderEllipsePreview(drawList, camera);
        }
        
        // 渲染控制点和吸附指示器
        renderControlPoints(drawList, camera);
        renderMouseSnapIndicator(drawList, camera);
        // 统一渲染捕捉指示器（ImGui版本）
        snapEnhancer.renderSnapIndicator(drawList, camera);
    }
    
    // ====== 预览渲染方法 ======
    
    /**
     * 渲染椭圆预览
     */
    private void renderEllipsePreview(ImDrawList drawList, CanvasCamera camera) {
        if (previewEllipse == null) return;
        
        // 使用当前图层的预览颜色
        Color previewColor = getPreviewColor();
        int imguiColor = ImColor.rgba(previewColor.getRed(), previewColor.getGreen(), 
                                    previewColor.getBlue(), previewColor.getAlpha());
        
        // 获取椭圆参数
        Vec2d center = previewEllipse.getCenter();
        double majorRadius = previewEllipse.getRadiusX();
        double minorRadius = previewEllipse.getRadiusY();
        double ellipseRotation = previewEllipse.getRotation();
        
        // 转换中心点到屏幕坐标
        Vec2d screenCenter = camera.worldToScreen(center);
        
        // 绘制椭圆
        int segmentsCount = PREVIEW_SEGMENTS;
        Vec2d prevScreenPoint = null;

        for (int i = 0; i <= segmentsCount; i++) {
            double angle = (2 * Math.PI * i) / segmentsCount;
            
            // 计算椭圆上的点
            double x = majorRadius * Math.cos(angle);
            double y = minorRadius * Math.sin(angle);
            
            // 应用旋转
            double rotatedX = x * Math.cos(ellipseRotation) - y * Math.sin(ellipseRotation);
            double rotatedY = x * Math.sin(ellipseRotation) + y * Math.cos(ellipseRotation);
            
            // 转换到世界坐标
            Vec2d worldPoint = new Vec2d(
                center.x + rotatedX,
                center.y + rotatedY
            );
            
            // 转换到屏幕坐标
            Vec2d screenPoint = camera.worldToScreen(worldPoint);

            // 绘制线段
            if (prevScreenPoint != null) {
                drawList.addLine(
                    (float)prevScreenPoint.x, (float)prevScreenPoint.y,
                    (float)screenPoint.x, (float)screenPoint.y,
                    imguiColor,
                    PREVIEW_LINE_THICKNESS
                );
            }
            prevScreenPoint = screenPoint;
        }

        // 显示主轴和副轴
        if (showAxes) {
            renderAxes(drawList, camera, center, majorRadius, minorRadius, ellipseRotation);
        }

        // 显示焦点
        if (showFoci && majorRadius > minorRadius) {
            renderFoci(drawList, camera, center, majorRadius, minorRadius, ellipseRotation);
        }

        // 绘制中心点
        drawList.addCircleFilled(
            (float)screenCenter.x,
            (float)screenCenter.y,
            CENTER_POINT_SIZE,
            imguiColor
        );
        
        // 显示信息
        if (currentMousePoint != null) {
            Vec2d screenPos = camera.worldToScreen(currentMousePoint);
            String info = String.format("长轴: %.2f, 短轴: %.2f", 
                majorRadius * 2, minorRadius * 2);
            
            if (ellipseRotation != 0.0) {
                info += String.format(", 旋转: %.1f°", Math.toDegrees(ellipseRotation));
            }
            
            drawList.addText(
                (float)screenPos.x + TEXT_OFFSET_X,
                (float)screenPos.y + TEXT_OFFSET_Y,
                imguiColor,
                info
            );
        }
    }
    
    /**
     * 渲染主轴和副轴
     */
    private void renderAxes(ImDrawList drawList, CanvasCamera camera, Vec2d center, 
                          double majorRadius, double minorRadius, double rotation) {
        int axisColor = withAlpha(ThemeManager.getInstance().getCurrentTheme().warningText, (int)(PREVIEW_ALPHA * 255));
        
        // 计算主轴和副轴上的点
        Vec2d majorStart = getPointAtAngle(center, majorRadius, minorRadius, 0, rotation);
        Vec2d majorEnd = getPointAtAngle(center, majorRadius, minorRadius, Math.PI, rotation);
        Vec2d minorStart = getPointAtAngle(center, majorRadius, minorRadius, Math.PI / 2, rotation);
        Vec2d minorEnd = getPointAtAngle(center, majorRadius, minorRadius, 3 * Math.PI / 2, rotation);
        
        // 转换到屏幕坐标并绘制
        Vec2d[] screenPoints = {
            camera.worldToScreen(majorStart),
            camera.worldToScreen(majorEnd),
            camera.worldToScreen(minorStart),
            camera.worldToScreen(minorEnd)
        };
        
        // 绘制主轴和副轴
        drawList.addLine(
            (float)screenPoints[0].x, (float)screenPoints[0].y,
            (float)screenPoints[1].x, (float)screenPoints[1].y,
            axisColor, AXIS_LINE_THICKNESS
        );
        drawList.addLine(
            (float)screenPoints[2].x, (float)screenPoints[2].y,
            (float)screenPoints[3].x, (float)screenPoints[3].y,
            axisColor, AXIS_LINE_THICKNESS
        );
        
        // 绘制端点
        for (Vec2d point : screenPoints) {
            drawList.addCircleFilled(
                (float)point.x, (float)point.y,
                AXIS_POINT_SIZE, axisColor
            );
        }
    }

    /**
     * 渲染焦点
     */
    private void renderFoci(ImDrawList drawList, CanvasCamera camera, Vec2d center, 
                          double majorRadius, double minorRadius, double rotation) {
        // 计算焦距
        double c = Math.sqrt(majorRadius * majorRadius - minorRadius * minorRadius);
        
        // 计算焦点位置
        double focusX = c * Math.cos(rotation);
        double focusY = c * Math.sin(rotation);
        
        // 创建焦点
        Vec2d focus1 = new Vec2d(center.x + focusX, center.y + focusY);
        Vec2d focus2 = new Vec2d(center.x - focusX, center.y - focusY);
        
        // 转换到屏幕坐标
        Vec2d screenFocus1 = camera.worldToScreen(focus1);
        Vec2d screenFocus2 = camera.worldToScreen(focus2);
        Vec2d screenCenter = camera.worldToScreen(center);
        
        // 绘制焦点
        int fociColor = withAlpha(ThemeManager.getInstance().getCurrentTheme().accent, (int)(PREVIEW_ALPHA * 255));
        
        drawList.addCircleFilled(
            (float)screenFocus1.x, (float)screenFocus1.y,
            FOCI_POINT_SIZE, fociColor
        );
        drawList.addCircleFilled(
            (float)screenFocus2.x, (float)screenFocus2.y,
            FOCI_POINT_SIZE, fociColor
        );
        
        // 绘制连接线
        int fociLineColorInt = withAlpha(ThemeManager.getInstance().getCurrentTheme().accent, 128);
        
        drawList.addLine(
            (float)screenFocus1.x, (float)screenFocus1.y,
            (float)screenCenter.x, (float)screenCenter.y,
            fociLineColorInt, 1.0f
        );
        drawList.addLine(
            (float)screenFocus2.x, (float)screenFocus2.y,
            (float)screenCenter.x, (float)screenCenter.y,
            fociLineColorInt, 1.0f
        );
    }

    /**
     * 渲染控制点
     */
    private void renderControlPoints(ImDrawList drawList, CanvasCamera camera) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        int centerPointColor = withAlpha(theme.errorText, 0xFF);
        int controlPointColor = withAlpha(theme.infoText, 0xFF);
        int snapColor = withAlpha(theme.warningText, 200);

        for (int i = 0; i < controlPoints.size(); i++) {
            Vec2d point = controlPoints.get(i);
            Vec2d screenPoint = camera.worldToScreen(point);
            
            // 控制点颜色：第一个点红色（中心点），其他绿色
            int color = (i == 0 && currentMode == EllipseMode.THREE_POINTS_CENTER)
                    ? centerPointColor
                    : controlPointColor;
            
            // 绘制控制点
            drawList.addCircleFilled(
                (float)screenPoint.x, (float)screenPoint.y, 
                6.0f, color
            );
            
            // 绘制吸附指示器（亮黄色环）
            drawList.addCircle(
                (float)screenPoint.x, (float)screenPoint.y, 
                SNAP_INDICATOR_SIZE, snapColor, 0, 2.0f
            );
        }
    }
    
    /**
     * 渲染当前鼠标位置的吸附指示器
     */
    private void renderMouseSnapIndicator(ImDrawList drawList, CanvasCamera camera) {
        if (currentMousePoint != null && !controlPoints.isEmpty()) {
            Vec2d screenPoint = camera.worldToScreen(currentMousePoint);
            int color = withAlpha(ThemeManager.getInstance().getCurrentTheme().warningText, 140);
            
            drawList.addCircle(
                (float)screenPoint.x, (float)screenPoint.y, 
                SNAP_INDICATOR_SIZE * 0.7f, color, 0, 1.5f
            );
        }
    }

    private static Color toColor(int color, int alpha) {
        return new Color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, alpha);
    }

    private static int toImColor(Color color) {
        return ImColor.rgba(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
    
    /**
     * 计算椭圆上指定角度的点
     */
    private Vec2d getPointAtAngle(Vec2d center, double radiusX, double radiusY, double angle, double rotation) {
        // 计算椭圆上的点（未旋转）
        double x = radiusX * Math.cos(angle);
        double y = radiusY * Math.sin(angle);
        
        // 应用旋转
        double rotatedX = x * Math.cos(rotation) - y * Math.sin(rotation);
        double rotatedY = x * Math.sin(rotation) + y * Math.cos(rotation);
        
        // 返回世界坐标
        return new Vec2d(center.x + rotatedX, center.y + rotatedY);
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
            LOGGER.warn("EllipseTool: 获取预览颜色失败: {}", e.getMessage());
        }

        return toColor(ThemeManager.getInstance().getCurrentTheme().accent, 200);
    }
    
    /**
     * 使用DrawContext绘制椭圆预览
     */
    private void drawEllipsePreview(DrawContext context, Vec2d center, double radiusX, double radiusY, double rotation, Color color) {
        int segments = PREVIEW_SEGMENTS;
        Vec2d prevPoint = null;
        
        for (int i = 0; i <= segments; i++) {
            double angle = (2 * Math.PI * i) / segments;
            
            // 计算椭圆上的点
            double x = radiusX * Math.cos(angle);
            double y = radiusY * Math.sin(angle);
            
            // 应用旋转
            double rotatedX = x * Math.cos(rotation) - y * Math.sin(rotation);
            double rotatedY = x * Math.sin(rotation) + y * Math.cos(rotation);
            
            // 转换到世界坐标
            Vec2d point = new Vec2d(center.x + rotatedX, center.y + rotatedY);
            
            // 绘制线段
            if (prevPoint != null) {
                context.drawLine(prevPoint, point, color);
            }
            prevPoint = point;
        }
        
        // 绘制中心点
        context.drawCircleFilled(center, CENTER_POINT_SIZE, color);
    }
    
    @Override
    public void updateConfig(String key, String value) {
        LOGGER.debug("EllipseTool 配置更新: {}={}", key, value);
        
        switch (key) {
            case CONFIG_TYPE:
                EllipseMode newMode = EllipseMode.fromConfigValue(value);
                if (newMode != currentMode) {
                    currentMode = newMode;
                    resetDrawingState();
                    updateStatusMessageForCurrentMode();
                    LOGGER.debug("椭圆模式切换到: {}", currentMode.getDisplayName());
                }
                break;
                
            case CONFIG_SEGMENTS:
                try {
                    int newSegments = Integer.parseInt(value);
                    int segments = Math.max(MIN_SEGMENTS, Math.min(MAX_SEGMENTS, newSegments));
                    LOGGER.debug("椭圆段数更新为: {}", segments);
                } catch (NumberFormatException e) {
                    LOGGER.warn("无效的段数值: {}", value);
                }
                break;
                
            case CONFIG_SHOW_FOCI:
                showFoci = Boolean.parseBoolean(value);
                LOGGER.debug("焦点显示: {}", showFoci);
                break;
                
            case CONFIG_SHOW_AXES:
                showAxes = Boolean.parseBoolean(value);
                LOGGER.debug("轴线显示: {}", showAxes);
                break;
                
            default:
                LOGGER.debug("EllipseTool: 未知配置键: {}", key);
                break;
        }
    }
    
    // ====== 配置事件处理 ======
    
    /**
     * 事件监听回调
     */
    @Override
    public void onEvent(com.plot.infrastructure.event.base.Event event) {
        if (event instanceof ToolConfigEvent toolConfigEvent) {
            if ("ellipse".equals(toolConfigEvent.getToolId())) {
                LOGGER.debug("接收到椭圆工具配置事件: {}={}", toolConfigEvent.getOptionName(), toolConfigEvent.getValue());
                updateConfig(toolConfigEvent.getOptionName(), String.valueOf(toolConfigEvent.getValue()));
            }
        }
    }
    
    // ====== 内部状态管理 ======
    
    /**
     * 重置绘制状态
     */
    private void resetDrawingState() {
        controlPoints.clear();
        currentMousePoint = null;
        previewEllipse = null;
        currentStep = 0;
        updateStatusMessageForCurrentMode();
        
        LOGGER.debug("椭圆工具状态已重置");
    }
    
    /**
     * 根据当前模式更新状态消息
     */
    private void updateStatusMessageForCurrentMode() {
        String[] messages = MODE_STATUS_MESSAGES.get(currentMode);
        if (messages != null && currentStep < messages.length) {
            setStatusMessage(messages[currentStep]);
        }
    }

    /**
     * 检查是否应该显示预览
     */
    @Override
    protected boolean shouldShowPreview() {
        return previewEllipse != null || !controlPoints.isEmpty();
    }
    
    /**
     * 更新预览椭圆
     */
    private void updatePreview() {
        // 根据当前模式和控制点状态更新预览
        switch (currentMode) {
            case THREE_POINTS_AXIS:
                updateThreePointsAxisPreview();
                break;
            case THREE_POINTS_CENTER:
                updateThreePointsCenterPreview();
                break;
            case TWO_POINTS:
                updateTwoPointsEllipsePreview();
                break;
        }
        
        // 设置预览图形
        if (previewEllipse != null) {
            previewShape = previewEllipse;
        }
    }
    
    // ====== 自定义交互策略 ======
    
    /**
     * 椭圆工具专用交互策略
     * 支持三种绘制模式的多点点击交互
     */
    private class EllipseInteractionStrategy implements com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy {
        
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
            
            // 使用增强捕捉，带类型识别与可视化状态
            var snapResult = snapEnhancer.performEnhancedSnap(pos, context);
            Vec2d worldPoint = snapResult.point;
            LOGGER.debug("EllipseTool.onMouseDown: 点击位置={}, 捕捉后={}, 类型={}", pos, worldPoint, snapResult.snapType);
            
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
                case THREE_POINTS_AXIS:
                case THREE_POINTS_CENTER:
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
            // 更新当前鼠标位置（含捕捉状态）
            var snapResult = snapEnhancer.performEnhancedSnap(pos, context);
            currentMousePoint = snapResult.point;
            
            // 即使没有控制点，也要更新鼠标位置以显示吸附效果
            if (controlPoints.isEmpty()) {
                return InteractionResult.CONTINUE;
            }
            
            // 根据当前模式更新预览
            return switch (currentMode) {
                case THREE_POINTS_AXIS -> {
                    updateThreePointsAxisPreview();
                    yield InteractionResult.CONTINUE;
                }
                case THREE_POINTS_CENTER -> {
                    updateThreePointsCenterPreview();
                    yield InteractionResult.CONTINUE;
                }
                case TWO_POINTS -> {
                    updateTwoPointsEllipsePreview();
                    yield InteractionResult.CONTINUE;
                }
            };
            
            // 强制更新预览

        }
        
        @Override
        public InteractionResult onMouseUp(Vec2d pos, int button, DrawingToolContext context) {
            // 椭圆工具使用点击模式，不需要处理鼠标释放
            return InteractionResult.IGNORED;
        }
        
        @Override
        public com.plot.core.model.Shape getFinalShape() {
            if (previewEllipse != null) {
                EllipseShape ellipse = new EllipseShape(
                    previewEllipse.getCenter(),
                    previewEllipse.getRadiusX(),
                    previewEllipse.getRadiusY(),
                    previewEllipse.getRotation()
                );
                
                ShapeStyle style = getStyleHandler().getFinalStyle();
                if (style != null) {
                    ellipse.setStyle(style);
                }
                
                LOGGER.debug("EllipseTool 创建椭圆: 中心={}, 长半轴={:.2f}, 短半轴={:.2f}, 旋转={:.2f}°", 
                            ellipse.getCenter(), ellipse.getRadiusX(), ellipse.getRadiusY(), 
                            Math.toDegrees(ellipse.getRotation()));
                return ellipse;
            }
            
            return null;
        }
        
        @Override
        public void reset() {
            resetDrawingState();
        }
        
        @Override
        public String getStrategyName() {
            return "EllipseInteractionStrategy";
        }
        
        @Override
        public String getStrategyDescription() {
            return "椭圆工具多点点击交互策略，支持三种绘制模式";
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
    
    // ====== 预览计算方法 ======
    
    /**
     * 更新三点-轴模式的预览
     * 前两点确定长轴，第三点确定短半轴
     */
    private void updateThreePointsAxisPreview() {
        if (controlPoints.isEmpty() || currentMousePoint == null) {
            previewEllipse = null;
            return;
        }
        
        // 优化：第一个点+鼠标位置时也生成退化椭圆预览
        if (controlPoints.size() == 1) {
            Vec2d p1 = controlPoints.getFirst();
            Vec2d p2 = currentMousePoint;
            Vec2d center = p1.add(p2).multiply(0.5);
            double majorRadius = p1.distance(p2) / 2.0;
            double minorRadius = 0.0; // 退化为线
            double rotation = Math.atan2(p2.y - p1.y, p2.x - p1.x);
            previewEllipse = new EllipseShape(center, majorRadius, minorRadius, rotation);
            previewEllipse.setStyle(getStyleHandler().getPreviewStyle());
            return;
        }
        
        // 当 controlPoints.size() >= 2 时，才开始预览椭圆
        Vec2d p1 = controlPoints.get(0);
        Vec2d p2 = controlPoints.get(1);
        Vec2d center = p1.add(p2).multiply(0.5);
        double majorRadius = p1.distance(p2) / 2.0;
        Vec2d toThirdPoint = currentMousePoint.subtract(center);
        Vec2d axisDirection = p2.subtract(p1).normalize();
        Vec2d perpDirection = new Vec2d(-axisDirection.y, axisDirection.x);
        double minorRadius = Math.abs(toThirdPoint.dot(perpDirection));
        double rotation = Math.atan2(axisDirection.y, axisDirection.x);
        previewEllipse = new EllipseShape(center, majorRadius, minorRadius, rotation);
        previewEllipse.setStyle(getStyleHandler().getPreviewStyle());
    }

    /**
     * 更新三点-中心点模式的预览
     * 第一点确定中心点，第二点和第三点分别确定长半轴和短半轴
     */
    private void updateThreePointsCenterPreview() {
        if (controlPoints.isEmpty() || currentMousePoint == null) {
            previewEllipse = null;
            return;
        }
        
        Vec2d center = controlPoints.getFirst();
        Vec2d secondPoint;
        double minorRadius;
        
        if (controlPoints.size() == 1) {
            // 只有中心点：预览一个以鼠标位置为边界的圆形
            secondPoint = currentMousePoint;
            minorRadius = center.distance(secondPoint); // 短半轴 = 长半轴
        } else { // controlPoints.size() >= 2
            // 有中心点和主轴点：根据鼠标位置确定短轴
            secondPoint = controlPoints.get(1);
            Vec2d toThirdPoint = currentMousePoint.subtract(center);
            Vec2d axisDirection = secondPoint.subtract(center).normalize();
            Vec2d perpDirection = new Vec2d(-axisDirection.y, axisDirection.x);
            minorRadius = Math.abs(toThirdPoint.dot(perpDirection));
        }
        
        double majorRadius = center.distance(secondPoint);
        double rotation = Math.atan2(secondPoint.y - center.y, secondPoint.x - center.x);
        previewEllipse = new EllipseShape(center, majorRadius, minorRadius, rotation);
        previewEllipse.setStyle(getStyleHandler().getPreviewStyle());
    }

    /**
     * 更新两点模式的预览
     * 根据两点确定矩形，然后计算内切椭圆
     */
    private void updateTwoPointsEllipsePreview() {
        if (controlPoints.isEmpty() || currentMousePoint == null) {
            previewEllipse = null;
            return;
        }
        
        Vec2d p1 = controlPoints.getFirst();
        Vec2d p2 = (controlPoints.size() >= 2) ? controlPoints.get(1) : currentMousePoint;
        
        // 计算中心点和半径
        Vec2d center = p1.add(p2).multiply(0.5);
        double radiusX = Math.abs(p2.x - p1.x) / 2.0;
        double radiusY = Math.abs(p2.y - p1.y) / 2.0;
        
        // 内切于轴对齐矩形的椭圆，旋转角度总是0
        previewEllipse = new EllipseShape(center, radiusX, radiusY, 0.0);
        previewEllipse.setStyle(getStyleHandler().getPreviewStyle());
    }

    @Override
    protected com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy createStrategy(InteractionType type) {
        return new EllipseInteractionStrategy();
    }
} 