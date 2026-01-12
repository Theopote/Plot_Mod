package com.masterplanner.ui.tools.impl.drawing;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.state.IAppState;
import com.masterplanner.api.snap.ISnapManager;
import com.masterplanner.core.geometry.shapes.Polygon;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.canvas.CanvasCamera;
import com.masterplanner.ui.component.Icons;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.tool.ToolConfigEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import imgui.ImDrawList;
import imgui.ImColor;
import com.masterplanner.ui.tools.snap.SnapEnhancer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.masterplanner.ui.tools.impl.drawing.strategy.MultiStepInteractionStrategy;

/**
 * 星形工具 - 策略模式兼容版本 (三点绘制)
 * * <p>功能特色：</p>
 * <ul>
 * <li><b>三点绘制:</b> 1. 中心点, 2. 外角点 (决定外半径和旋转), 3. 内角点 (决定内半径)</li>
 * <li>可配置星形角点数量（3-20个角）</li>
 * <li>内外半径比例和旋转角度由绘制过程动态决定</li>
 * <li>实时预览和参数配置</li>
 * <li>完整的策略模式集成</li>
 * </ul>
 * * <p>配置参数：</p>
 * <ul>
 * <li>points: 星形角点数量（默认5，范围3-20）</li>
 * <li>innerRatio: 内外半径比例（默认0.4，范围0.1-0.9），<b>仅作为初始默认值，实际绘制时会被交互动态覆盖</b></li>
 * <li>rotation: 旋转角度（默认0°，范围0-360°），<b>仅作为初始默认值，实际绘制时会被交互动态覆盖</b></li>
 * <li>filled: 是否填充（默认false）</li>
 * <li>innerTwist/outerTwist: 内外顶点扭转角度（度），可通过UI动态调整</li>
 * </ul>
 * <p><b>注意：</b>innerRatio 和 rotation 的 UI 配置仅影响首次绘制的初始状态，实际星形参数由鼠标交互动态决定。</p>
 * @author MasterPlanner Team
 * @version 3.0 - 三点绘制交互模式
 */
public class StarTool extends DrawingTool {
    // 统一捕捉可视化
    private final com.masterplanner.ui.tools.snap.SnapEnhancer snapEnhancer =
            new com.masterplanner.ui.tools.snap.SnapEnhancer("StarTool");
    private static final Logger LOGGER = LoggerFactory.getLogger(StarTool.class);

    // ====== 配置常量 ======
    public static final String CONFIG_KEY_POINTS = "points";
    public static final String CONFIG_KEY_INNER_RATIO = "innerRatio";
    public static final String CONFIG_KEY_ROTATION = "rotation";
    public static final String CONFIG_KEY_FILLED = "filled";

    // ====== 数值常量 ======
    private static final int DEFAULT_POINTS = 5;
    private static final int MIN_POINTS = 3;
    private static final int MAX_POINTS = 20;

    private static final double DEFAULT_INNER_RATIO = 0.4;
    private static final double MIN_INNER_RATIO = 0.1;
    private static final double MAX_INNER_RATIO = 0.9;

    private static final double DEFAULT_ROTATION = 0.0;

    private static final double MIN_RADIUS = 0.5; // 最小半径限制
    private static final double START_ANGLE_OFFSET_RAD = -Math.PI / 2.0; // 默认顶点朝上

    // ====== 新增：扭转角度常量 ======
    public static final String CONFIG_KEY_INNER_TWIST = "innerTwist";
    public static final String CONFIG_KEY_OUTER_TWIST = "outerTwist";

    // 扭转角度范围常量 - 在工具和UI渲染器间共享
    public static final double MIN_TWIST_DEGREES = -90.0;
    public static final double MAX_TWIST_DEGREES = 90.0;

    private double innerTwist = 0.0; // 内顶点扭转角度（度）
    private double outerTwist = 0.0; // 外顶点扭转角度（度）

