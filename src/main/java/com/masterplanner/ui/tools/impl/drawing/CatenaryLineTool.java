package com.masterplanner.ui.tools.impl.drawing;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.state.IAppState;
import com.masterplanner.api.snap.ISnapManager;
import com.masterplanner.core.geometry.shapes.CableShape;
import com.masterplanner.core.geometry.shapes.PolylineShape;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.model.Shape;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.tool.ToolConfigEvent;
import com.masterplanner.ui.canvas.CanvasCamera;
import com.masterplanner.ui.component.Icons;
import com.masterplanner.ui.tools.impl.drawing.strategy.IInteractionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import imgui.ImDrawList;
import imgui.ImColor;
import com.masterplanner.ui.tools.snap.SnapEnhancer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 悬链线工具 - 策略模式兼容版本
 * <p>
 * 支持三点绘制模式：
 * 1. 第一点：起点
 * 2. 第二点：终点  
 * 3. 第三点：弧垂控制点（决定悬链线的弯曲程度）
 * <p>
 * 支持两种绘制模式：
 * - 标准模式（standard）：对称悬链线，弧垂点到中点的距离决定弯曲程度
 * - 样条插值模式（uneven）：非对称悬链线，支持起终点高度不同
 */
public class CatenaryLineTool extends DrawingTool {
    // 统一捕捉可视化
    private final com.masterplanner.ui.tools.snap.SnapEnhancer snapEnhancer =
            new com.masterplanner.ui.tools.snap.SnapEnhancer("CatenaryLineTool");
    private static final Logger LOGGER = LoggerFactory.getLogger(CatenaryLineTool.class);

    // 配置键常量
    public static final String CONFIG_KEY_MODE = "mode";
    public static final String CONFIG_KEY_SAG = "sag";
    public static final String CONFIG_KEY_SEGMENTS = "segments";

    // 绘制模式常量
    public static final String MODE_STANDARD = "standard";  // 标准悬链线（对称）
    public static final String MODE_UNEVEN = "uneven";      // 样条插值悬链线

    // 默认参数
    private static final double DEFAULT_SAG = 1.0;
    private static final int DEFAULT_SEGMENTS = 20;
    private static final int MIN_SEGMENTS = 10;
    private static final int MAX_SEGMENTS = 60;

    // 颜色常量
    private static final Color CONTROL_POINT_COLOR = new Color(0, 100, 255);        // 蓝色控制点
    private static final Color SAG_POINT_COLOR = new Color(255, 50, 50);            // 红色弧垂点
    private static final Color CONNECTOR_LINE_COLOR = new Color(180, 180, 180, 160); // 灰色连接线
    private static final Color PREVIEW_COLOR = new Color(100, 200, 255);            // 预览颜色

    // 渲染常量
    private static final float CONTROL_POINT_SIZE = 6.0f;
    private static final float SAG_POINT_SIZE = 7.0f;
    private static final float PREVIEW_LINE_WIDTH = 2.0f;

    // 配置参数
    private String currentMode = MODE_STANDARD;
    private double sagParameter = DEFAULT_SAG;
    private int segments = DEFAULT_SEGMENTS;

    public CatenaryLineTool(IAppState appState, ISnapManager snapManager) {
        super("catenary", "悬链线", Icons.CATENARY_IDENTIFIER, "绘制悬链线",
                appState, snapManager, InteractionType.CLICK_AND_CLICK);

        // 订阅配置事件
        subscribeToConfigEvents();

        // 设置自定义策略
        this.interactionStrategy = createCatenaryStrategy();
    }

    /**
     * 订阅配置事件
     */
    private void subscribeToConfigEvents() {
        EventBus eventBus = EventBus.getInstance();
        if (eventBus != null) {
            eventBus.subscribe(ToolConfigEvent.class, event -> {
                if (event instanceof ToolConfigEvent configEvent) {
                    if (this.getId().equals(configEvent.getToolId())) {
                        updateConfig(configEvent.getOptionName(), String.valueOf(configEvent.getValue()));
                    }
                }
            });
        }
    }

