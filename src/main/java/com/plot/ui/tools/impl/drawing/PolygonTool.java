package com.plot.ui.tools.impl.drawing;

import com.plot.api.geometry.Vec2d;
import com.plot.api.state.IAppState;
import com.plot.api.snap.ISnapManager;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.graphics.DrawContext;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.model.Shape;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.component.Icons;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import imgui.ImDrawList;
import imgui.ImColor;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.plot.utils.PlotI18n;
import com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy;
import com.plot.ui.tools.impl.drawing.strategy.MultiStepInteractionStrategy;

/**
 * 多边形工具 - 完整的策略模式实现
 * <p>
 * 支持三种绘制模式：
 * 1. 中心-半径模式：第一点为中心，第二点确定半径和第一个顶点位置
 * 2. 中心-顶点模式：第一点为中心，拖动确定外接圆半径
 * 3. 边-边模式：通过两个相邻顶点确定多边形的位置和大小
 */
public class PolygonTool extends DrawingTool {
    // 统一捕捉可视化
    private final com.plot.ui.tools.snap.SnapEnhancer snapEnhancer =
            new com.plot.ui.tools.snap.SnapEnhancer("PolygonTool");
    private static final Logger LOGGER = LoggerFactory.getLogger(PolygonTool.class);

    // ====== 多边形渲染参数常量 ======
    private static final int DEFAULT_SIDES = 6;              // 默认边数
    private static final int MIN_SIDES = 3;                  // 最小边数
    private static final int MAX_SIDES = 20;                 // 最大边数
    private static final double MIN_RADIUS = 1.0;            // 最小半径
    
    // ====== 渲染样式常量 ======
    private static final float PREVIEW_ALPHA = 0.8f;         // 预览透明度
    private static final float PREVIEW_LINE_THICKNESS = 2.5f; // 预览线条粗细
    private static final float VERTEX_POINT_SIZE = 5.0f;     // 顶点大小
    private static final float CENTER_POINT_SIZE = 6.0f;     // 中心点大小
    private static final float SNAP_INDICATOR_SIZE = com.plot.ui.tools.snap.SnapVisualStyle.DEFAULT_RING_SIZE;   // 吸附指示器大小
    
    // ====== 颜色常量 ======
    private static final Color PREVIEW_COLOR = new Color(255, 255, 255, (int)(PREVIEW_ALPHA * 255));
    private static final Color VERTEX_COLOR = new Color(102, 255, 102, (int)(PREVIEW_ALPHA * 255));
    private static final Color CENTER_COLOR = new Color(255, 102, 102, (int)(PREVIEW_ALPHA * 255));
    private static final Color GUIDE_LINE_COLOR = new Color(128, 128, 255, (int)(PREVIEW_ALPHA * 0.6 * 255));
    private static final Color SNAP_INDICATOR_COLOR = com.plot.ui.tools.snap.SnapVisualStyle.DEFAULT_HIGHLIGHT;
    
    // ====== 文本偏移常量 ======
    private static final float TEXT_OFFSET_X = 10.0f;        // 文本X轴偏移
    private static final float TEXT_OFFSET_Y = 10.0f;        // 文本Y轴偏移
    
    // ====== 几何数据存储类 ======
    /**
     * 存储预览渲染所需的所有几何数据
     * 统一管理几何计算，避免渲染方法中的重复计算
     */
    private static class PreviewGeometry {
        Polygon polygon;
        Vec2d center;
        List<Vec2d> vertices;
        List<Line> guideLines;
        List<Vec2d> controlPoints; // used by renderControlPoints
        Vec2d currentMousePoint;    // used by UI text and snap ring
        double radius;
        String infoText;
        
        void clear() {
            polygon = null;
            center = null;
            vertices = null;
            guideLines = null;
            controlPoints = null;
            currentMousePoint = null;
            radius = 0;
            infoText = null;
        }
    }

    /**
     * 简单的线段数据类
     */
    private static class Line {
        final Vec2d start;
        final Vec2d end;

        Line(Vec2d start, Vec2d end) {
            this.start = start;
            this.end = end;
        }
    }
    
    // ====== 绘制模式枚举 ======
    public enum PolygonMode {
        CENTER_RADIUS("center_radius", "mode.plot.polygon.center_radius_short", "mode.plot.polygon.center_radius.desc"),
        CENTER_VERTEX("center_vertex", "mode.plot.polygon.center_vertex_short", "mode.plot.polygon.center_vertex.desc");

