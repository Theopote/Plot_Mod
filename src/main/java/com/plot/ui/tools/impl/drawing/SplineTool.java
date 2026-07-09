package com.plot.ui.tools.impl.drawing;

import com.plot.api.geometry.Vec2d;
import com.plot.api.state.IAppState;
import com.plot.api.snap.ISnapManager;
import com.plot.api.tool.IToolConfig;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.graphics.DrawContext;
import com.plot.core.model.Shape;

import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.component.Icons;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy;
import com.plot.ui.tools.impl.drawing.config.SplineConfig;
import com.plot.ui.tools.impl.drawing.strategy.ISplineGenerationStrategy;
import com.plot.ui.tools.impl.drawing.strategy.ThroughPointsSplineStrategy;
import com.plot.ui.tools.impl.drawing.strategy.ControlPolygonSplineStrategy;
import com.plot.ui.tools.event.SplineConfigChangedEvent;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolConfigEvent;

import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import imgui.ImDrawList;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 样条曲线工具 - 策略模式版本
 * 
 * <p>支持两种样条曲线类型：</p>
 * <ul>
 *   <li>贝塞尔曲线：通过控制点创建平滑的参数曲线</li>
 *   <li>插值样条：曲线通过所有给定的点</li>
 * </ul>
 * 
 * <p>功能特色：</p>
 * <ul>
 *   <li>完整的策略模式集成</li>
 *   <li>多点点击式交互</li>
 *   <li>实时预览和控制点编辑</li>
 *   <li>自适应控制点生成</li>
 *   <li>丰富的参数配置选项</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 2.0 - 策略模式版本
 */
public class SplineTool extends DrawingTool {
    // 统一捕捉可视化
    private final com.plot.ui.tools.snap.SnapEnhancer snapEnhancer =
            new com.plot.ui.tools.snap.SnapEnhancer("SplineTool");
    private static final Logger LOGGER = LoggerFactory.getLogger(SplineTool.class);

    // ====== 配置常量 ======
    
    // 1. 统一配置键
    public static final String CONFIG_KEY_MODE = "spline_mode";
    public static final String CONFIG_KEY_TENSION = "spline_tension";
    public static final String CONFIG_KEY_SEGMENTS = "spline_segments";
    public static final double DEFAULT_SMOOTHNESS = 0.5;
    
    // 2. 统一模式枚举
    public enum SplineMode {
        THROUGH_POINTS("through_points", "mode.plot.spline.fit_short", "mode.plot.spline_fit"),
        CONTROL_POLYGON("control_polygon", "mode.plot.spline.control_short", "mode.plot.spline_control");
        private final String id;
        private final String nameKey;
        private final String descKey;
        SplineMode(String id, String nameKey, String descKey) {
            this.id = id;
            this.nameKey = nameKey;
            this.descKey = descKey;
        }
        public String getId() { return id; }
        public String getDisplayName() { return PlotI18n.modeLabel(nameKey); }
        public String getDescription() { return PlotI18n.modeLabel(descKey); }
        public static SplineMode fromId(String id) {
            for (SplineMode mode : values()) {
                if (mode.id.equals(id)) return mode;
            }
            return THROUGH_POINTS;
        }
    }
    
    // 3. 状态字段 - 使用配置对象和策略模式
    private final SplineConfig config = new SplineConfig();
    private ISplineGenerationStrategy currentGenerationStrategy;
    private final List<Vec2d> controlPoints = new ArrayList<>();
    private Vec2d currentMousePoint;
    private int selectedPointIndex = -1;
    
    // 4. 渲染常量 - 统一管理所有渲染参数
    // 尺寸常量
    private static final double POINT_SIZE = 3.0;
    private static final double SELECTED_POINT_SIZE = 4.0;
    private static final double CURRENT_MOUSE_SIZE = 2.0;
    private static final double CURVE_THICKNESS = 3.0;
    private static final double HELPER_LINE_THICKNESS = 1.0;
    private static final double TEXT_OFFSET_X = 8.0;
    private static final double TEXT_OFFSET_Y = 8.0;
    
