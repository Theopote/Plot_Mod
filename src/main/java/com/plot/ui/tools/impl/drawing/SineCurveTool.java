package com.plot.ui.tools.impl.drawing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.SineCurveShape;
import com.plot.core.graphics.DrawContext;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.model.Shape;
import com.plot.api.state.IAppState;
import com.plot.api.snap.ISnapManager;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import com.plot.ui.component.Icons;
import com.plot.ui.theme.ThemeManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import imgui.ImDrawList;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.tools.snap.SnapEnhancer;

import java.util.List;
import java.util.ArrayList;
import com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy;
import com.plot.utils.PlotI18n;

/**
 * 正弦曲线工具 - 策略模式版本
 * 
 * <p>支持三点绘制正弦波形：</p>
 * <ul>
 *   <li>第一点：起点</li>
 *   <li>第二点：终点（确定基线长度和方向）</li>
 *   <li>第三点：振幅点（确定波形振幅和方向）</li>
 * </ul>
 * 
 * <p>功能特色：</p>
 * <ul>
 *   <li>完整的策略模式集成</li>
 *   <li>实时预览和吸附支持</li>
 *   <li>可配置的波长、振幅和相位</li>
 *   <li>详细的几何计算和参数显示</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 2.0 - 策略模式版本
 */
public class SineCurveTool extends DrawingTool {
    // 统一捕捉可视化
    private final com.plot.ui.tools.snap.SnapEnhancer snapEnhancer =
            new com.plot.ui.tools.snap.SnapEnhancer("SineCurveTool");
    private static final Logger LOGGER = LoggerFactory.getLogger(SineCurveTool.class);
    
    // ====== 配置常量 ======
    
    public static final String CONFIG_KEY_PHASE = "phase";
    
    // ====== 数值常量 ======
    
    private static final float DEFAULT_WAVELENGTH = 100.0f;
    private static final float DEFAULT_AMPLITUDE = 50.0f;
    private static final float DEFAULT_PHASE = 0.0f;
    private static final int DEFAULT_SEGMENTS = 100;
    
    // ====== 渲染常量 ======

    private static final float LINE_THICKNESS = 2.0f;
    private static final float POINT_SIZE = 5.0f;
    private static final float CURVE_THICKNESS = 3.0f;
    
    // ====== 状态字段 ======

    private float amplitude = DEFAULT_AMPLITUDE;
    private float phase = DEFAULT_PHASE;

    // 绘制状态
    private final List<Vec2d> controlPoints = new ArrayList<>();
    private Vec2d currentMousePoint;
    private int currentStep = 0;
    private SineCurveShape previewCurve;
    
    // 状态消息映射
    private static final List<String> STATUS_MESSAGES = List.of(
        "status.plot.draw.sine.start",
        "status.plot.draw.sine.wavelength",
        "status.plot.draw.sine.length",
        "status.plot.draw.sine.amplitude"
    );
    
    // ====== 构造函数 ======
    
    /**
     * 依赖注入构造函数（推荐方式）
     */
    public SineCurveTool(IAppState appState, ISnapManager snapManager) {
        super("sine", Icons.SINE_IDENTIFIER, appState, snapManager, InteractionType.CLICK_AND_CLICK);
        
        initializeSineCurveTool();
    }
    
    /**
     * 兼容性构造函数
     * @deprecated 推荐使用依赖注入构造函数
     */
    @Deprecated
    public SineCurveTool(AppState appState) {
        super("sine", Icons.SINE_IDENTIFIER);
        
        initializeSineCurveTool();
    }
    
    // ====== 初始化方法 ======
    
    /**
     * 初始化正弦曲线工具
     */
    private void initializeSineCurveTool() {
        // 订阅配置事件
        eventBus.subscribe(ToolConfigEvent.class, event -> {
            if (event instanceof ToolConfigEvent toolConfigEvent && 
                getId().equals(toolConfigEvent.getToolId())) {
                updateConfig(toolConfigEvent.getOptionName(), String.valueOf(toolConfigEvent.getValue()));
            }
        });
        
        // 更新状态消息
        updateStatusMessage();

        LOGGER.debug("SineCurveTool 初始化完成，波长: {}, 振幅: {}, 分段数: {}", DEFAULT_WAVELENGTH, amplitude, DEFAULT_SEGMENTS);
    }
    
    // ====== 状态管理方法 ======
    