    // ====== 渲染常量 ======
    private static final int CENTER_POINT_COLOR = 0xFFFF0000; // 红色中心点
    private static final int OUTER_RADIUS_COLOR = 0x80FF8800; // 橙色外半径线
    private static final int INNER_RADIUS_COLOR = 0x8000FFFF; // 紫色内半径线
    private static final float CENTER_POINT_SIZE = 4.0f;
    private static final float RADIUS_LINE_THICKNESS = 1.5f;
    private static final float STAR_LINE_THICKNESS = 2.0f;

    // ====== 配置参数 ======
    private int starPoints = DEFAULT_POINTS;
    private double innerRatio = DEFAULT_INNER_RATIO;
    private double rotation = DEFAULT_ROTATION; // 旋转角度（度）
    private boolean filled = false;

    // ====== 构造函数 ======
    public StarTool(IAppState appState, ISnapManager snapManager) {
        super("star", "星形", Icons.STAR_IDENTIFIER, "绘制星形 (三点式)", appState, snapManager, InteractionType.CLICK_AND_CLICK);
        init(); // 调用初始化方法
    }

    @Deprecated
    public StarTool() {
        super("star", "星形", Icons.STAR_IDENTIFIER, "绘制星形 (三点式)");
        init(); // 调用初始化方法
    }

    /**
     * 封装通用的初始化逻辑，如事件订阅
     */
    private void init() {
        EventBus.getInstance().subscribe(ToolConfigEvent.class, event -> {
            if (event instanceof ToolConfigEvent toolConfigEvent &&
                    getId().equals(toolConfigEvent.getToolId())) {
                updateConfig(toolConfigEvent.getOptionName(), String.valueOf(toolConfigEvent.getValue()));
            }
        });
        LOGGER.debug("StarTool 初始化完成（三点绘制模式）");
    }

    /**
     * 获取当前交互点（使用接口方法，避免强转）
     */
    private List<Vec2d> getInteractionPoints() {
        return interactionStrategy.getControlPoints();
    }

    // ====== 交互与形状创建 ======