    // 5. 状态消息
    private static final List<String> STATUS_MESSAGES = List.of(
        "status.plot.draw.spline.add_point",
        "status.plot.draw.spline.continue",
        "status.plot.draw.spline.continue_close"
    );
    
    // ====== 构造函数 ======
    
    /**
     * 依赖注入构造函数（推荐）
     */
    public SplineTool(IAppState appState, ISnapManager snapManager) {
        super("spline", Icons.SPLINE_IDENTIFIER, appState, snapManager, InteractionType.CLICK_AND_CLICK);
        
        // 初始化策略
        updateGenerationStrategy();
        
        // 订阅配置事件
        eventBus.subscribe(ToolConfigEvent.class, this::handleConfigEvent);
        
        // 其他初始化
        updateStatusMessage();
        
        LOGGER.debug("SplineTool 初始化完成。配置: {}", config);
    }

    /**
     * 默认构造函数（兼容性）
     * @deprecated 使用依赖注入构造函数
     */
    @Deprecated
    public SplineTool() {
        this(null, null);
    }
    
    // ====== 状态管理方法 ======
    
    /**
     * 更新状态消息
     */
    private void updateStatusMessage() {
        int index;
        if (controlPoints.isEmpty()) {
            index = 0;
        } else if (controlPoints.size() < 3) {
            index = 1;
        } else {
            index = 2; // 显示包含C键封闭提示的消息
        }
        
        String baseMessage = PlotI18n.status(STATUS_MESSAGES.get(index));
        String modeName = config.getCurrentMode().getDisplayName();

        if (controlPoints.isEmpty()) {
            setStatusMessage(PlotI18n.status("status.plot.draw.spline.with_mode", baseMessage, modeName));
        } else {
            setStatusMessage(PlotI18n.status("status.plot.draw.spline.with_mode_points",
                    baseMessage, modeName, controlPoints.size()));
        }
        LOGGER.debug("样条工具状态消息已更新");
    }
    
    /**
     * 重置绘制状态
     */
    private void resetDrawingState() {
        controlPoints.clear();
        currentMousePoint = null;
        selectedPointIndex = -1;
        updateStatusMessage();
        LOGGER.debug("样条工具状态已重置");
    }
    
    // ====== 基类重写方法 ======
    
    @Override
    public void onActivate() {
        LOGGER.debug("SplineTool activated");
        resetDrawingState();
        updateStatusMessage();
        
        // 发布工具激活事件，包含当前所有配置
        SplineConfigChangedEvent activationEvent = new SplineConfigChangedEvent(
            getId(), "tool_activated", null, null, config
        );
        eventBus.publish(activationEvent);
    }
    
    @Override
    public void onDeactivate() {
        LOGGER.debug("SplineTool deactivated");
        resetDrawingState();
        setStatusMessage("");
    }