    /**
     * 更新状态消息
     */
    private void updateStatusMessage() {
        int index = Math.min(controlPoints.size(), STATUS_MESSAGES.size() - 1);
        String message = STATUS_MESSAGES.get(index);
        setStatusMessage(message);
        LOGGER.debug("正弦曲线工具状态消息已更新: {}", message);
    }
    
    /**
     * 重置绘制状态
     */
    private void resetDrawingState() {
        controlPoints.clear();
        currentMousePoint = null;
        currentStep = 0;
        previewCurve = null;
        updateStatusMessage();
        LOGGER.debug("正弦曲线工具状态已重置");
    }
    
    // ====== 基类重写方法 ======
    
    @Override
    public void onActivate() {
        LOGGER.debug("SineCurveTool activated");
        resetDrawingState();
        updateStatusMessage();
    }
    
    @Override
    public void onDeactivate() {
        LOGGER.debug("SineCurveTool deactivated");
        resetDrawingState();
        setStatusMessage("");
    }
    
    @Override
    protected Shape createShape(Vec2d startPoint, Vec2d endPoint) {
        // 策略模式下，图形创建在策略中完成
        // 这里提供向后兼容的实现
        if (controlPoints.size() >= 4) {
            Vec2d curveStartPoint = controlPoints.get(0);
            Vec2d wavelengthPoint = controlPoints.get(1);
            Vec2d lengthPoint = controlPoints.get(2);
            Vec2d amplitudePoint = controlPoints.get(3);
            
            // 计算波长（第一点到第二点的距离）
            double wavelength = curveStartPoint.distance(wavelengthPoint);
            
            // 计算整体长度（从起点到第三点的总长度）
            /* double totalLength = curveStartPoint.distance(lengthPoint); */
            
            // 计算振幅
            double actualAmplitude = calculateActualAmplitude(curveStartPoint, lengthPoint, amplitudePoint);
            
            // 创建正弦曲线，使用计算出的波长和总长度
            SineCurveShape curve = new SineCurveShape(
                curveStartPoint, lengthPoint, actualAmplitude, wavelength, phase);
            
            ShapeStyle style = getStyleHandler().getFinalStyle();
            if (style != null) {
                curve.setStyle(style);
            }
            
            return curve;
        }
        
        return null;
    }
    
    // ====== 配置管理 ======
    
    /**
     * 更新工具配置
     */
    @Override
    public void updateConfig(String key, String value) {
        if (key.equals(CONFIG_KEY_PHASE)) {
            try {
                // 将接收到的角度值转换为弧度
                phase = (float) Math.toRadians(Float.parseFloat(value));
                updatePreview();
                LOGGER.debug("正弦曲线工具相位更新为: {} (弧度)", phase);
            } catch (NumberFormatException e) {
                LOGGER.warn("无效的相位值: {}", value);
            }
        } else {
            LOGGER.debug("未知的配置键: {}", key);
        }
    }
    
    // ====== 预览更新方法 ======
    