    @Override
    protected Shape createShape(Vec2d startPoint, Vec2d endPoint) {
        if (startPoint == null || endPoint == null) return null;

        // 默认创建基础悬链线（用于两点拖拽模式）
        CableShape catenary = new CableShape(startPoint, endPoint, sagParameter, segments);
        catenary.setDrawMode(currentMode);

        // 设置合理的默认弧垂
        double distance = startPoint.distance(endPoint);
        double defaultSag = distance * 0.15; // 使用15%的距离作为默认弧垂
        catenary.setSagDepth(defaultSag);

        // 应用样式
        ShapeStyle style = getStyleHandler().getFinalStyle();
        if (style != null) {
            catenary.setStyle(style);
        }

        return catenary;
    }

    /**
     * 创建自定义交互策略
     * 悬链线工具使用自定义的三点交互策略，忽略传入的类型参数
     */
    private IInteractionStrategy createCatenaryStrategy() {
        // 为悬链线工具创建自定义的三点交互策略
        return new CatenaryInteractionStrategy();
    }

    @Override
    protected com.masterplanner.ui.tools.impl.drawing.strategy.IInteractionStrategy createStrategy(InteractionType type) {
        return createCatenaryStrategy();
    }

    @Override
    protected void renderPreview(DrawContext context) {
        // 始终渲染捕捉指示器（即使尚未开始绘制）
        snapEnhancer.renderSnapIndicator(context);

        if (!shouldShowPreview() || previewShape == null) return;
        if (previewShape instanceof CableShape catenary) {
            ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
            Color lineColor = previewStyle != null ? previewStyle.getLineColor() : PREVIEW_COLOR;

            // 渲染悬链线
            List<Vec2d> points = catenary.getPoints();
            if (points != null && points.size() > 1) {
                for (int i = 0; i < points.size() - 1; i++) {
                    context.drawLine(points.get(i), points.get(i + 1), lineColor);
                }
            }
        } else if (previewShape instanceof PolylineShape polyline) {
            // 第一步到第二步移动时的辅助线（起点 -> 当前鼠标位置）
            List<Vec2d> pts = polyline.getPoints();
            if (pts != null && pts.size() >= 2) {
                // 使用与 ImGui 预览一致的辅助线颜色
                context.drawDashedLine(pts.get(0), pts.get(1), CONNECTOR_LINE_COLOR);
            }
        }
    }

    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        // 始终渲染捕捉指示器（即使尚未开始绘制）
        snapEnhancer.renderSnapIndicator(drawList, camera);

        if (!shouldShowPreview()) return;

