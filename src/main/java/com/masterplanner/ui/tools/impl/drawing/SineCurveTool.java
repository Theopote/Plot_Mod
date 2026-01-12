package com.masterplanner.ui.tools.impl.drawing;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.geometry.shapes.SineCurveShape;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.core.snap.SnapManager;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.tool.ToolConfigEvent;
import com.masterplanner.ui.component.Icons;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import imgui.ImDrawList;
import com.masterplanner.ui.canvas.CanvasCamera;
import com.masterplanner.ui.tools.snap.SnapEnhancer;

import java.util.List;
import java.util.ArrayList;
import com.masterplanner.ui.tools.impl.drawing.strategy.IInteractionStrategy;

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
 * @author MasterPlanner Team
 * @version 2.0 - 策略模式版本
 */
public class SineCurveTool extends DrawingTool {
    // 统一捕捉可视化
    private final com.masterplanner.ui.tools.snap.SnapEnhancer snapEnhancer =
            new com.masterplanner.ui.tools.snap.SnapEnhancer("SineCurveTool");
    private static final Logger LOGGER = LoggerFactory.getLogger(SineCurveTool.class);
    
    // ====== 配置常量 ======
    
    public static final String CONFIG_KEY_PHASE = "phase";
    
    // ====== 数值常量 ======
    
    private static final float DEFAULT_WAVELENGTH = 100.0f;
    private static final float DEFAULT_AMPLITUDE = 50.0f;
    private static final float DEFAULT_PHASE = 0.0f;
    private static final int DEFAULT_SEGMENTS = 100;
    
    // ====== 渲染常量 ======
    