    @Override
    protected com.masterplanner.ui.tools.impl.drawing.strategy.IInteractionStrategy createStrategy(InteractionType type) {
        // 修改为三步交互：1.中心, 2.外顶点, 3.内顶点
        return new MultiStepInteractionStrategy(3, false, "星形工具") {
            @Override
            public InteractionResult onMouseDown(Vec2d pos, int button, DrawingToolContext context) {
                // 增强吸附：点击前更新吸附状态，并使用吸附点
                SnapEnhancer.SnapResult snap = snapEnhancer.performEnhancedSnap(pos, context);
                return super.onMouseDown(snap.point, button, context);
            }

            @Override
            public InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context) {
                // 始终增强吸附：即使未开始也更新指示器
                SnapEnhancer.SnapResult snap = snapEnhancer.performEnhancedSnap(pos, context);
                InteractionResult result = super.onMouseMove(snap.point, context);
                // 如果未进入绘制，返回 CONTINUE 以触发重绘显示指示器
                return result == InteractionResult.IGNORED ? InteractionResult.CONTINUE : result;
            }
        };
    }

    /**
     * [重命名并重构]
     * 根据交互点和当前鼠标位置创建预览形状。
     * 这个方法不用于创建最终的持久化图形。
     * @param points 交互控制点
     * @param currentMousePos 当前鼠标位置 (可能为null)
     * @return 用于预览的 Polygon，或者在无效输入时返回 null
     */
    public Polygon createPreviewShape(List<Vec2d> points, Vec2d currentMousePos) {
        if (points == null || points.isEmpty()) {
            return null;
        }

        Vec2d center = points.get(0);

        switch (points.size()) {
            case 1:
                // 步骤1预览：定义外顶点
                Vec2d outerPos = Optional.ofNullable(currentMousePos)
                        .orElse(new Vec2d(center.x + MIN_RADIUS, center.y)); // 使用 Optional 简化
                double outerRadius = Math.max(center.distance(outerPos), MIN_RADIUS);
                double rotationRad = Math.atan2(outerPos.y - center.y, outerPos.x - center.x) - START_ANGLE_OFFSET_RAD;
                return new Polygon(generateRegularPolygonVertices(center, outerRadius, starPoints, rotationRad));

            case 2:
                // 步骤2预览：定义内顶点
                if (currentMousePos == null) return null;
                Vec2d outerVertex = points.get(1);
                double finalOuterRadius = center.distance(outerVertex);
                double innerRadius = Math.max(0.0, Math.min(center.distance(currentMousePos), finalOuterRadius));
                double finalRotationRad = Math.atan2(outerVertex.y - center.y, outerVertex.x - center.x) - START_ANGLE_OFFSET_RAD;
                return new Polygon(generateStarVertices(center, finalOuterRadius, innerRadius, starPoints, finalRotationRad));

            default:
                // 如果点数超过2，理论上交互已完成，不再需要预览。返回null是安全的。
                return null;
        }
    }

    // 在 updatePreview 方法中调用新的 createPreviewShape 方法
    protected void updatePreview(List<Vec2d> points, Vec2d currentMousePos) {
        this.previewShape = createPreviewShape(points, currentMousePos);
        if (this.previewShape == null && !points.isEmpty()) {
            LOGGER.warn("StarTool.updatePreview: 未能生成有效的预览形状，交互点数量: {}", points.size());
        }
    }

    /**
     * 创建星形图形（供 MultiStepInteractionStrategy 调用）
     * 这个方法处理三点交互：中心点、外顶点、内顶点
     */
    public Shape createShapeFromPoints(List<Vec2d> points) {
        if (points == null || points.isEmpty()) {
            return null;
        }

        // 如果点数正好是3个，直接创建最终星形
        if (points.size() == 3) {
            return createFinalStar(points);
        }

        // 如果点数少于3个，创建预览形状
        if (points.size() < 3) {
            // 对于预览，最后一个点可能是当前鼠标位置
            Vec2d currentMousePos = points.size() > 1 ? points.get(points.size() - 1) : null;
            List<Vec2d> controlPoints = points.size() > 1 ? points.subList(0, points.size() - 1) : points;
            return createPreviewShape(controlPoints, currentMousePos);
        }

        // 如果点数超过3个，取前3个点创建最终星形
        return createFinalStar(points.subList(0, 3));
    }

    /**
     * 创建最终星形（严格的三点要求）
     * 这个方法只在交互完成时调用，确保创建的是完整的星形
     */
    public Shape createFinalStar(List<Vec2d> points) {
        if (points == null || points.size() < 3) {
            LOGGER.warn("无法创建星形，需要3个控制点，实际提供了 {} 个。", points != null ? points.size() : 0);
            return null; // 明确表示无法创建
        }

        try {
            Vec2d center = points.get(0);
            Vec2d outerVertex = points.get(1);
            Vec2d innerVertex = points.get(2);

            double outerRadius = center.distance(outerVertex);
            double innerRadius = center.distance(innerVertex);
            innerRadius = Math.max(0.0, Math.min(innerRadius, outerRadius));

            double rotationRad = Math.atan2(outerVertex.y - center.y, outerVertex.x - center.x) - START_ANGLE_OFFSET_RAD;

            List<Vec2d> vertices = generateStarVertices(center, outerRadius, innerRadius, starPoints, rotationRad);

            if (vertices.isEmpty()) return null;

            Polygon star = new Polygon(vertices);
            
            // 应用样式
            ShapeStyle style = getStyleHandler().getFinalStyle();
            if (style != null) {
                star.setStyle(style);
            }
            
            LOGGER.debug("成功创建最终的星形。");
            return star;
        } catch (Exception e) {
            LOGGER.error("创建最终星形失败", e);
            return null;
        }
    }

    // 仅为兼容基类要求，星形工具使用 createShapeFromPoints 进行三点交互创建
    @Override
    protected Shape createShape(Vec2d startPoint, Vec2d endPoint) {
        // 仅为兼容基类要求，星形工具使用 createPreviewShape 和 createFinalStar 进行三点交互创建
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("StarTool.createShape(Vec2d, Vec2d) 被调用，但该方法不支持两点创建星形。");
        }
        return null;
    }

    /**
     * 生成星形顶点
     */
    private List<Vec2d> generateStarVertices(Vec2d center, double outerRadius, double innerRadius,
                                             int points, double rotationRad) {
        List<Vec2d> vertices = new ArrayList<>();
        double angleStep = Math.PI / points; // 顶点间的角度步进
        double innerTwistRad = Math.toRadians(innerTwist);
        double outerTwistRad = Math.toRadians(outerTwist);
        for (int i = 0; i < points * 2; i++) {
            // 偶数为外顶点，奇数为内顶点
            double twist = (i % 2 == 0) ? outerTwistRad : innerTwistRad;
            double currentAngle = i * angleStep + rotationRad + START_ANGLE_OFFSET_RAD + twist;
            double radius = (i % 2 == 0) ? outerRadius : innerRadius;
            double x = center.x + radius * Math.cos(currentAngle);
            double y = center.y + radius * Math.sin(currentAngle);
            vertices.add(new Vec2d(x, y));
        }
        return vertices;
    }

    // ====== 预览逻辑 ======

    /**
     * 生成正多边形顶点
     * @param center 中心点
     * @param radius 半径
     * @param sides 边数
     * @param rotationRad 旋转角度（弧度）
     * @return 正多边形顶点列表
     */
    private List<Vec2d> generateRegularPolygonVertices(Vec2d center, double radius, int sides, double rotationRad) {
        List<Vec2d> vertices = new ArrayList<>();
        double angleStep = 2.0 * Math.PI / sides; // 正多边形顶点间的角度步进

        for (int i = 0; i < sides; i++) {
            double currentAngle = i * angleStep + rotationRad + START_ANGLE_OFFSET_RAD;
            double x = center.x + radius * Math.cos(currentAngle);
            double y = center.y + radius * Math.sin(currentAngle);
            vertices.add(new Vec2d(x, y));
        }
        return vertices;
    }

    // ====== 渲染 ======

    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        // 始终渲染捕捉指示器（即使尚未开始绘制）
        snapEnhancer.renderSnapIndicator(drawList, camera);

        List<Vec2d> interactionPoints = getInteractionPoints();
        int step = interactionPoints.size();

        if (step == 0) return; // 未开始绘制

        // 步骤1：绘制从中心到鼠标的半径线
        if (step == 1) {
            Optional<Vec2d> currentMousePoint = Optional.ofNullable(interactionStrategy.getCurrentMousePoint());
            currentMousePoint.ifPresent(mousePoint -> {
                Vec2d center = camera.worldToScreen(interactionPoints.getFirst());
                Vec2d mouse = camera.worldToScreen(mousePoint);
                drawList.addLine((float)center.x, (float)center.y, (float)mouse.x, (float)mouse.y, OUTER_RADIUS_COLOR, RADIUS_LINE_THICKNESS);
                drawList.addCircleFilled((float)center.x, (float)center.y, CENTER_POINT_SIZE, CENTER_POINT_COLOR);
            });
        }

        // 渲染预览形状（步骤1和2都会生成previewShape）
        if (previewShape instanceof Polygon star && !star.getPoints().isEmpty()) {
            renderStarPreview(drawList, camera, star, interactionPoints);
        }
    }

    private void renderStarPreview(ImDrawList drawList, CanvasCamera camera, Polygon star, List<Vec2d> interactionPoints) {
        ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
        Color lineColor = previewStyle != null ? previewStyle.getLineColor() : Color.YELLOW;
        int imguiColor = ImColor.rgba(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), lineColor.getAlpha());

        List<Vec2d> vertices = star.getPoints();

        // 绘制星形轮廓
        for (int i = 0; i < vertices.size(); i++) {
            Vec2d p1 = camera.worldToScreen(vertices.get(i));
            Vec2d p2 = camera.worldToScreen(vertices.get((i + 1) % vertices.size()));
            drawList.addLine((float)p1.x, (float)p1.y, (float)p2.x, (float)p2.y, imguiColor, STAR_LINE_THICKNESS);
        }

        // 绘制辅助元素
        Vec2d center = camera.worldToScreen(interactionPoints.get(0));
        drawList.addCircleFilled((float)center.x, (float)center.y, CENTER_POINT_SIZE, CENTER_POINT_COLOR);

        if (interactionPoints.size() == 2) {
            // 步骤2: 绘制固定的外半径线和动态的内半径线
            Vec2d outerVertex = camera.worldToScreen(interactionPoints.get(1));
            drawList.addLine((float)center.x, (float)center.y, (float)outerVertex.x, (float)outerVertex.y, OUTER_RADIUS_COLOR, RADIUS_LINE_THICKNESS);

            // 绘制动态内半径线
            Optional<Vec2d> currentMousePoint = Optional.ofNullable(interactionStrategy.getCurrentMousePoint());
            currentMousePoint.ifPresent(mousePoint -> {
                Vec2d mouse = camera.worldToScreen(mousePoint);
                drawList.addLine((float)center.x, (float)center.y, (float)mouse.x, (float)mouse.y, INNER_RADIUS_COLOR, RADIUS_LINE_THICKNESS);
            });
        }

        // 预览填充效果 (使用三角化)
        if (filled && previewStyle != null) {
            // 统一填充色为线条颜色，保证与最终形状一致
            int fillImguiColor = ImColor.rgba(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), lineColor.getAlpha());
            for (int i = 0; i < vertices.size(); i++) {
                Vec2d p1 = camera.worldToScreen(vertices.get(i));
                Vec2d p2 = camera.worldToScreen(vertices.get((i + 1) % vertices.size()));
                drawList.addTriangleFilled((float)center.x, (float)center.y, (float)p1.x, (float)p1.y, (float)p2.x, (float)p2.y, fillImguiColor);
            }
        }
    }

    @Override
    protected void renderPreview(DrawContext context) {
        // 始终渲染捕捉指示器（即使尚未开始绘制）
        snapEnhancer.renderSnapIndicator(context);

        // DrawContext 渲染可以保持简单，主要依赖 ImGui 渲染
        if (previewShape instanceof Polygon star) {
            Color lineColor = getStyleHandler().getPreviewStyle().getLineColor();
            List<Vec2d> vertices = star.getPoints();
            for (int i = 0; i < vertices.size(); i++) {
                context.drawLine(vertices.get(i), vertices.get((i + 1) % vertices.size()), lineColor);
            }
        }
    }


    // ====== 配置管理 (无需修改) ======
    @Override
    public void updateConfig(String key, String value) {
        if (key == null || value == null) return;
        try {
            switch (key) {
                case CONFIG_KEY_POINTS:
                    int newPoints = Integer.parseInt(value);
                    if (newPoints >= MIN_POINTS && newPoints <= MAX_POINTS) this.starPoints = newPoints;
                    break;
                case CONFIG_KEY_INNER_RATIO:
                    // 仅作为初始默认值，实际绘制时会被交互动态覆盖
                    double newRatio = Double.parseDouble(value);
                    if (newRatio >= MIN_INNER_RATIO && newRatio <= MAX_INNER_RATIO) this.innerRatio = newRatio;
                    break;
                case CONFIG_KEY_ROTATION:
                    // 仅作为初始默认值，实际绘制时会被交互动态覆盖
                    double newRotation = Double.parseDouble(value);
                    this.rotation = ((newRotation % 360) + 360) % 360;
                    break;
                case CONFIG_KEY_FILLED:
                    this.filled = Boolean.parseBoolean(value);
                    break;
                // 新增对innerTwist和outerTwist的支持
                case CONFIG_KEY_INNER_TWIST:
                    double newInnerTwist = Double.parseDouble(value);
                    // 限制范围为 -90 到 90 度，防止极端扭曲
                    this.innerTwist = Math.max(MIN_TWIST_DEGREES, Math.min(MAX_TWIST_DEGREES, newInnerTwist));
                    break;
                case CONFIG_KEY_OUTER_TWIST:
                    double newOuterTwist = Double.parseDouble(value);
                    // 限制范围为 -90 到 90 度，防止极端扭曲
                    this.outerTwist = Math.max(MIN_TWIST_DEGREES, Math.min(MAX_TWIST_DEGREES, newOuterTwist));
                    break;
            }
        } catch (Exception e) {
            LOGGER.error("StarTool.updateConfig: 配置更新异常 - key={}, value={}", key, value, e);
        }
    }
}