        // 委托给自定义策略渲染
        if (interactionStrategy instanceof CatenaryInteractionStrategy catenaryStrategy) {
            catenaryStrategy.renderImGuiPreview(drawList, camera);
        }
    }

    @Override
    public void updateConfig(String key, String value) {
        try {
            switch (key) {
                case CONFIG_KEY_MODE -> {
                    if (MODE_STANDARD.equals(value) || MODE_UNEVEN.equals(value)) {
                        currentMode = value;
                        LOGGER.debug("CatenaryLineTool 更新绘制模式: {}", currentMode);

                        // 如果正在交互中，重新计算预览
                        if (interactionStrategy instanceof CatenaryInteractionStrategy catenaryStrategy) {
                            catenaryStrategy.onModeChanged();
                        }
                    }
                }
                case CONFIG_KEY_SAG -> {
                    try {
                        double newSag = Double.parseDouble(value);
                        sagParameter = Math.max(0.05, Math.min(5.0, newSag));
                        LOGGER.debug("CatenaryLineTool 更新悬垂参数: {}", sagParameter);

                        // 如果正在交互中，重新计算预览
                        if (interactionStrategy instanceof CatenaryInteractionStrategy catenaryStrategy) {
                            catenaryStrategy.onParameterChanged();
                        }
                    } catch (NumberFormatException e) {
                        LOGGER.warn("CatenaryLineTool 解析悬垂参数失败: {}", value);
                    }
                }
                case CONFIG_KEY_SEGMENTS -> {
                    try {
                        int newSegments = Integer.parseInt(value);
                        segments = Math.max(MIN_SEGMENTS, Math.min(MAX_SEGMENTS, newSegments));
                        LOGGER.debug("CatenaryLineTool 更新分段数: {}", segments);

                        // 如果正在交互中，重新计算预览
                        if (interactionStrategy instanceof CatenaryInteractionStrategy catenaryStrategy) {
                            catenaryStrategy.onParameterChanged();
                        }
                    } catch (NumberFormatException e) {
                        LOGGER.warn("CatenaryLineTool 解析分段数失败: {}", value);
                    }
                }
                default -> LOGGER.debug("CatenaryLineTool: 未知配置键: {}", key);
            }
        } catch (Exception e) {
            LOGGER.error("CatenaryLineTool 更新配置失败: key={}, value={}", key, value, e);
        }
    }

    /**
     * 悬链线交互策略 - 三点绘制模式
     */
    private class CatenaryInteractionStrategy implements IInteractionStrategy {
        private final List<Vec2d> controlPoints = new ArrayList<>();
        private Vec2d currentMousePoint;
        private CableShape previewCatenary;
        private boolean isActive = false;

        @Override
        public InteractionResult onMouseDown(Vec2d pos, int button, DrawingToolContext context) {
            if (button != 0) return InteractionResult.IGNORED; // 只处理左键

            try {
                // 使用增强吸附，确保吸附指示器与类型信息完整
                SnapEnhancer.SnapResult snapResult = snapEnhancer.performEnhancedSnap(pos, context);
                Vec2d snappedPoint = snapResult.point;
                if (snappedPoint == null) {
                    LOGGER.warn("CatenaryLineTool 获取吸附点失败");
                    return InteractionResult.IGNORED;
                }

                if (!isActive) {
                    startInteraction(snappedPoint, context);
                    return InteractionResult.CONTINUE;
                } else if (controlPoints.size() == 1) {
                    addSecondPoint(snappedPoint, context);
                    return InteractionResult.CONTINUE;
                } else if (controlPoints.size() == 2) {
                    if (controlPoints.get(0) == null || controlPoints.get(1) == null) {
                        LOGGER.error("CatenaryLineTool 第三步失败: 控制点无效");
                        reset();
                        return InteractionResult.CANCEL;
                    }
                    // 完成交互：创建最终图形并保存
                    finishInteraction(snappedPoint, context);
                    
                    // 验证图形是否已正确设置
                    if (previewCatenary == null) {
                        LOGGER.error("CatenaryLineTool finishInteraction 后 previewCatenary 仍为 null");
                        reset();
                        return InteractionResult.CANCEL;
                    }
                    
                    LOGGER.info("CatenaryLineTool 准备返回 COMPLETE，图形ID={}", previewCatenary.getId());
                    return InteractionResult.COMPLETE;
                } else {
                    LOGGER.warn("CatenaryLineTool 处于异常状态，进行重置");
                    reset();
                    return InteractionResult.CANCEL;
                }
            } catch (Exception e) {
                LOGGER.error("CatenaryLineTool 鼠标按下处理失败: {}", e.getMessage(), e);
                reset();
                return InteractionResult.CANCEL;
            }
        }

        @Override
        public InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context) {
            try {
                // 始终执行增强吸附：即使在尚未开始绘制前，也要更新捕捉状态用于指示器渲染
                SnapEnhancer.SnapResult snapResult = snapEnhancer.performEnhancedSnap(pos, context);
                currentMousePoint = snapResult.point;

                if (!isActive) {
                    // 未开始绘制：仅显示捕捉指示器，不更新预览
                    return InteractionResult.CONTINUE;
                }

                updatePreview(context);
                return InteractionResult.CONTINUE;
            } catch (Exception e) {
                LOGGER.error("CatenaryLineTool 鼠标移动处理失败: {}", e.getMessage(), e);
                return InteractionResult.CONTINUE;
            }
        }

        @Override
        public InteractionResult onMouseUp(Vec2d pos, int button, DrawingToolContext context) {
            return InteractionResult.IGNORED; // 点击模式，不处理释放
        }

        private void startInteraction(Vec2d firstPoint, DrawingToolContext context) {
            isActive = true;
            controlPoints.clear();
            controlPoints.add(firstPoint);
            previewCatenary = null;
            context.setToolState(ToolState.DRAWING);
        }

        private void addSecondPoint(Vec2d secondPoint, DrawingToolContext context) {
            if (controlPoints.isEmpty()) {
                LOGGER.error("CatenaryLineTool addSecondPoint 失败: 起点未设置");
                reset();
                return;
            }
            controlPoints.add(secondPoint);
        }

        private void finishInteraction(Vec2d sagPoint, DrawingToolContext context) {
            if (controlPoints.size() < 2 || controlPoints.get(0) == null || controlPoints.get(1) == null) {
                LOGGER.error("CatenaryLineTool finishInteraction 失败: 控制点不足或无效");
                reset();
                return;
            }
            if (sagPoint == null) {
                LOGGER.error("CatenaryLineTool finishInteraction 失败: 弧垂点为null");
                reset();
                return;
            }

            try {
                Vec2d startPoint = controlPoints.get(0);
                Vec2d endPoint = controlPoints.get(1);
                CableShape finalCatenary = createFinalCatenary(startPoint, endPoint, sagPoint);

                if (finalCatenary != null) {
                    // 应用最终样式
                    context.getStyleHandler().applyFinalStyle(finalCatenary);
                    
                    // 保存为最终图形（供 getFinalShape() 返回）
                    this.previewCatenary = finalCatenary;
                    
                    // 同时设置到 context 的 previewShape，确保 DrawingTool 能正确获取
                    context.setPreviewShape(finalCatenary);
                    
                    LOGGER.info("CatenaryLineTool 完成绘制: 起点={}, 终点={}, 控制点={}, 图形ID={}", 
                        startPoint, endPoint, sagPoint, finalCatenary.getId());
                } else {
                    LOGGER.error("CatenaryLineTool 创建最终悬链线失败");
                    reset();
                }
            } catch (Exception e) {
                LOGGER.error("CatenaryLineTool finishInteraction 执行失败: {}", e.getMessage(), e);
                reset();
            }
        }

        private void updatePreview(DrawingToolContext context) {
            if (currentMousePoint == null) {
                context.setPreviewShape(null);
                return;
            }

            try {
                if (controlPoints.size() == 1) {
                    // 第二步：显示从起点到鼠标的直线预览
                    List<Vec2d> linePoints = List.of(controlPoints.getFirst(), currentMousePoint);
                    com.masterplanner.core.geometry.shapes.PolylineShape previewLine =
                            new com.masterplanner.core.geometry.shapes.PolylineShape(linePoints, false);
                    context.getStyleHandler().applyPreviewStyle(previewLine);
                    context.setPreviewShape(previewLine);
                } else if (controlPoints.size() == 2) {
                    // 第三步：显示带控制点的悬链线预览
                    CableShape tempPreview = createFinalCatenary(controlPoints.get(0), controlPoints.get(1), currentMousePoint);
                    if (tempPreview != null) {
                        context.getStyleHandler().applyPreviewStyle(tempPreview);
                        context.setPreviewShape(tempPreview);
                    } else {
                        context.setPreviewShape(null);
                    }
                } else {
                    context.setPreviewShape(null);
                }
            } catch (Exception e) {
                LOGGER.error("CatenaryLineTool updatePreview 失败: {}", e.getMessage(), e);
                context.setPreviewShape(null);
            }
        }

        /**
         * [修正] 根据当前模式创建最终的悬链线对象
         */
        private CableShape createFinalCatenary(Vec2d start, Vec2d end, Vec2d sagPoint) {
            try {
                CableShape catenary = new CableShape(start, end, sagParameter, segments);

                // 根据当前模式设置悬链线的属性
                if (MODE_STANDARD.equals(currentMode)) {
                    // 标准模式：计算并设置弧垂深度和方向
                    catenary.setDrawMode(MODE_STANDARD);
                    double sagDepth = calculatePerpendicularSag(start, end, sagPoint);
                    catenary.setSagDepth(Math.abs(sagDepth));
                    
                    // 根据弧垂点的位置确定弧垂方向
                    double sagDirection = sagDepth >= 0 ? 1.0 : -1.0;
                    catenary.setSagDirection(sagDirection);
                } else { // MODE_UNEVEN (样条插值模式)
                    // 样条插值模式：直接设置贝塞尔曲线的控制点
                    // setSagPoint 会自动将 drawMode 设置为 MODE_UNEVEN
                    catenary.setSagPoint(sagPoint);
                }

                return catenary;
            } catch (Exception e) {
                LOGGER.error("CatenaryLineTool 创建悬链线失败: {}", e.getMessage(), e);
                return null;
            }
        }

        /**
         * 计算标准模式下垂直于连线的弧垂深度
         */
        private double calculatePerpendicularSag(Vec2d start, Vec2d end, Vec2d sagPoint) {
            Vec2d lineVec = end.subtract(start);
            double lineLength = lineVec.length();
            if (lineLength < 0.0001) return 0.0;

            Vec2d unitLineVec = lineVec.multiply(1.0 / lineLength);
            Vec2d toSag = sagPoint.subtract(start);
            double projDist = toSag.dot(unitLineVec);
            Vec2d projPoint = start.add(unitLineVec.multiply(projDist));
            double perpendicularDist = sagPoint.distance(projPoint);
            double crossProduct = lineVec.x * toSag.y - lineVec.y * toSag.x;

            return (crossProduct < 0) ? -perpendicularDist : perpendicularDist;
        }

        public void renderImGuiPreview(ImDrawList drawList, CanvasCamera camera) {
            if (!isActive || camera == null) return;
            try {
                // 渲染已确定的控制点
                controlPoints.forEach(p -> {
                    Vec2d screenPoint = camera.worldToScreen(p);
                    drawList.addCircleFilled((float)screenPoint.x, (float)screenPoint.y, CONTROL_POINT_SIZE, ImColor.rgba(CONTROL_POINT_COLOR));
                });

                if (currentMousePoint != null) {
                    if (controlPoints.size() == 1) {
                        // 渲染起点到鼠标的连接线
                        Vec2d screenStart = camera.worldToScreen(controlPoints.getFirst());
                        Vec2d screenMouse = camera.worldToScreen(currentMousePoint);
                        drawList.addLine((float)screenStart.x, (float)screenStart.y, (float)screenMouse.x, (float)screenMouse.y, ImColor.rgba(CONNECTOR_LINE_COLOR), PREVIEW_LINE_WIDTH);
                    } else if (controlPoints.size() == 2) {
                        // 渲染第三个（弧垂/控制）点
                        Vec2d screenSag = camera.worldToScreen(currentMousePoint);
                        drawList.addCircleFilled((float)screenSag.x, (float)screenSag.y, SAG_POINT_SIZE, ImColor.rgba(SAG_POINT_COLOR));
                    }
                }
            } catch (Exception e) {
                LOGGER.error("CatenaryLineTool renderImGuiPreview 渲染失败: {}", e.getMessage(), e);
            }
        }

        public void onModeChanged() {
            // 模式或参数改变时，如果正在交互，只需让 updatePreview 在下次鼠标移动时重新计算即可
            LOGGER.debug("CatenaryLineTool 模式或参数已更改，预览将自动更新");
        }

        public void onParameterChanged() {
            onModeChanged();
        }

        @Override
        public Shape getFinalShape() {
            if (previewCatenary != null) {
                // 使用 clone() 创建新实例，确保返回的图形独立于内部状态（参考 CircleTool 的实现）
                // 这样即使 reset() 清空了 previewCatenary，已返回的图形也不会受影响
                CableShape finalCatenary = (CableShape) previewCatenary.clone();
                
                // 应用最终样式（确保样式正确）
                // 注意：clone() 已经复制了样式，但为了确保使用最新的最终样式，我们重新应用
                // 由于 getFinalShape() 没有 context 参数，样式已经在 finishInteraction 中应用过了
                // 所以这里直接使用 clone 的样式即可
                
                LOGGER.info("CatenaryLineTool.getFinalShape: 创建新图形实例 ID={}, 类型={}, 起点={}, 终点={}", 
                    finalCatenary.getId(), finalCatenary.getClass().getSimpleName(),
                    finalCatenary.getStart(), finalCatenary.getEnd());
                return finalCatenary;
            } else {
                LOGGER.error("CatenaryLineTool.getFinalShape: previewCatenary 为 null！这会导致图形无法提交");
                return null;
            }
        }

        @Override
        public void reset() {
            isActive = false;
            controlPoints.clear();
            currentMousePoint = null;
            previewCatenary = null;
            LOGGER.debug("CatenaryLineTool 状态已重置");
        }

        @Override
        public String getStrategyName() {
            return "CatenaryInteractionStrategy";
        }

        @Override
        public String getStrategyDescription() {
            return "悬链线工具三点绘制交互策略";
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