    private static final int PREVIEW_COLOR = 0x80FFFFFF; // 白色半透明
    private static final int START_POINT_COLOR = 0xFF0000FF; // 蓝色
    private static final int END_POINT_COLOR = 0xFF00FF00; // 绿色
    private static final int AMPLITUDE_POINT_COLOR = 0xFFFF0000; // 红色
    private static final int BASELINE_COLOR = 0xFF888888; // 灰色

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
        "点击确定正弦曲线起点",
        "点击确定波长（第一点到第二点的距离）",
        "点击确定整体长度（从起点到第三点的总长度）",
        "点击确定振幅"
    );
    
    // ====== 构造函数 ======
    
    /**
     * 依赖注入构造函数（推荐方式）
     */
    public SineCurveTool(AppState appState, SnapManager snapManager) {
        super("sine", "正弦曲线", Icons.SINE_IDENTIFIER, 
              "绘制正弦波形，支持自定义波长、振幅和相位", appState, snapManager, InteractionType.CLICK_AND_CLICK);
        
        initializeSineCurveTool();
    }
    
    /**
     * 兼容性构造函数
     * @deprecated 推荐使用依赖注入构造函数
     */
    @Deprecated
    public SineCurveTool(AppState appState) {
        super("sine", "正弦曲线", Icons.SINE_IDENTIFIER, 
              "绘制正弦波形，支持自定义波长、振幅和相位");
        
        initializeSineCurveTool();
    }
    
    // ====== 初始化方法 ======
    
    /**
     * 初始化正弦曲线工具
     */
    private void initializeSineCurveTool() {
        // 订阅配置事件
        EventBus.getInstance().subscribe(ToolConfigEvent.class, event -> {
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
        // 起点
        if (!controlPoints.isEmpty()) {
            context.drawCircle(controlPoints.getFirst(), 3.0, java.awt.Color.BLUE);
        }
        
        // 波长点
        if (controlPoints.size() >= 2) {
            context.drawCircle(controlPoints.get(1), 3.0, java.awt.Color.GREEN);
        }
        
        // 长度点
        if (controlPoints.size() >= 3) {
            context.drawCircle(controlPoints.get(2), 3.0, java.awt.Color.YELLOW);
        } else if (currentMousePoint != null && controlPoints.size() == 2) {
            context.drawCircle(currentMousePoint, 3.0, java.awt.Color.ORANGE);
        }
        
        // 振幅点
        if (controlPoints.size() >= 4) {
            context.drawCircle(controlPoints.get(3), 3.0, java.awt.Color.RED);
        } else if (currentMousePoint != null && controlPoints.size() == 3) {
            context.drawCircle(currentMousePoint, 3.0, java.awt.Color.MAGENTA);
        }
    }
    
    /**
     * 渲染基线
     */
    private void renderBaseline(DrawContext context) {
        if (controlPoints.size() >= 2) {
            // 绘制波长线（起点到波长点）
            context.drawLine(controlPoints.get(0), controlPoints.get(1), java.awt.Color.GREEN);
            
            if (controlPoints.size() >= 3) {
                // 绘制长度线（起点到长度点）
                context.drawLine(controlPoints.get(0), controlPoints.get(2), java.awt.Color.YELLOW);
            } else if (currentMousePoint != null) {
                // 预览长度线
                context.drawLine(controlPoints.getFirst(), currentMousePoint, java.awt.Color.ORANGE);
            }
        } else if (controlPoints.size() == 1 && currentMousePoint != null) {
            context.drawLine(controlPoints.getFirst(), currentMousePoint, java.awt.Color.LIGHT_GRAY);
        }
    }
    
    /**
     * 渲染振幅线
     */
    private void renderAmplitudeLine(DrawContext context) {
        if (controlPoints.size() >= 3) {
            Vec2d startPoint = controlPoints.get(0);
            Vec2d lengthPoint = controlPoints.get(2);
            Vec2d midPoint = startPoint.add(lengthPoint).divide(2);
            
            Vec2d amplitudePoint = controlPoints.size() >= 4 ? controlPoints.get(3) : currentMousePoint;
            if (amplitudePoint != null) {
                context.drawLine(midPoint, amplitudePoint, java.awt.Color.MAGENTA);
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
                com.masterplanner.core.graphics.style.ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
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
        // 起点
        if (!controlPoints.isEmpty()) {
            Vec2d screenPoint = camera.worldToScreen(controlPoints.getFirst());
            drawList.addCircleFilled(
                (float) screenPoint.x, (float) screenPoint.y,
                POINT_SIZE, START_POINT_COLOR
            );
        }
        
        // 波长点
        if (controlPoints.size() >= 2) {
            Vec2d screenPoint = camera.worldToScreen(controlPoints.get(1));
            drawList.addCircleFilled(
                (float) screenPoint.x, (float) screenPoint.y,
                POINT_SIZE, END_POINT_COLOR
            );
        }
        
        // 长度点
        if (controlPoints.size() >= 3) {
            Vec2d screenPoint = camera.worldToScreen(controlPoints.get(2));
            drawList.addCircleFilled(
                (float) screenPoint.x, (float) screenPoint.y,
                POINT_SIZE, 0xFFFFFF00 // 黄色
            );
        } else if (currentMousePoint != null && controlPoints.size() == 2) {
            Vec2d screenPoint = camera.worldToScreen(currentMousePoint);
            drawList.addCircleFilled(
                (float) screenPoint.x, (float) screenPoint.y,
                POINT_SIZE, 0xFFFF8000 // 橙色
            );
        }
        
        // 振幅点
        if (controlPoints.size() >= 4) {
            Vec2d screenPoint = camera.worldToScreen(controlPoints.get(3));
            drawList.addCircleFilled(
                (float) screenPoint.x, (float) screenPoint.y,
                POINT_SIZE, AMPLITUDE_POINT_COLOR
            );
        } else if (currentMousePoint != null && controlPoints.size() == 3) {
            Vec2d screenPoint = camera.worldToScreen(currentMousePoint);
            drawList.addCircleFilled(
                (float) screenPoint.x, (float) screenPoint.y,
                POINT_SIZE, 0xFFFF00FF // 洋红色
            );
        }
    }
    
    /**
     * 渲染基线（ImGui版本）
     */
    private void renderBaselineImGui(ImDrawList drawList, CanvasCamera camera) {
        if (controlPoints.size() >= 2) {
            // 绘制波长线（起点到波长点）
            Vec2d screenStart = camera.worldToScreen(controlPoints.get(0));
            Vec2d screenWavelength = camera.worldToScreen(controlPoints.get(1));
            drawList.addLine(
                (float) screenStart.x, (float) screenStart.y,
                (float) screenWavelength.x, (float) screenWavelength.y,
                0xFF00FF00, LINE_THICKNESS // 绿色
            );
            
            if (controlPoints.size() >= 3) {
                // 绘制长度线（起点到长度点）
                Vec2d screenLength = camera.worldToScreen(controlPoints.get(2));
                drawList.addLine(
                    (float) screenStart.x, (float) screenStart.y,
                    (float) screenLength.x, (float) screenLength.y,
                    0xFFFFFF00, LINE_THICKNESS // 黄色
                );
            } else if (currentMousePoint != null) {
                // 预览长度线
                Vec2d screenEnd = camera.worldToScreen(currentMousePoint);
                drawList.addLine(
                    (float) screenStart.x, (float) screenStart.y,
                    (float) screenEnd.x, (float) screenEnd.y,
                    0xFFFF8000, LINE_THICKNESS // 橙色
                );
            }
        } else if (controlPoints.size() == 1 && currentMousePoint != null) {
            Vec2d screenStart = camera.worldToScreen(controlPoints.getFirst());
            Vec2d screenEnd = camera.worldToScreen(currentMousePoint);
            drawList.addLine(
                (float) screenStart.x, (float) screenStart.y,
                (float) screenEnd.x, (float) screenEnd.y,
                BASELINE_COLOR, LINE_THICKNESS
            );
        }
    }
    
    /**
     * 渲染振幅线（ImGui版本）
     */
    private void renderAmplitudeLineImGui(ImDrawList drawList, CanvasCamera camera) {
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
                    0xFFFF00FF, LINE_THICKNESS // 洋红色
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
                int previewColor = PREVIEW_COLOR; // 默认颜色
                
                if (getStyleHandler() != null) {
                    com.masterplanner.core.graphics.style.ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
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
        if (controlPoints.size() >= 2 && currentMousePoint != null) {
            Vec2d displayPoint = currentMousePoint;
            Vec2d screenPoint = camera.worldToScreen(displayPoint);
            
            String info = "";
            if (controlPoints.size() >= 2) {
                double wavelength = controlPoints.get(0).distance(controlPoints.get(1));
                info += String.format("波长: %.1f  ", wavelength);
            }
            
            if (controlPoints.size() >= 3) {
                double totalLength = controlPoints.get(0).distance(controlPoints.get(2));
                info += String.format("长度: %.1f  ", totalLength);
            }
            
            if (controlPoints.size() >= 3) {
                Vec2d lengthPoint = controlPoints.size() >= 4 ? controlPoints.get(2) : currentMousePoint;
                double actualAmplitude = calculateActualAmplitude(
                    controlPoints.get(0), lengthPoint, displayPoint);
                info += String.format("振幅: %.1f  ", actualAmplitude);
            }
            
            info += String.format("相位: %.1f°", Math.toDegrees(phase));
            
            drawList.addText(
                (float) screenPoint.x + 15, (float) screenPoint.y - 20,
                PREVIEW_COLOR, info
            );
        }
    }
    
    // ====== 自定义交互策略 ======
    
    /**
     * 正弦曲线工具专用交互策略
     * 支持三点点击交互
     */
    private class SineCurveInteractionStrategy implements com.masterplanner.ui.tools.impl.drawing.strategy.IInteractionStrategy {
        
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
        public com.masterplanner.core.model.Shape getFinalShape() {
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
            return "正弦曲线工具三点点击交互策略";
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