    /**
     * 处理键盘事件
     * 按C键封闭样条曲线
     */
    @Override
    public boolean onKeyDown(int keyCode) {
        try {
            // 按C键封闭样条曲线
            if (keyCode == KeyEvent.VK_C && controlPoints.size() >= 3) {
                closeSpline();
                return true;
            }
            
            // 按ESC键取消绘制
            if (keyCode == KeyEvent.VK_ESCAPE && !controlPoints.isEmpty()) {
                resetDrawingState();
                return true;
            }
            
            return super.onKeyDown(keyCode);
        } catch (Exception e) {
            LOGGER.error("SplineTool 处理键盘事件失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean onKeyTyped(char character) {
        char normalized = Character.toLowerCase(character);
        if (normalized == 'c' && controlPoints.size() >= 3) {
            closeSpline();
            return true;
        }
        return super.onKeyTyped(character);
    }
    
    /**
     * 封闭样条曲线 - 简化版本，完全委托给策略
     */
    private void closeSpline() {
        if (controlPoints.size() < currentGenerationStrategy.getMinimumClosedPointCount()) {
            LOGGER.warn("点数不足，无法封闭样条曲线，当前点数: {}, 需要: {}", 
                controlPoints.size(), currentGenerationStrategy.getMinimumClosedPointCount());
            return;
        }
        
        try {
            // 直接将职责委托给当前策略
            BezierCurveShape closedSpline = currentGenerationStrategy.generateClosedCurve(controlPoints, config);
            
            if (closedSpline != null) {
                if (getStyleHandler() != null) {
                    getStyleHandler().applyFinalStyle(closedSpline);
                }
                commitShape(closedSpline);
                resetDrawingState();
                LOGGER.debug("已通过策略模式封闭样条曲线");
            } else {
                LOGGER.warn("当前策略无法创建封闭样条曲线");
            }
        } catch (Exception e) {
            LOGGER.error("封闭样条曲线时发生策略执行错误: {}", e.getMessage(), e);
        }
    }
    

    
    @Override
    protected Shape createShape(Vec2d startPoint, Vec2d endPoint) {
        // 策略模式下，图形创建在策略中完成
        // 这里提供向后兼容的实现

        return null;
    }
    
    @Override
    protected IInteractionStrategy createStrategy(InteractionType type) {
        // 如果有自定义 SplineInteractionStrategy 则返回，否则用多步策略
        return new SplineInteractionStrategy();
    }
    
    // ====== 配置管理 ======
    
    /**
     * 更新工具配置
     */
    @Override
    public void updateConfig(String key, String value) {
        try {
            Object oldValue = config.getValue(key);
            boolean needsUpdate = config.updateConfig(key, value);
            
            if (needsUpdate) {
                // 更新生成策略（如果模式发生变化）
                if (CONFIG_KEY_MODE.equals(key)) {
                    updateGenerationStrategy();
                }
                
                // 更新预览
                updatePreview();
                updateStatusMessage();
                
                // 发布配置变更事件
                Object newValue = config.getValue(key);
                SplineConfigChangedEvent event = new SplineConfigChangedEvent(
                    getId(), key, oldValue, newValue, config
                );
                eventBus.publish(event);
                
                LOGGER.debug("配置已更新: {} = {} (旧值: {})", key, newValue, oldValue);
            }
        } catch (Exception e) {
            LOGGER.error("配置更新错误: key={}, value={}, error={}", key, value, e.getMessage());
        }
    }
    
    /**
     * 获取样条配置对象
     * @return 配置对象副本
     */
    public SplineConfig getSplineConfig() {
        return new SplineConfig(config);
    }
    
    /**
     * 更新生成策略
     */
    private void updateGenerationStrategy() {
        SplineMode mode = config.getCurrentMode();
        currentGenerationStrategy = switch (mode) {
            case THROUGH_POINTS -> new ThroughPointsSplineStrategy();
            case CONTROL_POLYGON -> new ControlPolygonSplineStrategy();
        };
        LOGGER.debug("生成策略已更新为: {}", currentGenerationStrategy.getDisplayName());
    }
    
    /**
     * 处理配置事件（从工具选项面板接收）
     */
    private void handleConfigEvent(Object eventObj) {
        if (!(eventObj instanceof ToolConfigEvent event)) {
            return;
        }
        
        if (!getId().equals(event.getToolId())) {
            return; // 不是我们的事件
        }
        
        String key = event.getOptionName();
        String value = String.valueOf(event.getValue());
        
        LOGGER.debug("SplineTool 接收到配置事件: {} = {}", key, value);
        
        // 调用现有的 updateConfig 方法
        updateConfig(key, value);
    }
    

    
    /**
     * 重写 getConfig 方法以返回配置对象（适配父类接口）
     */
    @Override
    public IToolConfig getConfig() {
        return new com.plot.ui.tools.impl.drawing.config.BaseToolConfigAdapter() {
            @Override
            public Object getValue(String key) {
                return config.getValue(key);
            }
            
            @Override
            public void setValue(String key, Object value) {
                if (value instanceof String stringValue) {
                    config.updateConfig(key, stringValue);
                }
            }
            
            @Override
            public String getDescription() {
                return "Spline Tool Config";
            }
            
            @Override
            public void resetToDefault() {
                config.reset();
            }
        };
    }
    
    // ====== 预览更新方法 (重构后) ======
    /**
     * 【已重构】更新预览的核心方法 - 使用策略模式
     */
    private void updatePreview() {
        if (controlPoints.isEmpty()) {
            this.previewShape = null;
            markDirty();
            return;
        }

        List<Vec2d> previewPoints = new ArrayList<>(controlPoints);
        if (currentMousePoint != null) {
            previewPoints.add(currentMousePoint);
        }

        if (previewPoints.size() < 2) {
            this.previewShape = null;
            markDirty();
            return;
        }

        // 使用生成策略创建曲线
        try {
            this.previewShape = currentGenerationStrategy.generateCurve(previewPoints, config);
            // 如果预览是贝塞尔曲线，应用首选采样段数以响应 UI 的 "采样段数" 设置
            if (this.previewShape instanceof BezierCurveShape previewBezier) {
                // 将 UI 中的 "采样段数" 视作希望的总采样点数，按曲线段分配到每段
                int totalDesired = Math.max(10, config.getSegments());
                int segCount = Math.max(1, previewBezier.getSegmentCount());
                int perSegment = Math.max(4, totalDesired / segCount);
                previewBezier.setPreferredSamplingSteps(perSegment);
            }
            
            // 应用预览样式
            if (this.previewShape != null && getStyleHandler() != null && getStyleHandler().getPreviewStyle() != null) {
                getStyleHandler().applyPreviewStyle(this.previewShape);
            }
        } catch (Exception e) {
            this.previewShape = null;
            LOGGER.warn("无法创建样条曲线预览: {}", e.getMessage());
        }
        
        markDirty();
    }


    
    /**
     * 构建工具提示信息
     */
    private String buildTooltipMessage() {
        SplineMode currentMode = config.getCurrentMode();

        return PlotI18n.status("status.plot.draw.spline.tooltip",
                currentMode.getDisplayName(),
                controlPoints.size(),
                currentMode.getDescription(),
                config.getTension(),
                config.getSegments());
    }
    
    // ====== 预览渲染方法 ======
    
    @Override
    public void renderPreview(DrawContext context) {
        renderControlPoints(context);
        renderHelperLines(context);
        if (this.previewShape != null) {
            // 手动绘制预览曲线，确保使用正确的颜色（参考 EllipseTool 的实现）
            if (this.previewShape instanceof BezierCurveShape spline) {
                Color previewColor = getPreviewColor();
                List<Vec2d> curvePoints = spline.getCurvePoints();
                
                // 手动绘制曲线段
                for (int i = 1; i < curvePoints.size(); i++) {
                    context.drawLine(curvePoints.get(i - 1), curvePoints.get(i), previewColor);
                }
            }
        }
        // 统一渲染捕捉指示器
        snapEnhancer.renderSnapIndicator(context);
    }
    
    /**
     * 渲染控制点
     */
    private void renderControlPoints(DrawContext context) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        java.awt.Color selectedColor = toColor(theme.errorText, 255);
        java.awt.Color controlColor = toColor(theme.infoText, 255);
        java.awt.Color textColor = toColor(theme.text, 255);
        java.awt.Color mouseColor = toColor(theme.warningText, 255);

        for (int i = 0; i < controlPoints.size(); i++) {
            Vec2d point = controlPoints.get(i);
            java.awt.Color color = (i == selectedPointIndex) ? selectedColor : controlColor;
            double size = (i == selectedPointIndex) ? SELECTED_POINT_SIZE : POINT_SIZE;
            
            context.drawCircle(point, size, color);
            
            // 绘制点的索引
            context.drawText(String.valueOf(i + 1), 
                new Vec2d(point.x + TEXT_OFFSET_X, point.y - TEXT_OFFSET_Y), textColor);
        }
        
        // 当前鼠标位置
        if (currentMousePoint != null && !controlPoints.isEmpty()) {
            context.drawCircle(currentMousePoint, CURRENT_MOUSE_SIZE, mouseColor);
        }
    }
    
    /**
     * 渲染辅助线 - 使用虚线
     */
    private void renderHelperLines(DrawContext context) {
        java.awt.Color helperLineColor = toColor(ThemeManager.getInstance().getCurrentTheme().mutedText, 180);
        if (controlPoints.size() < 2) return;
        
        // 绘制控制点之间的虚线连线
        for (int i = 0; i < controlPoints.size() - 1; i++) {
            context.drawDashedLine(controlPoints.get(i), controlPoints.get(i + 1), helperLineColor);
        }
        
        // 当前鼠标到最后一个点的虚线连线
        if (currentMousePoint != null && !controlPoints.isEmpty()) {
            Vec2d lastPoint = controlPoints.getLast();
            context.drawDashedLine(lastPoint, currentMousePoint, helperLineColor);
        }
    }

    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        renderControlPointsImGui(drawList, camera);
        renderHelperLinesImGui(drawList, camera);
        // 渲染曲线预览（ImGui版本）
        if (this.previewShape instanceof BezierCurveShape spline) {
            // 获取预览颜色（直接返回ImGui格式）
            int imguiColor = getPreviewColorInt();
            
            // 绘制曲线
            List<Vec2d> curvePoints = spline.getCurvePoints();
            for (int i = 1; i < curvePoints.size(); i++) {
                Vec2d screenPrev = camera.worldToScreen(curvePoints.get(i - 1));
                Vec2d screenCurr = camera.worldToScreen(curvePoints.get(i));
                drawList.addLine(
                    (float) screenPrev.x, (float) screenPrev.y,
                    (float) screenCurr.x, (float) screenCurr.y,
                    imguiColor, (float) CURVE_THICKNESS
                );
            }
        }
        renderParameterInfoImGui(drawList, camera);
        // 统一渲染捕捉指示器
        snapEnhancer.renderSnapIndicator(drawList, camera);
    }
    