    /**
     * 更新预览
     */
    private void updatePreview() {
        if (controlPoints.size() >= 2 && currentMousePoint != null) {
            Vec2d startPoint = controlPoints.get(0);

            Vec2d wavelengthPoint = controlPoints.get(1);
            double wavelength = startPoint.distance(wavelengthPoint);

            if (controlPoints.size() >= 3) {
                Vec2d lengthPoint = controlPoints.get(2);
                Vec2d amplitudePoint = controlPoints.size() >= 4 ? controlPoints.get(3) : currentMousePoint;

                double actualAmplitude = calculateActualAmplitude(startPoint, lengthPoint, amplitudePoint);

                previewCurve = new SineCurveShape(startPoint, lengthPoint, actualAmplitude, wavelength, phase);
            } else {
                // 只有两个点时，使用鼠标位置作为长度点
                Vec2d lengthPoint = currentMousePoint;
                Vec2d amplitudePoint = currentMousePoint;

                double actualAmplitude = calculateActualAmplitude(startPoint, lengthPoint, amplitudePoint);

                previewCurve = new SineCurveShape(startPoint, lengthPoint, actualAmplitude, wavelength, phase);
            }

            // 使用当前图层颜色的预览样式
            if (getStyleHandler() != null) {
                ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
                if (previewStyle != null) {
                    previewCurve.setStyle(previewStyle);
                    LOGGER.debug("SineCurveTool: 设置预览样式，颜色: {}", previewStyle.getLineColor());
                } else {
                    LOGGER.warn("SineCurveTool: 获取预览样式失败，使用默认样式");
                }
            } else {
                LOGGER.warn("SineCurveTool: StyleHandler为null");
            }
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
     * 计算实际振幅
     */
    private double calculateActualAmplitude(Vec2d startPoint, Vec2d endPoint, Vec2d amplitudePoint) {
        if (startPoint == null || endPoint == null || amplitudePoint == null) {
            return amplitude;
        }
        
        // 计算基线方向的法向量
        Vec2d direction = endPoint.subtract(startPoint);
        Vec2d normal = new Vec2d(-direction.y, direction.x).normalize();
        
        // 计算基线中点
        Vec2d midPoint = startPoint.add(endPoint).divide(2);
        
        // 计算振幅点到基线的距离
        Vec2d toAmplitudePoint = amplitudePoint.subtract(midPoint);
        return Math.abs(toAmplitudePoint.dot(normal));
    }
    
    // ====== 预览渲染方法 ======
    
    @Override
    protected void renderPreview(DrawContext context) {
        // 始终渲染捕捉指示器（即使尚未开始绘制）
        snapEnhancer.renderSnapIndicator(context);

        if (!shouldShowPreview()) {
            return;
        }
        
        renderControlPoints(context);
        renderBaseline(context);
        renderAmplitudeLine(context);
        renderCurvePreview(context);
    }
    
    /**
     * 渲染控制点
     */
    private void renderControlPoints(DrawContext context) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        java.awt.Color startPointColor = toColor(theme.infoText, 255);
        java.awt.Color wavelengthPointColor = toColor(theme.successText, 255);
        java.awt.Color lengthPointColor = toColor(theme.warningText, 255);
        java.awt.Color previewLengthPointColor = toColor(theme.warningText, 220);
        java.awt.Color amplitudePointColor = toColor(theme.errorText, 255);
        java.awt.Color previewAmplitudePointColor = toColor(theme.accent, 255);

        // 起点
        if (!controlPoints.isEmpty()) {
            context.drawCircle(controlPoints.getFirst(), 3.0, startPointColor);
        }
        
        // 波长点
        if (controlPoints.size() >= 2) {
            context.drawCircle(controlPoints.get(1), 3.0, wavelengthPointColor);
        }
        
        // 长度点
        if (controlPoints.size() >= 3) {
            context.drawCircle(controlPoints.get(2), 3.0, lengthPointColor);
        } else if (currentMousePoint != null && controlPoints.size() == 2) {
            context.drawCircle(currentMousePoint, 3.0, previewLengthPointColor);
        }
        
        // 振幅点
        if (controlPoints.size() >= 4) {
            context.drawCircle(controlPoints.get(3), 3.0, amplitudePointColor);
        } else if (currentMousePoint != null && controlPoints.size() == 3) {
            context.drawCircle(currentMousePoint, 3.0, previewAmplitudePointColor);
        }
    }
    
    /**
     * 渲染基线
     */
    private void renderBaseline(DrawContext context) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        java.awt.Color wavelengthLineColor = toColor(theme.successText, 255);
        java.awt.Color lengthLineColor = toColor(theme.warningText, 255);
        java.awt.Color previewLengthLineColor = toColor(theme.warningText, 220);
        java.awt.Color seedLineColor = toColor(theme.mutedText, 220);

        if (controlPoints.size() >= 2) {
            // 绘制波长线（起点到波长点）
            context.drawLine(controlPoints.get(0), controlPoints.get(1), wavelengthLineColor);
            
            if (controlPoints.size() >= 3) {
                // 绘制长度线（起点到长度点）
                context.drawLine(controlPoints.get(0), controlPoints.get(2), lengthLineColor);
            } else if (currentMousePoint != null) {
                // 预览长度线
                context.drawLine(controlPoints.getFirst(), currentMousePoint, previewLengthLineColor);
            }
        } else if (controlPoints.size() == 1 && currentMousePoint != null) {
            context.drawLine(controlPoints.getFirst(), currentMousePoint, seedLineColor);
        }
    }
    
