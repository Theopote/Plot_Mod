package com.plot.ui.tools.impl.drawing;

import com.plot.api.geometry.Vec2d;
import com.plot.api.state.IAppState;
import com.plot.api.snap.ISnapManager;
import com.plot.core.geometry.shapes.CableShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.graphics.DrawContext;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.model.Shape;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.component.Icons;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import imgui.ImDrawList;
import com.plot.ui.tools.snap.SnapEnhancer;

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
    private final com.plot.ui.tools.snap.SnapEnhancer snapEnhancer =
            new com.plot.ui.tools.snap.SnapEnhancer("CatenaryLineTool");
    private static final Logger LOGGER = LoggerFactory.getLogger(CatenaryLineTool.class);

    // 配置键常量
    public static final String CONFIG_KEY_MODE = "mode";
    public static final String CONFIG_KEY_SAG = "sag";
    public static final String CONFIG_KEY_SEGMENTS = "segments";

    // 绘制模式常量
    public static final String MODE_STANDARD = "standard";  // 标准悬链线（对称）
    public static final String MODE_UNEVEN = "uneven";      // 样条插值悬链线

    // 默认参数
    private static final int DEFAULT_SEGMENTS = 20;

    // 渲染常量
    private static final float CONTROL_POINT_SIZE = 6.0f;
    private static final float SAG_POINT_SIZE = 7.0f;
    private static final float PREVIEW_LINE_WIDTH = 2.0f;

    // 配置参数
    private String currentMode = MODE_STANDARD;
    // 固定悬垂参数与分段数（不从 UI 修改）
    private final double sagParameter = 1.0; // 固定为1
    private final int segments = DEFAULT_SEGMENTS; // 固定为默认分段数

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
        // 明确设置分段数（构造器已设置，但显式赋值可避免歧义）
        catenary.setSegments(segments);

        // 设置合理的默认弧垂，并让 UI 的 sagParameter 按语义影响默认弧垂
        double distance = startPoint.distance(endPoint);
        double defaultSag = distance * 0.15; // 使用15%的距离作为默认弧垂基准
        double effectiveDefaultSag = defaultSag * (1.0 / Math.max(0.0001, sagParameter));
        catenary.setSagDepth(effectiveDefaultSag);

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
    protected com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy createStrategy(InteractionType type) {
        return createCatenaryStrategy();
    }

    @Override
    protected void renderPreview(DrawContext context) {
        // 始终渲染捕捉指示器（即使尚未开始绘制）
        snapEnhancer.renderSnapIndicator(context);

        if (!shouldShowPreview() || previewShape == null) return;
        if (previewShape instanceof CableShape catenary) {
            ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
            Color lineColor = previewStyle != null ? previewStyle.getLineColor() :
                    toColor(ThemeManager.getInstance().getCurrentTheme().accent, 255);

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
                context.drawDashedLine(pts.get(0), pts.get(1), toColor(ThemeManager.getInstance().getCurrentTheme().warningText, 160));
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
                case CONFIG_KEY_SAG -> // 已固定悬垂参数，忽略来自 UI 的修改
                        LOGGER.debug("CatenaryLineTool: 忽略来自 UI 的悬垂参数修改 (固定值)");
                case CONFIG_KEY_SEGMENTS -> // 已固定分段数，忽略来自 UI 的修改
                        LOGGER.debug("CatenaryLineTool: 忽略来自 UI 的分段数修改 (固定值)");
                default -> LOGGER.debug("CatenaryLineTool: 未知配置键: {}", key);
            }
        } catch (Exception e) {
            LOGGER.error("CatenaryLineTool 更新配置失败: key={}, value={}", key, value, e);
        }
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
                // 对于第一、第二点使用吸附；第三点（弧垂）不吸附以保留侧向信息
                Vec2d snappedPoint;
                if (!isActive) {
                    SnapEnhancer.SnapResult snapResult = snapEnhancer.performEnhancedSnap(pos, context);
                    snappedPoint = snapResult.point;
                } else if (controlPoints.size() == 1) {
                    SnapEnhancer.SnapResult snapResult = snapEnhancer.performEnhancedSnap(pos, context);
                    snappedPoint = snapResult.point;
                    snappedPoint = constrainSecondPointToAxis(controlPoints.getFirst(), snappedPoint);
                } else {
                    // 第三点：使用原始屏幕坐标转换为世界坐标，避免吸附覆盖侧向选择
                    try {
                        CanvasCamera cam = context.getCamera();
                        snappedPoint = cam != null ? cam.screenToWorld(pos) : pos;
                    } catch (Exception e) {
                        snappedPoint = pos;
                    }
                }
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
                    // 完成交互：创建最终图形并保存（第三点为非吸附点）
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
                // 鼠标移动：第一/第二点使用吸附以显示指示器，第三点（预览弧垂）使用原始世界点以保留侧向信息
                if (!isActive) {
                    SnapEnhancer.SnapResult snapResult = snapEnhancer.performEnhancedSnap(pos, context);
                    currentMousePoint = snapResult.point;
                } else if (controlPoints.size() == 1) {
                    SnapEnhancer.SnapResult snapResult = snapEnhancer.performEnhancedSnap(pos, context);
                    currentMousePoint = snapResult.point;
                    currentMousePoint = constrainSecondPointToAxis(controlPoints.getFirst(), currentMousePoint);
                } else {
                    try {
                        CanvasCamera cam = context.getCamera();
                        currentMousePoint = cam != null ? cam.screenToWorld(pos) : pos;
                    } catch (Exception e) {
                        currentMousePoint = pos;
                    }
                }

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
                CableShape finalCatenary = createFinalCatenary(startPoint, endPoint, sagPoint, context);

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
                    List<Vec2d> linePoints = List.of(controlPoints.getFirst(), currentMousePoint);
                    com.plot.core.geometry.shapes.PolylineShape previewLine =
                            new com.plot.core.geometry.shapes.PolylineShape(linePoints, false);
                    context.getStyleHandler().applyPreviewStyle(previewLine);
                    context.setPreviewShape(previewLine);
                } else if (controlPoints.size() == 2) {
                    // 第三步：显示带控制点的悬链线预览
                    CableShape tempPreview = createFinalCatenary(controlPoints.get(0), controlPoints.get(1), currentMousePoint, context);
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
        private CableShape createFinalCatenary(Vec2d start, Vec2d end, Vec2d sagPoint, DrawingToolContext context) {
            try {
                CableShape catenary = new CableShape(start, end, sagParameter, segments);
                // 确保分段数与工具配置一致
                catenary.setSegments(segments);

                // 根据当前模式设置悬链线的属性
                if (MODE_STANDARD.equals(currentMode)) {
                    // 标准模式：计算并设置弧垂深度和方向
                    catenary.setDrawMode(MODE_STANDARD);
                    // 先计算世界空间的垂直距离作为深度参考
                    double sagDepthWorld = calculatePerpendicularSag(start, end, sagPoint);
                    // 让 sagParameter 按 UI 语义影响最终弧垂：sagParameter 越小，下垂越明显
                    double effectiveSagDepth = Math.abs(sagDepthWorld) * (1.0 / Math.max(0.0001, sagParameter));
                    catenary.setSagDepth(effectiveSagDepth);

                    // 优先在屏幕空间判定侧向（更符合用户视觉交互）
                    double sagSign = 1.0;
                    try {
                        if (context != null && context.getCamera() != null) {
                            CanvasCamera cam = context.getCamera();
                            Vec2d sScreen = cam.worldToScreen(start);
                            Vec2d eScreen = cam.worldToScreen(end);
                            Vec2d sagScreen = cam.worldToScreen(sagPoint);

                            Vec2d lineVec = eScreen.subtract(sScreen);
                            double len = lineVec.length();
                            if (len > 1e-6) {
                                Vec2d unitLine = lineVec.multiply(1.0 / len);
                                Vec2d unitNormal = new Vec2d(-unitLine.y, unitLine.x);
                                Vec2d toSag = sagScreen.subtract(sScreen);
                                double proj = toSag.dot(unitLine);
                                Vec2d projPoint = sScreen.add(unitLine.multiply(proj));
                                Vec2d offset = sagScreen.subtract(projPoint);
                                double signedPerpScreen = offset.dot(unitNormal);
                                sagSign = signedPerpScreen >= 0 ? 1.0 : -1.0;
                            }
                        } else {
                            // 回退到世界空间符号判定
                            sagSign = sagDepthWorld >= 0 ? 1.0 : -1.0;
                        }
                    } catch (Exception e) {
                        sagSign = sagDepthWorld >= 0 ? 1.0 : -1.0;
                    }

                    catenary.setSagDirection(sagSign);
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
            // 使用法向量与 sag 点到投影点向量的点积来获取带符号的垂直距离。
            // 这种方式不依赖于外部坐标系的 y 方向约定，比直接使用叉积对屏幕/世界坐标系更稳健。
            Vec2d unitNormal = new Vec2d(-unitLineVec.y, unitLineVec.x);
            Vec2d offsetVec = sagPoint.subtract(projPoint);
            return offsetVec.dot(unitNormal);
        }

        public void renderImGuiPreview(ImDrawList drawList, CanvasCamera camera) {
            if (!isActive || camera == null) return;
            try {
                var theme = ThemeManager.getInstance().getCurrentTheme();
                int controlPointColor = theme.infoText;
                int connectorLineColor = withAlpha(theme.warningText, 160);
                int sagPointColor = theme.errorText;

                // 渲染已确定的控制点
                controlPoints.forEach(p -> {
                    Vec2d screenPoint = camera.worldToScreen(p);
                    drawList.addCircleFilled((float)screenPoint.x, (float)screenPoint.y, CONTROL_POINT_SIZE, controlPointColor);
                });

                if (currentMousePoint != null) {
                    if (controlPoints.size() == 1) {
                        // 渲染起点到鼠标的连接线
                        Vec2d screenStart = camera.worldToScreen(controlPoints.getFirst());
                        Vec2d screenMouse = camera.worldToScreen(currentMousePoint);
                        drawList.addLine((float)screenStart.x, (float)screenStart.y, (float)screenMouse.x, (float)screenMouse.y, connectorLineColor, PREVIEW_LINE_WIDTH);
                    } else if (controlPoints.size() == 2) {
                        // 渲染第三个（弧垂/控制）点
                        Vec2d screenSag = camera.worldToScreen(currentMousePoint);
                        drawList.addCircleFilled((float)screenSag.x, (float)screenSag.y, SAG_POINT_SIZE, sagPointColor);
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

    private static Color toColor(int color, int alpha) {
        return new Color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, alpha);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