        private final String configValue;
        private final String nameKey;
        private final String descKey;

        PolygonMode(String configValue, String nameKey, String descKey) {
            this.configValue = configValue;
            this.nameKey = nameKey;
            this.descKey = descKey;
        }

        public String getConfigValue() { return configValue; }
        public String getDisplayName() { return PlotI18n.modeLabel(nameKey); }
        public String getDescription() { return PlotI18n.modeLabel(descKey); }
        
        public static PolygonMode fromConfigValue(String value) {
            for (PolygonMode mode : values()) {
                if (mode.configValue.equals(value)) {
                    return mode;
                }
            }
            return CENTER_RADIUS; // 默认模式
        }
    }
    
    // ====== 配置键常量 ======
    private static final String CONFIG_SIDES = "sides";        // 与UI面板保持一致
    private static final String CONFIG_MODE = "mode";          // 绘制模式
    
    // ====== 状态提示映射 ======
    private static final Map<PolygonMode, String[]> MODE_STATUS_MESSAGES = Map.of(
        PolygonMode.CENTER_RADIUS, new String[]{
            "status.plot.draw.polygon.center_radius_p1",
            "status.plot.draw.polygon.center_radius_p2"
        },
        PolygonMode.CENTER_VERTEX, new String[]{
            "status.plot.draw.polygon.center_vertex_p1",
            "status.plot.draw.polygon.center_vertex_p2"
        }
    );
    
    // ====== 工具状态 ======
    private PolygonMode currentMode = PolygonMode.CENTER_VERTEX;  // 默认为中心-顶点模式
    private int sides = DEFAULT_SIDES;
    
    // ====== 交互状态 ======
    private final List<Vec2d> controlPoints = new ArrayList<>();
    private Vec2d currentMousePoint;
    private int currentStep = 0;
    
    // ====== 几何数据缓存 ======
    private final PreviewGeometry previewGeometry = new PreviewGeometry();
    
    // ====== 几何数据存储类 ======

    
    // ====== 颜色转换辅助方法 ======
    /**
     * 将java.awt.Color转换为ImGui颜色整数
     * 简化ImDrawList渲染中的颜色转换
     */
    private static int toImGuiColor(Color color) {
        return ImColor.rgba(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }
    
    // ====== 构造函数 ======
    
    public PolygonTool(IAppState appState, ISnapManager snapManager) {
        super("polygon", "多边形", Icons.POLYGON_IDENTIFIER, "绘制正多边形", appState, snapManager, InteractionType.CLICK_AND_CLICK);
        
        // 注册配置事件监听
        eventBus.subscribe(ToolConfigEvent.class, this::onToolConfigEvent);
        
        // 使用反射设置正确的交互类型
        setInteractionTypeUsingReflection();
        
        LOGGER.debug("PolygonTool 初始化完成，当前模式: {}，边数: {}", currentMode.getDisplayName(), sides);
    }

    @Deprecated
    public PolygonTool() {
        this(com.plot.core.state.AppState.getInstance(), 
             com.plot.core.snap.SnapManager.getInstance());
    }
    
    // ====== 策略模式集成 ======
    
    /**
     * 通过反射设置交互类型为 CLICK_AND_CLICK
     * 这确保了多边形工具使用自定义策略而不是默认的拖放策略
     * <p>
     * 注意：由于基类 DrawingTool 的 interactionStrategy 字段是 final 且无法通过构造函数注入，
     * 因此采用反射进行设置。如果基类提供 setInteractionStrategy 方法，应优先使用该方法。
     */
    private void setInteractionTypeUsingReflection() {
        try {
            Field strategyField = DrawingTool.class.getDeclaredField("interactionStrategy");
            strategyField.setAccessible(true);
            strategyField.set(this, new PolygonInteractionStrategy());
            LOGGER.debug("成功设置多边形工具交互策略");
        } catch (Exception e) {
            LOGGER.error("设置交互策略失败: {}", e.getMessage(), e);
        }
    }
    
    // ====== 基类实现 ======
    
    @Override
    protected Shape createShape(Vec2d startPoint, Vec2d endPoint) {
        // 对于多边形工具，这个方法主要用于向后兼容
        // 实际的图形创建在自定义策略中完成
        if (startPoint == null || endPoint == null) return null;
        
        double radius = Math.max(startPoint.distance(endPoint), MIN_RADIUS);
        List<Vec2d> vertices = generatePolygonVertices(startPoint, radius, sides, 0.0);
        
        Polygon polygon = new Polygon(vertices);
        ShapeStyle style = getStyleHandler().getFinalStyle();
        if (style != null) {
            polygon.setStyle(style);
        }
        
        return polygon;
    }

    @Override
    protected IInteractionStrategy createStrategy(InteractionType type) {
        // 标准多边形工具采用多步策略
        return MultiStepInteractionStrategy.forPolygonTool();
    }

    @Override
    protected void renderPreview(DrawContext context) {
        // 始终渲染捕捉指示器（即使尚未开始绘制）
        snapEnhancer.renderSnapIndicator(context);

        // 根据模式渲染不同的预览内容（只在有控制点时）
        if (!controlPoints.isEmpty()) {
            updatePreviewGeometry();
            renderPreviewGeometry(context);
        }
    }

    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (camera == null) return;
        // 始终渲染捕捉指示器（即使尚未开始绘制）
        snapEnhancer.renderSnapIndicator(drawList, camera);
        
        // 根据模式渲染不同的预览内容（只在有控制点时）
        if (!controlPoints.isEmpty()) {
            updatePreviewGeometry();
            renderPreviewGeometry(drawList, camera);
        }
        
        // 总是渲染控制点
        renderControlPoints(drawList, camera);
        
        // 渲染当前鼠标位置的吸附指示器
        renderMouseSnapIndicator(drawList, camera);
    }
    