    /**
     * 渲染振幅线
     */
    private void renderAmplitudeLine(DrawContext context) {
        java.awt.Color amplitudeLineColor = toColor(ThemeManager.getInstance().getCurrentTheme().accent, 255);
        if (controlPoints.size() >= 3) {
            Vec2d startPoint = controlPoints.get(0);
            Vec2d lengthPoint = controlPoints.get(2);
            Vec2d midPoint = startPoint.add(lengthPoint).divide(2);
            
            Vec2d amplitudePoint = controlPoints.size() >= 4 ? controlPoints.get(3) : currentMousePoint;
            if (amplitudePoint != null) {
                context.drawLine(midPoint, amplitudePoint, amplitudeLineColor);
            }
        }
    }
    
    /**
     * 渲染曲线预览
     */
    private void renderCurvePreview(DrawContext context) {
        if (previewCurve != null) {
            // 确保预览曲线使用当前图层颜色
            if (getStyleHandler() != null) {
                com.plot.core.graphics.style.ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
                if (previewStyle != null) {
                    // 在DrawContext中设置预览样式，这样SineCurveShape.draw()会使用正确的颜色
                    context.setStyle(previewStyle);
                    LOGGER.trace("SineCurveTool: 在DrawContext中设置预览样式，颜色: {}", previewStyle.getLineColor());
                }
            }
            previewCurve.draw(context);
        }
    }
    
    @Override
    protected boolean shouldShowPreview() {
        // 正弦曲线工具的预览基于控制点，而不是previewShape
        return !controlPoints.isEmpty() || currentMousePoint != null;
    }
    
    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        // 始终渲染捕捉指示器（即使尚未开始绘制）
        snapEnhancer.renderSnapIndicator(drawList, camera);