    /**
     * 渲染控制点（ImGui版本）
     */
    private void renderControlPointsImGui(ImDrawList drawList, CanvasCamera camera) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        for (int i = 0; i < controlPoints.size(); i++) {
            Vec2d screenPoint = camera.worldToScreen(controlPoints.get(i));
            int color = (i == selectedPointIndex) ? theme.errorText : theme.infoText;
            float size = (float) ((i == selectedPointIndex) ? SELECTED_POINT_SIZE : POINT_SIZE);
            
            drawList.addCircleFilled(
                (float) screenPoint.x, (float) screenPoint.y,
                size, color
            );
            
            // 绘制点的索引
            drawList.addText(
                (float) (screenPoint.x + TEXT_OFFSET_X), (float) (screenPoint.y - TEXT_OFFSET_Y),
                theme.text, String.valueOf(i + 1)
            );
        }
        
        // 当前鼠标位置
        if (currentMousePoint != null && !controlPoints.isEmpty()) {
            Vec2d screenPoint = camera.worldToScreen(currentMousePoint);
            drawList.addCircleFilled(
                (float) screenPoint.x, (float) screenPoint.y,
                (float) CURRENT_MOUSE_SIZE, theme.warningText
            );
        }
    }
    
    /**
     * 渲染辅助线（ImGui版本）- 使用虚线
     */
    private void renderHelperLinesImGui(ImDrawList drawList, CanvasCamera camera) {
        if (controlPoints.size() < 2) return;
        
        // 绘制控制点之间的虚线连线
        for (int i = 0; i < controlPoints.size() - 1; i++) {
            Vec2d screenStart = camera.worldToScreen(controlPoints.get(i));
            Vec2d screenEnd = camera.worldToScreen(controlPoints.get(i + 1));
            
            drawDashedLine(drawList, screenStart, screenEnd);
        }
        
        // 当前鼠标到最后一个点的虚线连线
        if (currentMousePoint != null && !controlPoints.isEmpty()) {
            Vec2d lastScreenPoint = camera.worldToScreen(controlPoints.getLast());
            Vec2d mouseScreenPoint = camera.worldToScreen(currentMousePoint);
            
            drawDashedLine(drawList, lastScreenPoint, mouseScreenPoint);
        }
    }
    
    /**
     * 获取预览颜色（参考 EllipseTool 和 CircleTool 的实现）
     */
    private java.awt.Color getPreviewColor() {
        try {
            if (getStyleHandler() == null) {
                LOGGER.warn("SplineTool: StyleHandler 为 null");
                return toColor(ThemeManager.getInstance().getCurrentTheme().accent, 200);
            }
            
            com.plot.core.graphics.style.ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
            if (previewStyle == null) {
                LOGGER.warn("SplineTool: 预览样式为 null");
                return toColor(ThemeManager.getInstance().getCurrentTheme().accent, 200);
            }
            
            java.awt.Color layerColor = previewStyle.getLineColor();
            if (layerColor == null) {
                LOGGER.warn("SplineTool: 图层颜色为 null");
                return toColor(ThemeManager.getInstance().getCurrentTheme().accent, 200);
            }
            
            LOGGER.debug("SplineTool: 获取到图层颜色: RGB({}, {}, {}), Alpha: {}", 
                layerColor.getRed(), layerColor.getGreen(), layerColor.getBlue(), layerColor.getAlpha());
            
            // 添加半透明效果用于预览
            java.awt.Color previewColor = new java.awt.Color(layerColor.getRed(), layerColor.getGreen(), 
                                   layerColor.getBlue(), 200);
            LOGGER.debug("SplineTool: 预览颜色: RGB({}, {}, {}), Alpha: {}", 
                previewColor.getRed(), previewColor.getGreen(), previewColor.getBlue(), previewColor.getAlpha());
            
            return previewColor;
        } catch (Exception e) {
            LOGGER.error("SplineTool: 获取预览颜色失败: {}", e.getMessage(), e);
        }

        return toColor(ThemeManager.getInstance().getCurrentTheme().accent, 200);
    }
    
    /**
     * 获取预览颜色（ImGui格式，参考 LineTool 的实现）
     */
    private int getPreviewColorInt() {
        java.awt.Color previewColor = toColor(ThemeManager.getInstance().getCurrentTheme().accent, 255);

        if (getStyleHandler() != null) {
            com.plot.core.graphics.style.ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
            if (previewStyle != null && previewStyle.getLineColor() != null) {
                previewColor = previewStyle.getLineColor();
                LOGGER.debug("SplineTool: 获取到预览颜色: RGB({}, {}, {}), Alpha: {}", 
                    previewColor.getRed(), previewColor.getGreen(), previewColor.getBlue(), previewColor.getAlpha());
            } else {
                LOGGER.warn("SplineTool: 无法获取预览样式或颜色");
            }
        } else {
            LOGGER.warn("SplineTool: StyleHandler 为 null");
        }

        // 转换为ImGui颜色格式
        return (previewColor.getAlpha() << 24) |
                        (previewColor.getBlue() << 16) |
                        (previewColor.getGreen() << 8) |
                        previewColor.getRed();
    }
    
    /**
     * 绘制虚线
     */
    private void drawDashedLine(ImDrawList drawList, Vec2d start, Vec2d end) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        int helperLineColor = withAlpha(theme.mutedText, 0x90);
        float dashLength = 8.0f;  // 虚线段长度
        float gapLength = 4.0f;   // 虚线间隙长度
        
        double totalLength = Math.sqrt(Math.pow(end.x - start.x, 2) + Math.pow(end.y - start.y, 2));
        if (totalLength < 0.1) return; // 太短的线不绘制
        
        double unitX = (end.x - start.x) / totalLength;
        double unitY = (end.y - start.y) / totalLength;
        
        double currentPos = 0;
        boolean drawing = true;
        
        while (currentPos < totalLength) {
            double segmentLength = drawing ? dashLength : gapLength;
            double nextPos = Math.min(currentPos + segmentLength, totalLength);
            
            if (drawing) {
                double x1 = start.x + currentPos * unitX;
                double y1 = start.y + currentPos * unitY;
                double x2 = start.x + nextPos * unitX;
                double y2 = start.y + nextPos * unitY;
                
                drawList.addLine((float) x1, (float) y1, (float) x2, (float) y2, helperLineColor, (float) HELPER_LINE_THICKNESS);
            }
            
            currentPos = nextPos;
            drawing = !drawing;
        }
    }

    /**
     * 渲染参数信息（ImGui版本）
     */
    private void renderParameterInfoImGui(ImDrawList drawList, CanvasCamera camera) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        if (!controlPoints.isEmpty()) {
            Vec2d lastScreenPoint = camera.worldToScreen(controlPoints.getLast());
            String info = buildTooltipMessage();
            
            drawList.addText(
                (float) (lastScreenPoint.x + TEXT_OFFSET_X), 
                (float) (lastScreenPoint.y - TEXT_OFFSET_Y),
                theme.text, info
            );
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static java.awt.Color toColor(int color, int alpha) {
        return new java.awt.Color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, alpha);
    }
    
    // ====== 自定义交互策略 (修正后) ======
    /**
     * 样条工具专用交互策略
     * 支持多点点击交互
     */
    private class SplineInteractionStrategy implements IInteractionStrategy {
        
        @Override
        public String getStrategyName() { return "SplineInteractionStrategy"; }
        @Override
        public String getStrategyDescription() { return PlotI18n.modeLabel("strategy.plot.draw.spline"); }
        /**
         * 【已修正】重置逻辑完全委托给外部工具类
         */
        @Override
        public void reset() {
            controlPoints.clear();
            currentMousePoint = null;
            selectedPointIndex = -1;
            updatePreview();
            updateStatusMessage();
        }
        /**
         * 【已修正】直接返回最后一次更新的预览图形，确保所见即所得
         */
        @Override
        public Shape getFinalShape() {
            Shape finalShape = SplineTool.this.previewShape;
            if (finalShape != null && getStyleHandler() != null) {
                getStyleHandler().applyFinalStyle(finalShape);
            }
            return finalShape;
        }
        @Override
        public InteractionResult onMouseDown(Vec2d pos, int button, DrawingToolContext context) {
            // --- 右键完成逻辑 ---
            if (button == 1) {
                if (controlPoints.size() >= 2) {
                    currentMousePoint = null;
                    updatePreview();
                    return InteractionResult.COMPLETE;
                } else {
                    context.resetDrawing("右键取消");
                    return InteractionResult.CANCEL;
                }
            }
            if (button != 0) return InteractionResult.IGNORED;
            // --- 左键添加点逻辑 ---
            Vec2d worldPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            controlPoints.add(worldPoint);
            if (controlPoints.size() == 1) {
                context.setToolState(ToolState.DRAWING);
            }
            updatePreview();
            updateStatusMessage();
            return InteractionResult.CONTINUE;
        }
        @Override
        public InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context) {
            // 仅在“正在绘制”状态下才响应鼠标移动
            if (SplineTool.this.getCurrentState() != ToolState.DRAWING) {
                return InteractionResult.IGNORED;
            }
            currentMousePoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            updatePreview();
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
} 