    // ====== 几何计算与渲染分离 ======
    
    /**
     * 统一更新预览几何数据
     * 根据当前模式和用户输入，计算出预览所需的所有几何信息
     */
    private void updatePreviewGeometry() {
        previewGeometry.clear();
        
        if (controlPoints.isEmpty() || currentMousePoint == null) {
            return;
        }
        
        // 根据当前模式计算几何数据
        switch (currentMode) {
            case CENTER_RADIUS -> updateCenterRadiusGeometry();
            case CENTER_VERTEX -> updateCenterVertexGeometry();
        }
        
        // 设置通用数据
        previewGeometry.controlPoints = new ArrayList<>(controlPoints);
        previewGeometry.currentMousePoint = currentMousePoint;
        
        // 计算辅助线
        if (previewGeometry.polygon != null) {
            previewGeometry.guideLines = calculateGuideLines();
        }
        
        // 计算信息文本
        previewGeometry.infoText = calculateInfoText();
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
     * 更新中心-半径模式的几何数据
     */
    private void updateCenterRadiusGeometry() {
        Vec2d center = controlPoints.getFirst();
        double radius = Math.max(center.distance(currentMousePoint), MIN_RADIUS);
        
        // 标准朝向，第一个顶点在上方（固定朝向）
        double startAngle = -Math.PI / 2;
        
        List<Vec2d> vertices = generatePolygonVertices(center, radius, sides, startAngle);
        previewGeometry.polygon = new Polygon(vertices);
        previewGeometry.polygon.setStyle(getStyleHandler().getPreviewStyle());
        previewGeometry.center = center;
        previewGeometry.vertices = vertices;
        previewGeometry.radius = radius;
    }
    
    /**
     * 更新中心-顶点模式的几何数据
     */
    private void updateCenterVertexGeometry() {
        Vec2d center = controlPoints.getFirst();
        double radius = Math.max(center.distance(currentMousePoint), MIN_RADIUS);
        
        // 计算起始角度，使第二个点位置成为多边形的一个顶点
        double startAngle = Math.atan2(currentMousePoint.y - center.y, currentMousePoint.x - center.x);
        
        List<Vec2d> vertices = generatePolygonVertices(center, radius, sides, startAngle);
        previewGeometry.polygon = new Polygon(vertices);
        previewGeometry.polygon.setStyle(getStyleHandler().getPreviewStyle());
        previewGeometry.center = center;
        previewGeometry.vertices = vertices;
        previewGeometry.radius = radius;
    }
    
    /**
     * 计算辅助线
     */
    private List<Line> calculateGuideLines() {
        List<Line> lines = new ArrayList<>();
        
        if (previewGeometry.center == null || previewGeometry.vertices == null) {
            return lines;
        }
        
        // 从中心到各个顶点的引导线
        for (Vec2d vertex : previewGeometry.vertices) {
            lines.add(new Line(previewGeometry.center, vertex));
        }
        
        return lines;
    }
    
    /**
     * 计算信息文本
     */
    private String calculateInfoText() {
        if (previewGeometry.radius > 0) {
            return String.format("%d边形, 半径: %.2f", sides, previewGeometry.radius);
        }
        return String.format("%d边形", sides);
    }
    
    // ====== 统一渲染方法 ======
    
    /**
     * 使用DrawContext渲染预览几何数据
     */
    private void renderPreviewGeometry(DrawContext context) {
        if (previewGeometry.polygon == null) {
            // 绘制引导线
            if (!controlPoints.isEmpty() && currentMousePoint != null) {
                Vec2d center = controlPoints.getFirst();
                context.drawLine(center, currentMousePoint, GUIDE_LINE_COLOR);
            }
            return;
        }
        
        // 绘制多边形轮廓
        ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
        Color lineColor = previewStyle != null ? previewStyle.getLineColor() : PREVIEW_COLOR;
        
        for (int i = 0; i < previewGeometry.vertices.size(); i++) {
            Vec2d start = previewGeometry.vertices.get(i);
            Vec2d end = previewGeometry.vertices.get((i + 1) % previewGeometry.vertices.size());
            context.drawLine(start, end, lineColor);
        }
        
        // 绘制顶点
        for (Vec2d vertex : previewGeometry.vertices) {
            context.drawCircleFilled(vertex, VERTEX_POINT_SIZE, VERTEX_COLOR);
        }
        
        // 绘制中心点
        if (previewGeometry.center != null) {
            context.drawCircleFilled(previewGeometry.center, CENTER_POINT_SIZE, CENTER_COLOR);
        }
        
        // 绘制辅助线
        if (previewGeometry.guideLines != null) {
            for (Line line : previewGeometry.guideLines) {
                context.drawLine(line.start, line.end, GUIDE_LINE_COLOR);
            }
        }
        
        // 绘制外接圆
        if (previewGeometry.center != null && previewGeometry.radius > 0) {
            context.drawCircle(previewGeometry.center, previewGeometry.radius, GUIDE_LINE_COLOR);
        }
    }
    
    /**
     * 使用ImDrawList渲染预览几何数据
     */
    private void renderPreviewGeometry(ImDrawList drawList, CanvasCamera camera) {
        if (previewGeometry.polygon == null) {
            // 绘制引导线
            if (!controlPoints.isEmpty() && currentMousePoint != null) {
                Vec2d center = controlPoints.getFirst();
                Vec2d screenCenter = camera.worldToScreen(center);
                Vec2d screenMouse = camera.worldToScreen(currentMousePoint);
                
                int guideColor = toImGuiColor(GUIDE_LINE_COLOR);
                drawList.addLine(
                    (float)screenCenter.x, (float)screenCenter.y,
                    (float)screenMouse.x, (float)screenMouse.y,
                    guideColor, 1.5f
                );
            }
            return;
        }
        
        // 设置预览样式
        ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
        Color lineColor = previewStyle != null ? previewStyle.getLineColor() : PREVIEW_COLOR;
        int imguiColor = toImGuiColor(lineColor);
        
        // 绘制多边形轮廓
        for (int i = 0; i < previewGeometry.vertices.size(); i++) {
            Vec2d start = camera.worldToScreen(previewGeometry.vertices.get(i));
            Vec2d end = camera.worldToScreen(previewGeometry.vertices.get((i + 1) % previewGeometry.vertices.size()));
            
            drawList.addLine(
                (float)start.x, (float)start.y,
                (float)end.x, (float)end.y,
                imguiColor, PREVIEW_LINE_THICKNESS
            );
        }
        
        // 绘制顶点
        int vertexColor = toImGuiColor(VERTEX_COLOR);
        for (Vec2d vertex : previewGeometry.vertices) {
            Vec2d screenVertex = camera.worldToScreen(vertex);
            drawList.addCircleFilled(
                (float)screenVertex.x, (float)screenVertex.y,
                VERTEX_POINT_SIZE, vertexColor
            );
        }
        
        // 绘制中心点
        if (previewGeometry.center != null) {
            Vec2d screenCenter = camera.worldToScreen(previewGeometry.center);
            int centerColor = toImGuiColor(CENTER_COLOR);
            drawList.addCircleFilled(
                (float)screenCenter.x, (float)screenCenter.y,
                CENTER_POINT_SIZE, centerColor
            );
        }
        
        // 绘制辅助线
        if (previewGeometry.guideLines != null) {
            int guideColor = toImGuiColor(GUIDE_LINE_COLOR);
            for (Line line : previewGeometry.guideLines) {
                Vec2d screenStart = camera.worldToScreen(line.start);
                Vec2d screenEnd = camera.worldToScreen(line.end);
                drawList.addLine(
                    (float)screenStart.x, (float)screenStart.y,
                    (float)screenEnd.x, (float)screenEnd.y,
                    guideColor, 1.0f
                );
            }
        }
        
        // 绘制外接圆
        if (previewGeometry.center != null && previewGeometry.radius > 0) {
            Vec2d screenCenter = camera.worldToScreen(previewGeometry.center);
            Vec2d screenRadius = camera.worldToScreen(new Vec2d(previewGeometry.center.x + previewGeometry.radius, previewGeometry.center.y));
            float pixelRadius = (float)Math.abs(screenRadius.x - screenCenter.x);
            
            int guideColor = toImGuiColor(GUIDE_LINE_COLOR);
            drawList.addCircle(
                (float)screenCenter.x, (float)screenCenter.y,
                pixelRadius, guideColor, 0, 1.0f
            );
        }
        
        // 显示信息文本
        if (previewGeometry.infoText != null && currentMousePoint != null) {
            Vec2d screenPos = camera.worldToScreen(currentMousePoint);
            drawList.addText(
                (float)screenPos.x + TEXT_OFFSET_X,
                (float)screenPos.y + TEXT_OFFSET_Y,
                imguiColor, previewGeometry.infoText
            );
        }

        // 统一渲染捕捉指示器（ImGui版本）
        snapEnhancer.renderSnapIndicator(drawList, camera);
    }
    
    /**
     * 渲染控制点
     */
    private void renderControlPoints(ImDrawList drawList, CanvasCamera camera) {
        for (int i = 0; i < controlPoints.size(); i++) {
            Vec2d point = controlPoints.get(i);
            Vec2d screenPoint = camera.worldToScreen(point);
            
            // 控制点颜色：第一个点红色（中心点），其他绿色
            Color pointColor = (i == 0 && (currentMode == PolygonMode.CENTER_RADIUS || currentMode == PolygonMode.CENTER_VERTEX)) ? 
                               CENTER_COLOR : VERTEX_COLOR;
            
            int color = toImGuiColor(pointColor);
            
            // 绘制控制点
            drawList.addCircleFilled(
                (float)screenPoint.x, (float)screenPoint.y, 
                6.0f, color
            );
            
            // 绘制吸附指示器
            int snapColor = imgui.ImColor.rgba(SNAP_INDICATOR_COLOR.getRed(), SNAP_INDICATOR_COLOR.getGreen(), SNAP_INDICATOR_COLOR.getBlue(), 200);
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
            
            int color = imgui.ImColor.rgba(SNAP_INDICATOR_COLOR.getRed(), SNAP_INDICATOR_COLOR.getGreen(), SNAP_INDICATOR_COLOR.getBlue(), 180);
            
            drawList.addCircle(
                (float)screenPoint.x, (float)screenPoint.y, 
                SNAP_INDICATOR_SIZE * 0.7f, color, 0, 1.5f
            );
        }
    }

    @Override
    protected boolean shouldShowPreview() {
        // 多边形工具的预览基于控制点和当前状态
        boolean shouldShow = getCurrentState() == ToolState.DRAWING && 
                           (!controlPoints.isEmpty() || currentMousePoint != null);
        LOGGER.debug("PolygonTool: shouldShowPreview() = {}, state: {}, controlPoints: {}, currentMousePoint: {}", 
                    shouldShow, getCurrentState(), controlPoints.size(), currentMousePoint != null);
        return shouldShow;
    }

    @Override
    public void updateConfig(String key, String value) {
        LOGGER.debug("PolygonTool 配置更新: {}={}", key, value);
        
        switch (key) {
            case CONFIG_SIDES:
                updateSides(value);
                break;
                
            case CONFIG_MODE:
                PolygonMode newMode = PolygonMode.fromConfigValue(value);
                if (newMode != currentMode) {
                    currentMode = newMode;
                    resetDrawingState();
                    updateStatusMessageForCurrentMode();
                    LOGGER.debug("多边形模式切换到: {}", currentMode.getDisplayName());
                }
                break;
                
            default:
                LOGGER.debug("PolygonTool: 未知配置键: {}", key);
                break;
        }
    }
    
    private void updateSides(String value) {
        try {
            int newSides = Integer.parseInt(value);
            if (newSides >= MIN_SIDES && newSides <= MAX_SIDES) {
                sides = newSides;
                LOGGER.debug("多边形边数更新为: {}", sides);
                
                // 无需手动更新预览，下一帧的渲染会自动更新
            } else {
                LOGGER.warn("多边形边数超出范围 [{}, {}]: {}", MIN_SIDES, MAX_SIDES, newSides);
            }
        } catch (NumberFormatException e) {
            LOGGER.warn("无效的边数值: {}", value);
        }
    }
    
    // ====== 配置事件处理 ======
    
    /**
     * 处理工具配置事件
     */
    private void onToolConfigEvent(Object eventObj) {
        if (eventObj instanceof ToolConfigEvent event) {
            if ("polygon".equals(event.getToolId())) {
                LOGGER.debug("接收到多边形工具配置事件: {}={}", event.getOptionName(), event.getValue());
                updateConfig(event.getOptionName(), String.valueOf(event.getValue()));
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
        currentStep = 0;
        previewGeometry.clear();
        updateStatusMessageForCurrentMode();
        
        LOGGER.debug("多边形工具状态已重置");
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
     * 多边形工具专用交互策略
     * 支持三种绘制模式的多点点击交互
     */
    private class PolygonInteractionStrategy implements com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy {
        
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
            
            // 增强捕捉：更新可视化与类型标记
            var snap = snapEnhancer.performEnhancedSnap(pos, context);
            Vec2d worldPoint = snap.point;
            if (controlPoints.size() == 1) {
                worldPoint = constrainSecondPointToAxis(controlPoints.getFirst(), worldPoint);
            }
            LOGGER.debug("PolygonTool.onMouseDown: 点击位置={}, 转换后={}", pos, worldPoint);
            
            controlPoints.add(worldPoint);
            currentStep++;
            
            // 所有模式都是两步完成
            if (currentStep >= 2) {
                return InteractionResult.COMPLETE;
            } else {
                updateStatusMessageForCurrentMode();
                return InteractionResult.CONTINUE;
            }
        }
        
        @Override
        public InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context) {
            // 始终执行增强捕捉：在开始绘制之前也更新捕捉状态以显示指示器
            var snap = snapEnhancer.performEnhancedSnap(pos, context);
            currentMousePoint = snap.point;
            if (controlPoints.size() == 1) {
                currentMousePoint = constrainSecondPointToAxis(controlPoints.getFirst(), currentMousePoint);
            }

            // 未开始绘制：仅显示捕捉指示器
            // 触发重绘

            // 无需手动更新预览，渲染循环会自动调用 updatePreviewGeometry()
            return InteractionResult.CONTINUE;
        }
        
        @Override
        public InteractionResult onMouseUp(Vec2d pos, int button, DrawingToolContext context) {
            // 多边形工具使用点击模式，不需要处理鼠标释放
            return InteractionResult.IGNORED;
        }
        
        @Override
        public com.plot.core.model.Shape getFinalShape() {
            if (previewGeometry.polygon != null) {
                Polygon polygon = new Polygon(new ArrayList<>(previewGeometry.polygon.getPoints()));
                
                ShapeStyle style = getStyleHandler().getFinalStyle();
                if (style != null) {
                    polygon.setStyle(style);
                }
                
                LOGGER.debug("PolygonTool 创建多边形: 边数={}, 顶点数={}", 
                            sides, polygon.getPoints().size());
                return polygon;
            }
            
            return null;
        }
        
        @Override
        public void reset() {
            resetDrawingState();
        }
        
        @Override
        public String getStrategyName() {
            return "PolygonInteractionStrategy";
        }
    
        @Override
        public String getStrategyDescription() {
            return "多边形工具多点点击交互策略，支持三种绘制模式";
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
    
    // ====== 几何计算方法 ======
    
    /**
     * 生成正多边形的顶点
     */
    private List<Vec2d> generatePolygonVertices(Vec2d center, double radius, int sides, double startAngle) {
        List<Vec2d> vertices = new ArrayList<>();
        double angleStep = 2 * Math.PI / sides;
        
        for (int i = 0; i < sides; i++) {
            double angle = startAngle + i * angleStep;
            double x = center.x + radius * Math.cos(angle);
            double y = center.y + radius * Math.sin(angle);
            vertices.add(new Vec2d(x, y));
        }
        
        return vertices;
    }
    public PolygonMode getCurrentMode() {
        return currentMode;
    }
}