        renderControlPointsImGui(drawList, camera);
        renderBaselineImGui(drawList, camera);
        renderAmplitudeLineImGui(drawList, camera);
        renderCurvePreviewImGui(drawList, camera);
        renderParameterInfoImGui(drawList, camera);
    }
    
    /**
     * 渲染控制点（ImGui版本）
     */
    private void renderControlPointsImGui(ImDrawList drawList, CanvasCamera camera) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        // 起点
        if (!controlPoints.isEmpty()) {
            Vec2d screenPoint = camera.worldToScreen(controlPoints.getFirst());
            drawList.addCircleFilled(
                (float) screenPoint.x, (float) screenPoint.y,
                POINT_SIZE, theme.infoText
            );
        }
        
        // 波长点
        if (controlPoints.size() >= 2) {
            Vec2d screenPoint = camera.worldToScreen(controlPoints.get(1));
            drawList.addCircleFilled(
                (float) screenPoint.x, (float) screenPoint.y,
                POINT_SIZE, theme.successText
            );
        }
        
        // 长度点
        if (controlPoints.size() >= 3) {
            Vec2d screenPoint = camera.worldToScreen(controlPoints.get(2));
            drawList.addCircleFilled(
                (float) screenPoint.x, (float) screenPoint.y,
                POINT_SIZE, theme.warningText
            );
        } else if (currentMousePoint != null && controlPoints.size() == 2) {
            Vec2d screenPoint = camera.worldToScreen(currentMousePoint);
            drawList.addCircleFilled(
                (float) screenPoint.x, (float) screenPoint.y,
                POINT_SIZE, theme.warningText
            );
        }
        
        // 振幅点
        if (controlPoints.size() >= 4) {
            Vec2d screenPoint = camera.worldToScreen(controlPoints.get(3));
            drawList.addCircleFilled(
                (float) screenPoint.x, (float) screenPoint.y,
                POINT_SIZE, theme.errorText
            );
        } else if (currentMousePoint != null && controlPoints.size() == 3) {
            Vec2d screenPoint = camera.worldToScreen(currentMousePoint);
            drawList.addCircleFilled(
                (float) screenPoint.x, (float) screenPoint.y,
                POINT_SIZE, theme.accent
            );
        }
    }
    
    /**
     * 渲染基线（ImGui版本）
     */
    private void renderBaselineImGui(ImDrawList drawList, CanvasCamera camera) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        if (controlPoints.size() >= 2) {
            // 绘制波长线（起点到波长点）
            Vec2d screenStart = camera.worldToScreen(controlPoints.get(0));
            Vec2d screenWavelength = camera.worldToScreen(controlPoints.get(1));
            drawList.addLine(
                (float) screenStart.x, (float) screenStart.y,
                (float) screenWavelength.x, (float) screenWavelength.y,
                theme.successText, LINE_THICKNESS
            );
            
            if (controlPoints.size() >= 3) {
                // 绘制长度线（起点到长度点）
                Vec2d screenLength = camera.worldToScreen(controlPoints.get(2));
                drawList.addLine(
                    (float) screenStart.x, (float) screenStart.y,
                    (float) screenLength.x, (float) screenLength.y,
                    theme.warningText, LINE_THICKNESS
                );
            } else if (currentMousePoint != null) {
                // 预览长度线
                Vec2d screenEnd = camera.worldToScreen(currentMousePoint);
                drawList.addLine(
                    (float) screenStart.x, (float) screenStart.y,
                    (float) screenEnd.x, (float) screenEnd.y,
                    theme.warningText, LINE_THICKNESS
                );
            }
        } else if (controlPoints.size() == 1 && currentMousePoint != null) {
            Vec2d screenStart = camera.worldToScreen(controlPoints.getFirst());
            Vec2d screenEnd = camera.worldToScreen(currentMousePoint);
            drawList.addLine(
                (float) screenStart.x, (float) screenStart.y,
                (float) screenEnd.x, (float) screenEnd.y,
                withAlpha(theme.mutedText, 0xCC), LINE_THICKNESS
            );
        }
    }
    
    /**
     * 渲染振幅线（ImGui版本）
     */
    private void renderAmplitudeLineImGui(ImDrawList drawList, CanvasCamera camera) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        if (controlPoints.size() >= 3) {
            Vec2d startPoint = controlPoints.get(0);
            Vec2d lengthPoint = controlPoints.get(2);
            Vec2d midPoint = startPoint.add(lengthPoint).divide(2);
            Vec2d screenMid = camera.worldToScreen(midPoint);
            
            Vec2d amplitudePoint = controlPoints.size() >= 4 ? controlPoints.get(3) : currentMousePoint;
            if (amplitudePoint != null) {
                Vec2d screenAmplitude = camera.worldToScreen(amplitudePoint);
                drawList.addLine(
                    (float) screenMid.x, (float) screenMid.y,
                    (float) screenAmplitude.x, (float) screenAmplitude.y,
                    theme.accent, LINE_THICKNESS
                );
            }
        }
    }
    
    /**
     * 渲染曲线预览（ImGui版本）
     */
    private void renderCurvePreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        if (previewCurve != null) {
            List<Vec2d> points = previewCurve.getPoints();
            if (points.size() >= 2) {
                // 获取当前图层颜色用于预览
                int previewColor = withAlpha(ThemeManager.getInstance().getCurrentTheme().accent, 0x80);
                
                if (getStyleHandler() != null) {
                    com.plot.core.graphics.style.ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
                    if (previewStyle != null && previewStyle.getLineColor() != null) {
                        java.awt.Color layerColor = previewStyle.getLineColor();
                        // 转换为ImGui颜色格式 (ARGB)
                        previewColor = (layerColor.getAlpha() << 24) |
                                     (layerColor.getRed() << 16) |
                                     (layerColor.getGreen() << 8) |
                                     layerColor.getBlue();
                        LOGGER.trace("SineCurveTool: ImGui预览使用图层颜色: {} -> 0x{}", 
                                   layerColor, Integer.toHexString(previewColor));
                    }
                }
                
                for (int i = 1; i < points.size(); i++) {
                    Vec2d screenPrev = camera.worldToScreen(points.get(i - 1));
                    Vec2d screenCurr = camera.worldToScreen(points.get(i));
                    
                    drawList.addLine(
                        (float) screenPrev.x, (float) screenPrev.y,
                        (float) screenCurr.x, (float) screenCurr.y,
                        previewColor, CURVE_THICKNESS
                    );
                }
            }
        }
    }
    
    /**
     * 渲染参数信息（ImGui版本）
     */
    private void renderParameterInfoImGui(ImDrawList drawList, CanvasCamera camera) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        if (controlPoints.size() >= 2 && currentMousePoint != null) {
            Vec2d displayPoint = currentMousePoint;
            Vec2d screenPoint = camera.worldToScreen(displayPoint);
            
            String info = "";
            if (controlPoints.size() >= 2) {
                double wavelength = controlPoints.get(0).distance(controlPoints.get(1));
                info += PlotI18n.status("status.plot.draw.sine.wavelength", wavelength);
            }
            
            if (controlPoints.size() >= 3) {
                double totalLength = controlPoints.get(0).distance(controlPoints.get(2));
                info += PlotI18n.status("status.plot.draw.sine.length", totalLength);
            }
            
            if (controlPoints.size() >= 3) {
                Vec2d lengthPoint = controlPoints.size() >= 4 ? controlPoints.get(2) : currentMousePoint;
                double actualAmplitude = calculateActualAmplitude(
                    controlPoints.get(0), lengthPoint, displayPoint);
                info += PlotI18n.status("status.plot.draw.sine.amplitude", actualAmplitude);
            }
            
            info += PlotI18n.status("status.plot.draw.sine.phase", Math.toDegrees(phase));
            
            drawList.addText(
                (float) screenPoint.x + 15, (float) screenPoint.y - 20,
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
    
    // ====== 自定义交互策略 ======
    
    /**
     * 正弦曲线工具专用交互策略
     * 支持三点点击交互
     */
    private class SineCurveInteractionStrategy implements com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy {
        
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
            // 使用增强吸附，确保捕捉类型与可视化一致
            SnapEnhancer.SnapResult snapResult = snapEnhancer.performEnhancedSnap(pos, context);
            Vec2d worldPoint = snapResult.point;
            if (controlPoints.size() == 1) {
                worldPoint = constrainSecondPointToAxis(controlPoints.getFirst(), worldPoint);
            }
            LOGGER.debug("SineCurveTool.onMouseDown: 点击位置={}, 转换后={}", pos, worldPoint);
            
            // 添加控制点
            controlPoints.add(worldPoint);
            currentStep++;
            
            // 检查是否完成绘制（需要4个点）
            if (currentStep >= 4) {
                return InteractionResult.COMPLETE;
            } else {
                updateStatusMessage();
                return InteractionResult.CONTINUE;
            }
        }
        
        @Override
        public InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context) {
            // 始终进行增强吸附：在开始绘制之前也更新捕捉状态以显示指示器
            SnapEnhancer.SnapResult snapResult = snapEnhancer.performEnhancedSnap(pos, context);
            currentMousePoint = snapResult.point;
            if (controlPoints.size() == 1) {
                currentMousePoint = constrainSecondPointToAxis(controlPoints.getFirst(), currentMousePoint);
            }

            // 如果还没有控制点，仅显示捕捉效果，不更新曲线预览
            if (controlPoints.isEmpty()) {
                return InteractionResult.CONTINUE; // 触发重绘以显示吸附指示器
            }

            updatePreview();
            return InteractionResult.CONTINUE;
        }
        
        @Override
        public InteractionResult onMouseUp(Vec2d pos, int button, DrawingToolContext context) {
            // 正弦曲线工具使用点击模式，不需要处理鼠标释放
            return InteractionResult.IGNORED;
        }
        
        @Override
        public com.plot.core.model.Shape getFinalShape() {
            if (controlPoints.size() >= 4) {
                Vec2d startPoint = controlPoints.get(0);
                Vec2d wavelengthPoint = controlPoints.get(1);
                Vec2d lengthPoint = controlPoints.get(2);
                Vec2d amplitudePoint = controlPoints.get(3);
                
                // 计算波长（第一点到第二点的距离）
                double wavelength = startPoint.distance(wavelengthPoint);
                
                // 计算振幅
                double actualAmplitude = calculateActualAmplitude(startPoint, lengthPoint, amplitudePoint);
                
                SineCurveShape curve = new SineCurveShape(
                    startPoint, lengthPoint, actualAmplitude, wavelength, phase);
                
                ShapeStyle style = getStyleHandler().getFinalStyle();
                if (style != null) {
                    curve.setStyle(style);
                }
                
                LOGGER.debug("SineCurveTool 创建正弦曲线: 起点={}, 长度点={}, 振幅={:.2f}, 波长={:.2f}", 
                            startPoint, lengthPoint, actualAmplitude, wavelength);
                return curve;
            }
            
            return null;
        }
        
        @Override
        public void reset() {
            resetDrawingState();
        }
        
        @Override
        public String getStrategyName() {
            return "SineCurveInteractionStrategy";
        }
        
        @Override
        public String getStrategyDescription() {
            return PlotI18n.modeLabel("strategy.plot.draw.sine");
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
        // 如果有自定义 SineCurveInteractionStrategy 则返回，否则用多步策略
        return new SineCurveInteractionStrategy();
    }
} 
