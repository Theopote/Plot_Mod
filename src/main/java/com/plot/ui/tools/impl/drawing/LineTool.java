package com.plot.ui.tools.impl.drawing;

import com.plot.api.geometry.Vec2d;
import com.plot.api.render.IRenderVisitor;
import com.plot.api.state.IAppState;
import com.plot.api.snap.ISnapManager;
import com.plot.api.graphics.IShapeStyle;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.AffineTransform;
import com.plot.core.graphics.DrawContext;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.model.Shape;
import com.plot.infrastructure.event.EventListener;
import com.plot.infrastructure.event.base.Event;
import com.plot.infrastructure.event.tool.ToolConfigEvent;
import com.plot.ui.component.Icons;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.theme.ThemeManager;
import imgui.ImDrawList;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.ui.tools.impl.drawing.strategy.IInteractionStrategy;
import com.plot.ui.tools.impl.drawing.strategy.MultiStepInteractionStrategy;
import com.plot.ui.tools.impl.modify.helper.IShapeVisitor;
import com.plot.ui.tools.snap.SnapEnhancer;
import com.plot.utils.ExceptionDebug;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 直线工具类 - 基于策略模式的现代化实现
 * <p>
 * 支持以下功能:
 * 1. 单线模式 - 绘制单条直线
 * 2. 多线模式 - 绘制多条平行线
 * 3. 角度约束 - 按住Shift键约束角度
 * 4. 实时预览 - 移动时显示预览
 * 5. 图层颜色一致性 - 预览和最终线条都使用活动图层颜色
 * <p>
 * 采用策略模式架构：
 * - 使用CLICK_AND_CLICK交互模式
 * - 委托所有鼠标事件给策略处理
 * - 通过StyleHandler管理样式
 * - 通过SnapHandler处理吸附
 * 
 * @version 3.0 - 策略模式兼容版本
 */
public class LineTool extends DrawingTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(LineTool.class);
    
    // 键码常量
    private static final int ESC_KEY = 27;
    
    // 绘制模式常量
    private static final String MODE_SINGLE = "single";
    private static final String MODE_MULTI = "multi";
    
    // 配置字段 - LineTool特有的功能
    private String currentDrawingType = MODE_SINGLE;
    private int lineCount = 2;  // 修复：与选项面板默认值保持一致
    private float lineSpacing = 10.0f;

    // 统一捕捉可视化
    private final SnapEnhancer snapEnhancer = new SnapEnhancer("LineTool");

    // 事件处理器
    private final EventListener toolConfigHandler = this::handleToolConfig;

    /**
     * 构造函数（推荐方式 - 使用CLICK_AND_CLICK交互模式）
     * @param appState 应用状态管理器
     * @param snapManager 吸附管理器
     */
    public LineTool(IAppState appState, ISnapManager snapManager) {
        super("line", Icons.LINE_IDENTIFIER, appState, snapManager, InteractionType.CLICK_AND_CLICK);
        subscribeToEvents();
    }
    
    /**
     * 订阅相关事件
     */
    private void subscribeToEvents() {
        try {
            eventBus.subscribe(ToolConfigEvent.class, toolConfigHandler);
            LOGGER.debug("LineTool 事件订阅完成");
        } catch (Exception e) {
            LOGGER.error("LineTool 事件订阅失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理工具配置事件
     */
    private void handleToolConfig(Event event) {
        if (!(event instanceof ToolConfigEvent configEvent) || !"line".equals(configEvent.getToolId())) {
            return;
        }
        
        String key = configEvent.getOptionName();
        String value = String.valueOf(configEvent.getValue());
        
        if (key == null || value == null) {
            LOGGER.warn("配置事件参数无效: key={}, value={}", key, value);
            return;
        }
        
        updateConfig(key, value);
    }

    // ====== 键盘事件处理 ======

    @Override
    public boolean onKeyDown(int key) {
        if (key == ESC_KEY) {
            if (isInState()) {
                resetDrawing("ESC键取消绘制");
                LOGGER.info("LineTool: ESC键取消绘制");
            } else {
                onCancel();
            }
            return true;
        }

        return super.onKeyDown(key);
    }
    
    @Override
    public boolean onKeyUp(int key) {
        return super.onKeyUp(key);
    }

    // ====== 核心图形创建方法 ======
    
    @Override
    protected Shape createShape(Vec2d startPoint, Vec2d endPoint) {
        try {
            if (startPoint == null || endPoint == null) {
                return null;
            }

            // 应用正交约束（按住Shift）
            Vec2d finalEndPoint = endPoint;
            if (ImGui.getIO().getKeyShift()) {
                finalEndPoint = applyOrthogonalConstraint(startPoint, endPoint);
            }

            if (MODE_MULTI.equals(currentDrawingType)) {
                return createMultiLineShape(startPoint, finalEndPoint);
            } else {
                // 单线模式
                LineShape line = new LineShape(startPoint, finalEndPoint);
                ShapeStyle style = getStyleHandler().getFinalStyle();
                if (style != null) {
                    line.setStyle(style);
                }
                return line;
            }
        } catch (Exception e) {
            LOGGER.error("LineTool: 创建图形失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 创建多线图形
     * 修复：使用线条偏移量正确计算多条平行线的位置
     */
    private MultiLineShape createMultiLineShape(Vec2d start, Vec2d end) {
        List<LineShape> lines = createParallelLines(start, end);
        MultiLineShape multiLine = new MultiLineShape(lines);
        
        // 设置样式
        ShapeStyle finalStyle = getStyleHandler().getFinalStyle();
        if (finalStyle != null) {
            multiLine.setStyle(finalStyle);
        }
        
        return multiLine;
    }

    // ====== 渲染方法 ======

    @Override
    protected void renderPreview(DrawContext context) {
        if (previewShape == null) {
            return;
        }

        try {
            // 修复：使用StyleHandler统一管理样式，确保颜色一致性
            ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
                Color lineColor = previewStyle != null ? previewStyle.getLineColor() :
                    toColor(ThemeManager.getInstance().getCurrentTheme().accent);

            // 修复：对于多线模式，需要特殊处理预览渲染
            if (MODE_MULTI.equals(currentDrawingType)) {
                // 多线模式：直接使用预览图形作为参考轴，在其两侧对称分布多条平行线
                Vec2d startPoint = null;
                Vec2d endPoint = null;
                
                if (previewShape instanceof LineShape line) {
                    // 策略模式提供的LineShape就是用户绘制的轨迹，作为多线组的参考轴
                    startPoint = line.getStart();
                    endPoint = line.getEnd();
                } else if (previewShape instanceof MultiLineShape multiLine) {
                    // 如果预览图形已经是MultiLineShape，需要使用预览颜色渲染每条线
                    for (LineShape line : multiLine.getLines()) {
                        context.drawLine(line.getStart(), line.getEnd(), lineColor);
                    }
                    return; // 早期返回，避免重复渲染
                }
                
                if (startPoint != null && endPoint != null) {
                    // 修复：确保预览渲染也应用正交约束，与最终图形保持一致
                    Vec2d finalEndPoint = endPoint;
                    if (ImGui.getIO().getKeyShift()) {
                        finalEndPoint = applyOrthogonalConstraint(startPoint, endPoint);
                    }
                    
                    // 渲染所有平行线（使用用户绘制的轨迹作为参考轴）
                    List<LineShape> parallelLines = createParallelLines(startPoint, finalEndPoint);
                    for (LineShape line : parallelLines) {
                        context.drawLine(line.getStart(), line.getEnd(), lineColor);
                    }
                }
            } else if (previewShape instanceof LineShape line) {
                // 单线模式：渲染主线
                context.drawLine(line.getStart(), line.getEnd(), lineColor);
            } else if (previewShape instanceof MultiLineShape multiLine) {
                // 如果预览图形是MultiLineShape，使用预览颜色渲染每条线
                for (LineShape line : multiLine.getLines()) {
                    context.drawLine(line.getStart(), line.getEnd(), lineColor);
                }
            }

            // 渲染统一捕捉指示器
            snapEnhancer.renderSnapIndicator(context);
        } catch (Exception e) {
            LOGGER.error("LineTool: 预览渲染失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (previewShape == null || camera == null) {
            return;
        }

        try {
            // 修复：使用StyleHandler获取正确的预览颜色
            int imguiColor = getColor();

            float lineWidth = 2.0f; // 预览线宽

            // 修复：对于多线模式，需要特殊处理预览渲染
            if (MODE_MULTI.equals(currentDrawingType)) {
                // 多线模式：直接使用预览图形作为参考轴，在其两侧对称分布多条平行线
                Vec2d startPoint = null;
                Vec2d endPoint = null;
                
                if (previewShape instanceof LineShape line) {
                    // 策略模式提供的LineShape就是用户绘制的轨迹，作为多线组的参考轴
                    startPoint = line.getStart();
                    endPoint = line.getEnd();
                } else if (previewShape instanceof MultiLineShape multiLine) {
                    // 如果预览图形已经是MultiLineShape，渲染其所有线条
                    for (LineShape line : multiLine.getLines()) {
                        Vec2d screenStart = camera.worldToScreen(line.getStart());
                        Vec2d screenEnd = camera.worldToScreen(line.getEnd());
                        drawList.addLine(
                            (float) screenStart.x, (float) screenStart.y,
                            (float) screenEnd.x, (float) screenEnd.y,
                            imguiColor, lineWidth
                        );
                    }
                    return; // 早期返回，避免重复渲染
                }
                
                if (startPoint != null && endPoint != null) {
                    // 修复：确保预览渲染也应用正交约束，与最终图形保持一致
                    Vec2d finalEndPoint = endPoint;
                    if (ImGui.getIO().getKeyShift()) {
                        finalEndPoint = applyOrthogonalConstraint(startPoint, endPoint);
                    }
                    
                    // 渲染所有平行线（使用用户绘制的轨迹作为参考轴）
                    List<LineShape> parallelLines = createParallelLines(startPoint, finalEndPoint);
                    for (LineShape line : parallelLines) {
                        Vec2d screenStart = camera.worldToScreen(line.getStart());
                        Vec2d screenEnd = camera.worldToScreen(line.getEnd());
                        drawList.addLine(
                            (float) screenStart.x, (float) screenStart.y,
                            (float) screenEnd.x, (float) screenEnd.y,
                            imguiColor, lineWidth
                        );
                    }
                }
            } else if (previewShape instanceof LineShape line) {
                // 单线模式：渲染主线
                Vec2d screenStart = camera.worldToScreen(line.getStart());
                Vec2d screenEnd = camera.worldToScreen(line.getEnd());
                drawList.addLine(
                    (float) screenStart.x, (float) screenStart.y,
                    (float) screenEnd.x, (float) screenEnd.y,
                    imguiColor, lineWidth
                );
            } else if (previewShape instanceof MultiLineShape multiLine) {
                // 如果预览图形是MultiLineShape，渲染其所有线条
                for (LineShape line : multiLine.getLines()) {
                    Vec2d screenStart = camera.worldToScreen(line.getStart());
                    Vec2d screenEnd = camera.worldToScreen(line.getEnd());
                    drawList.addLine(
                        (float) screenStart.x, (float) screenStart.y,
                        (float) screenEnd.x, (float) screenEnd.y,
                        imguiColor, lineWidth
                    );
                }
            }

            // 渲染统一捕捉指示器
            snapEnhancer.renderSnapIndicator(drawList, camera);
        } catch (Exception e) {
            LOGGER.error("LineTool: ImGui预览渲染失败: {}", e.getMessage(), e);
        }
    }

    private int getColor() {
        Color previewColor = toColor(ThemeManager.getInstance().getCurrentTheme().accent);

        if (getStyleHandler() != null) {
            ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
            if (previewStyle != null && previewStyle.getLineColor() != null) {
                previewColor = previewStyle.getLineColor();
            }
        }

        // 转换为ImGui颜色格式
        return (previewColor.getAlpha() << 24) |
                        (previewColor.getBlue() << 16) |
                        (previewColor.getGreen() << 8) |
                        previewColor.getRed();
    }

    private static Color toColor(int color) {
        return new Color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 255);
    }

    // ====== 辅助方法 ======
    
    /**
     * 应用角度约束
     * @param start 起点
     * @param end 终点
     * @return 约束后的终点
     */
    private Vec2d applyOrthogonalConstraint(Vec2d start, Vec2d end) {
        if (start == null || end == null) {
            return end;
        }

        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double length = Math.hypot(dx, dy);
        if (length < 1e-9) {
            return end;
        }

        // 约束到最接近的45°倍角：0/45/90/135/.../315
        double angle = Math.atan2(dy, dx);
        double step = Math.PI / 4.0; // 45°
        double snappedAngle = Math.round(angle / step) * step;

        return new Vec2d(
            start.x + length * Math.cos(snappedAngle),
            start.y + length * Math.sin(snappedAngle)
        );
    }

    /**
     * 创建平行线集合
     * 修复：使用线条偏移量正确计算多条平行线的位置
     */
    private List<LineShape> createParallelLines(Vec2d start, Vec2d end) {
        List<LineShape> lines = new ArrayList<>();
        
        // 计算垂直于线段的单位向量（用于偏移）
        Vec2d direction = end.subtract(start);
        if (direction.length() == 0) {
            // 如果起点和终点相同，创建一条零长度线
            lines.add(new LineShape(start, end));
            return lines;
        }
        
        direction = direction.normalize();
        Vec2d perpendicular = new Vec2d(-direction.y, direction.x); // 垂直向量
        
        // 为每条线创建偏移版本
        for (int i = 0; i < lineCount; i++) {
            float offset = calculateLineOffset(i);
            Vec2d offsetVector = perpendicular.multiply(offset);
            
            Vec2d lineStart = start.add(offsetVector);
            Vec2d lineEnd = end.add(offsetVector);
            
            LineShape line = new LineShape(lineStart, lineEnd);
            
            // 应用样式
            ShapeStyle style = getStyleHandler().getFinalStyle();
            if (style != null) {
                line.setStyle(style.clone());
            }
            
            lines.add(line);
        }
        
        return lines;
    }
    
    /**
     * 计算线条偏移量
     * 修复：确保用户绘制的轨迹是多线组的中心轴
     */
    private float calculateLineOffset(int index) {
        int halfCount = lineCount / 2;
        float offset;
        
        if (lineCount % 2 == 0) {
            // 偶数条线：在原始线两侧对称分布
            // 例如：4条线时，偏移为 [-1.5, -0.5, +0.5, +1.5] * lineSpacing
            offset = index < halfCount ? 
                -lineSpacing * (halfCount - index - 0.5f) : 
                lineSpacing * (index - halfCount + 0.5f);
        } else {
            // 奇数条线：在原始线两侧对称分布，中间一条在参考轴上
            // 例如：3条线时，偏移为 [-1, 0, +1] * lineSpacing
            offset = -lineSpacing * (halfCount - index);
        }
        
        return offset;
    }

    // ====== 配置管理 ======
    
    /**
     * 立即更新预览 - 用于配置变化时的实时响应
     */
    private void updatePreviewImmediately() {
        try {
            // 如果有预览图形，重新创建以反映配置变化
            if (previewShape != null) {
                // 获取当前的控制点
                if (interactionStrategy != null) {
                    List<Vec2d> controlPoints = interactionStrategy.getControlPoints();
                    Vec2d currentMousePoint = interactionStrategy.getCurrentMousePoint();
                    
                    if (controlPoints.size() >= 2) {
                        Vec2d start = controlPoints.get(0);
                        Vec2d end = currentMousePoint != null ? currentMousePoint : controlPoints.get(1);
                        
                        // 重新创建预览图形
                        previewShape = createShape(start, end);
                        
                        // 应用预览样式
                        if (previewShape != null && getStyleHandler() != null) {
                            ShapeStyle previewStyle = getStyleHandler().getPreviewStyle();
                            if (previewStyle != null) {
                                previewShape.setStyle(previewStyle);
                            }
                        }
                        
                        LOGGER.debug("LineTool: 立即更新预览，模式={}, 线条数量={}", currentDrawingType, lineCount);
                    }
                }
            }
            
            // 标记需要重绘
            markDirty();
        } catch (Exception e) {
            LOGGER.error("LineTool: 立即更新预览失败: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public void updateConfig(String key, String value) {
        boolean configChanged = false;
        
        try {
            switch (key) {
                case "type", "lineType" -> {
                    configChanged = setDrawingMode(value);
                    // 修复：模式切换时立即更新预览，不等待isInState()
                    if (configChanged) {
                        updatePreviewImmediately();
                    }
                }
                case "count", "lineCount" -> {
                    try {
                        configChanged = setLineCount(Integer.parseInt(value));
                        // 修复：线条数量变化时立即更新预览
                        if (configChanged) {
                            updatePreviewImmediately();
                        }
                    } catch (NumberFormatException e) {
                        LOGGER.warn("LineTool: 无效的线条数量: {}", value);
                    }
                }
                case "spacing", "lineSpacing" -> {
                    try {
                        configChanged = setLineSpacing(Float.parseFloat(value));
                        // 修复：线条间距变化时立即更新预览
                        if (configChanged) {
                            updatePreviewImmediately();
                        }
                    } catch (NumberFormatException e) {
                        LOGGER.warn("LineTool: 无效的线条间距: {}", value);
                    }
                }
                case "angleConstraint" -> LOGGER.debug("LineTool: 忽略角度约束配置（已使用正交约束）");
                default -> LOGGER.debug("LineTool: 未知配置项: {} = {}", key, value);
            }
            
            // 修复：如果正在绘制且配置发生变化，标记需要更新
            if (configChanged && isInState()) {
                markDirty();
            }
            
        } catch (Exception e) {
            LOGGER.error("LineTool: 处理配置事件时出错: key={}, value={}, error={}", key, value, e.getMessage(), e);
        }
    }

    /**
     * 设置绘制模式
     */
    public boolean setDrawingMode(String mode) {
        if (MODE_SINGLE.equals(mode) || MODE_MULTI.equals(mode)) {
            String oldMode = currentDrawingType;
            currentDrawingType = mode;
            LOGGER.info("LineTool: 绘制模式已更新: {} -> {}", oldMode, mode);

            // 按模式更新使用方法提示，避免模式切换后残留旧提示
            if (MODE_MULTI.equals(mode)) {
                setStatusMessage("status.plot.line.multi_click");
            } else {
                setStatusMessage("");
            }
            
            // 修复：模式切换时立即清理旧的预览，确保状态一致性
            if (!oldMode.equals(mode)) {
                previewShape = null;
                
                // 如果正在绘制中，重置绘制状态
                if (isInState()) {
                    resetDrawing("模式切换");
                    LOGGER.debug("LineTool: 模式切换，重置绘制状态");
                }
                
                LOGGER.debug("LineTool: 模式切换，清理预览图形");
            }
            
            return true;
        }
        LOGGER.warn("LineTool: 无效的绘制模式: {}", mode);
        return false;
    }

    /**
     * 设置线条数量
     */
    public boolean setLineCount(int count) {
        if (count > 0 && count <= 50) { // 添加合理的上限
            lineCount = count;
            LOGGER.info("LineTool: 线条数量已更新: {}", count);
            return true;
        }
        LOGGER.warn("LineTool: 无效的线条数量: {}", count);
        return false;
    }

    /**
     * 设置线条间距
     */
    public boolean setLineSpacing(float spacing) {
        if (spacing > 0 && spacing <= 1000) { // 添加合理的上限
            lineSpacing = spacing;
            LOGGER.info("LineTool: 线条间距已更新: {}", spacing);
            return true;
        }
        LOGGER.warn("LineTool: 无效的线条间距: {}", spacing);
        return false;
    }

    // ====== 资源清理 ======
    
    @Override
    public void dispose() {
        // 取消事件订阅
        if (eventBus != null) {
            try {
                eventBus.unsubscribe(ToolConfigEvent.class, toolConfigHandler);
                LOGGER.debug("LineTool: 已取消事件订阅");
            } catch (Exception e) {
                LOGGER.warn("LineTool: 取消事件订阅时出错: {}", e.getMessage());
            }
        }
        
        // 调用父类dispose
        super.dispose();
        
        LOGGER.debug("LineTool: 资源清理完成");
    }

    /**
     * 处理鼠标滚轮：在多线模式且已开始绘制（已点击起点）时，调整线条间距并更新预览
     */
    @Override
    public boolean onMouseWheel(com.plot.api.geometry.Vec2d pos, double delta) {
        try {
            // 只在多线模式处理滚轮
            if (!MODE_MULTI.equals(currentDrawingType)) {
                return false;
            }

            // 只在已经开始绘制（至少有一个控制点）时响应滚轮
            if (interactionStrategy == null) return false;
            java.util.List<com.plot.api.geometry.Vec2d> controlPoints = interactionStrategy.getControlPoints();
            if (controlPoints == null || controlPoints.isEmpty()) {
                return false;
            }

            // 计算调整量并应用（向上滚轮为正）
            float adjustment = (float) (delta); // 每个滚动单位调整1.0个世界单位间距
            float newSpacing = Math.max(0.1f, Math.min(1000f, lineSpacing + adjustment));
            boolean changed = setLineSpacing(newSpacing);
            if (changed) {
                setStatusMessage("status.plot.line.multi_click");
                // 立即更新预览以反映变化
                updatePreviewImmediately();
                return true;
            }
            return false;
        } catch (Exception e) {
            LOGGER.error("LineTool.onMouseWheel 处理失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 重写提交流程：如果是 MultiLineShape，则拆分为若干独立的 LineShape 并逐个提交。
     * 这样最终在 AppState 中存储的是普通的单线对象，而不是组合对象。
     */
    @Override
    public void commitShape(com.plot.core.model.Shape shape) {
        if (shape instanceof MultiLineShape multi) {
            try {
                List<LineShape> lines = multi.getLines();
                if (lines.isEmpty()) {
                    resetDrawing("提交的多线为空");
                    return;
                }

                // 对每条子线应用样式并提交到 AppState
                for (LineShape line : lines) {
                    if (line == null) continue;

                    // 应用最终样式
                    try {
                        getStyleHandler().applyFinalStyle(line);
                    } catch (Exception e) {
                        LOGGER.warn("LineTool: 为子线应用样式失败，继续提交其他线: {}", e.getMessage());
                    }

                }

                if (appState instanceof com.plot.core.state.AppState concreteAppState) {
                    ModifyCommand command = new ModifyCommand(new ArrayList<>(), new ArrayList<>(lines), concreteAppState);
                    concreteAppState.getCommandHistory().execute(command);
                } else if (appState != null) {
                    for (LineShape line : lines) {
                        if (line == null) continue;
                        try {
                            java.lang.reflect.Method addShapeMethod = appState.getClass()
                                .getMethod("addShape", com.plot.core.model.Shape.class);
                            addShapeMethod.invoke(appState, line);
                        } catch (Exception reflectionEx) {
                            LOGGER.error("LineTool: 通过反射提交子线失败: {}", reflectionEx.getMessage(), reflectionEx);
                            throw new RuntimeException("无法提交子线到AppState", reflectionEx);
                        }
                    }
                } else {
                    LOGGER.error("LineTool: 提交子线失败，appState 为 null");
                    throw new IllegalStateException("AppState 不可用");
                }

                // 所有子线提交完成后统一重置状态
                resetDrawing("多线提交完成，已拆分为单线");
                LOGGER.info("LineTool: 多线已拆分并提交为 {} 条独立线", lines.size());
            } catch (RuntimeException e) {
                LOGGER.error("LineTool: 提交多线时出错: {}", e.getMessage(), e);
                resetDrawing("多线提交异常");
                throw e;
            } catch (Exception e) {
                LOGGER.error("LineTool: 提交多线时发生未知错误: {}", e.getMessage(), e);
                resetDrawing("多线提交异常");
                throw new RuntimeException("提交多线失败", e);
            }
        } else {
            super.commitShape(shape);
        }
    }

    @Override
    protected IInteractionStrategy createStrategy(InteractionType type) {
        // 使用通用线条策略，并在本地增强捕捉可视化
        IInteractionStrategy delegate = MultiStepInteractionStrategy.forLineTool();
        return new EnhancingLineStrategy(delegate);
    }

    private class EnhancingLineStrategy implements IInteractionStrategy {
        private final IInteractionStrategy delegate;
        EnhancingLineStrategy(IInteractionStrategy delegate) { this.delegate = delegate; }

        @Override
        public InteractionResult onMouseDown(Vec2d pos, int button, DrawingToolContext context) {
            try { snapEnhancer.performEnhancedSnap(pos, context); } catch (Exception e) { ExceptionDebug.log("LineTool: update snap on mouse down", e); }
            return delegate.onMouseDown(pos, button, context);
        }

        @Override
        public InteractionResult onMouseMove(Vec2d pos, DrawingToolContext context) {
            try { snapEnhancer.performEnhancedSnap(pos, context); } catch (Exception e) { ExceptionDebug.log("LineTool: update snap on mouse move", e); }
            return delegate.onMouseMove(pos, context);
        }

        @Override
        public InteractionResult onMouseUp(Vec2d pos, int button, DrawingToolContext context) {
            return delegate.onMouseUp(pos, button, context);
        }

        @Override
        public com.plot.core.model.Shape getFinalShape() {
            return delegate.getFinalShape();
        }

        @Override
        public void reset() { delegate.reset(); }

        @Override
        public String getStrategyName() { return "EnhancingLineStrategy"; }

        @Override
        public String getStrategyDescription() { return "Wraps base line strategy with snap visualization"; }

        @Override
        public java.util.List<Vec2d> getControlPoints() { return delegate.getControlPoints(); }

        @Override
        public Vec2d getCurrentMousePoint() { return delegate.getCurrentMousePoint(); }
    }

    public String getDrawingMode() {
        return currentDrawingType;
    }

    public int getLineCount() {
        return lineCount;
    }

    public float getLineSpacing() {
        return lineSpacing;
    }

    /**
     * 自定义多线图形类
     * 包含多条独立的平行线
     */
    private static class MultiLineShape extends Shape {
        private final List<LineShape> lines;
        
        public MultiLineShape(List<LineShape> lines) {
            super(lines.isEmpty() ? new Vec2d(0, 0) : lines.getFirst().getStart());
            this.lines = new ArrayList<>(lines);
        }
        
        /**
         * 获取所有的线条
         * @return 线条列表
         */
        public List<LineShape> getLines() {
            return new ArrayList<>(lines);
        }
        
        @Override
        public void draw(DrawContext context) {
            // 修复：绘制所有线条时应用当前样式
            IShapeStyle currentStyle = getStyle();
            if (currentStyle instanceof ShapeStyle shapeStyle) {
                // 确保每条子线都使用当前的样式
                for (LineShape line : lines) {
                    line.setStyle(shapeStyle);
                    line.draw(context);
                }
            } else {
                // 备用：使用默认样式绘制所有线条
                for (LineShape line : lines) {
                    line.draw(context);
                }
            }
        }
        
        @Override
        public void translate(Vec2d offset) {
            for (LineShape line : lines) {
                line.translate(offset);
            }
        }
        
        @Override
        public void rotate(double angle, Vec2d center) {
            for (LineShape line : lines) {
                line.rotate(angle, center);
            }
        }
        
        @Override
        public Shape transform(AffineTransform transformMatrix) {
            // 变换所有线条
            for (LineShape line : lines) {
                line.transform(transformMatrix);
            }
            return this; // 多线变换后仍然是多线
        }
        
        @Override
        public void scale(Vec2d scale, Vec2d center) {
            for (LineShape line : lines) {
                line.scale(scale, center);
            }
        }
        
        @Override
        public com.plot.core.geometry.BoundingBox getBoundingBox() {
            if (lines.isEmpty()) return null;
            
            com.plot.core.geometry.BoundingBox bounds = lines.getFirst().getBoundingBox();
            for (int i = 1; i < lines.size(); i++) {
                com.plot.core.geometry.BoundingBox lineBounds = lines.get(i).getBoundingBox();
                if (lineBounds != null) {
                    bounds = bounds.union(lineBounds);
                }
            }
            return bounds;
        }
        
        @Override
        public boolean contains(Vec2d point) {
            for (LineShape line : lines) {
                if (line.contains(point)) return true;
            }
            return false;
        }
        
        @Override
        public boolean containsPoint(Vec2d point, double tolerance) {
            for (LineShape line : lines) {
                if (line.containsPoint(point, tolerance)) return true;
            }
            return false;
        }
        
        @Override
        public Vec2d getClosestPoint(Vec2d point) {
            if (lines.isEmpty()) return point;
            
            Vec2d closest = lines.getFirst().getClosestPoint(point);
            double minDistance = closest.distance(point);
            
            for (int i = 1; i < lines.size(); i++) {
                Vec2d candidate = lines.get(i).getClosestPoint(point);
                double distance = candidate.distance(point);
                if (distance < minDistance) {
                    minDistance = distance;
                    closest = candidate;
                }
            }
            return closest;
        }
        
        @Override
        public List<Vec2d> getControlPoints() {
            List<Vec2d> points = new ArrayList<>();
            for (LineShape line : lines) {
                points.addAll(line.getControlPoints());
            }
            return points;
        }
        
        @Override
        public void setControlPoint(int index, Vec2d point) {
            // 简化实现：只修改第一条线的控制点
            if (!lines.isEmpty() && index < lines.getFirst().getControlPoints().size()) {
                lines.getFirst().setControlPoint(index, point);
            }
        }
        
        @Override
        public boolean intersects(Shape other) {
            for (LineShape line : lines) {
                if (line.intersects(other)) return true;
            }
            return false;
        }
        
        @Override
        public List<Vec2d> getIntersectionPoints(Shape other) {
            List<Vec2d> intersections = new ArrayList<>();
            for (LineShape line : lines) {
                intersections.addAll(line.getIntersectionPoints(other));
            }
            return intersections;
        }
        
        @Override
        public List<Vec2d> getIntersectionsWith(Shape other) {
            return getIntersectionPoints(other);
        }
        
        @Override
        public List<Vec2d> getExtensionIntersectionsWith(Shape other, Vec2d point, double maxDistance) {
            List<Vec2d> intersections = new ArrayList<>();
            for (LineShape line : lines) {
                intersections.addAll(line.getExtensionIntersectionsWith(other, point, maxDistance));
            }
            return intersections;
        }
        
        @Override
        public List<Vec2d> getEndpoints() {
            List<Vec2d> endpoints = new ArrayList<>();
            for (LineShape line : lines) {
                endpoints.addAll(line.getEndpoints());
            }
            return endpoints;
        }
        
        @Override
        public Vec2d getTangentAt(Vec2d point) {
            // 找到最近的线条并返回其切线
            if (lines.isEmpty()) return null;
            
            LineShape nearestLine = lines.getFirst();
            double minDistance = nearestLine.getClosestPoint(point).distance(point);
            
            for (int i = 1; i < lines.size(); i++) {
                double distance = lines.get(i).getClosestPoint(point).distance(point);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestLine = lines.get(i);
                }
            }
            return nearestLine.getTangentAt(point);
        }
        
        @Override
        public double getSignedDistance(Vec2d point) {
            if (lines.isEmpty()) return Double.POSITIVE_INFINITY;
            
            double minDistance = lines.getFirst().getSignedDistance(point);
            for (int i = 1; i < lines.size(); i++) {
                double distance = lines.get(i).getSignedDistance(point);
                if (Math.abs(distance) < Math.abs(minDistance)) {
                    minDistance = distance;
                }
            }
            return minDistance;
        }
        
        @Override
        public List<Shape> split(List<Vec2d> points, Vec2d pickPoint) {
            List<Shape> result = new ArrayList<>();
            for (LineShape line : lines) {
                result.addAll(line.split(points, pickPoint));
            }
            return result;
        }
        
        @Override
        public Shape extend(Vec2d point, double distance) {
            List<LineShape> extendedLines = new ArrayList<>();
            for (LineShape line : lines) {
                extendedLines.add((LineShape) line.extend(point, distance));
            }
            return new MultiLineShape(extendedLines);
        }
        
        @Override
        public Shape extend(Vec2d point, Vec2d toPoint) {
            List<LineShape> extendedLines = new ArrayList<>();
            for (LineShape line : lines) {
                extendedLines.add((LineShape) line.extend(point, toPoint));
            }
            return new MultiLineShape(extendedLines);
        }
        
        @Override
        public Shape trimToPoint(Vec2d point) {
            List<LineShape> trimmedLines = new ArrayList<>();
            for (LineShape line : lines) {
                trimmedLines.add((LineShape) line.trimToPoint(point));
            }
            return new MultiLineShape(trimmedLines);
        }
        
        @Override
        public Shape createOffset(double distance) {
            List<LineShape> offsetLines = new ArrayList<>();
            for (LineShape line : lines) {
                offsetLines.add((LineShape) line.createOffset(distance));
            }
            return new MultiLineShape(offsetLines);
        }
        
        @Override
        public List<Vec2d> getIntersectionsWithPolyline(List<Vec2d> points) {
            List<Vec2d> intersections = new ArrayList<>();
            for (LineShape line : lines) {
                intersections.addAll(line.getIntersectionsWithPolyline(points));
            }
            return intersections;
        }
        
        @Override
        public boolean intersectsPolyline(List<Vec2d> points) {
            for (LineShape line : lines) {
                if (line.intersectsPolyline(points)) return true;
            }
            return false;
        }
        
        @Override
        public Shape clone() {
            super.clone(); // 保持父类克隆流程
            List<LineShape> clonedLines = new ArrayList<>();
            for (LineShape line : lines) {
                clonedLines.add((LineShape) line.clone());
            }
            MultiLineShape clone = new MultiLineShape(clonedLines);
            clone.setStyle(getStyle().clone());
            clone.setSelected(isSelected());
            clone.setVisible(isVisible());
            return clone;
        }
        
        @Override
        public String serialize() {
            StringBuilder sb = new StringBuilder();
            sb.append("MULTILINE ");
            for (LineShape line : lines) {
                sb.append(line.serialize()).append(";");
            }
            return sb.toString();
        }
        
        @Override
        public void deserialize(String data) {
            // 简化实现
            lines.clear();
            if (data.startsWith("MULTILINE ")) {
                String[] lineDatas = data.substring(10).split(";");
                for (String lineData : lineDatas) {
                    if (!lineData.trim().isEmpty()) {
                        LineShape line = new LineShape(new Vec2d(0, 0), new Vec2d(0, 0));
                        line.deserialize(lineData.trim());
                        lines.add(line);
                    }
                }
            }
        }
        
        @Override
        public List<Vec2d> getPoints() {
            List<Vec2d> allPoints = new ArrayList<>();
            for (LineShape line : lines) {
                // 获取每条线的起点和终点
                allPoints.add(line.getStart());
                allPoints.add(line.getEnd());
            }
            return allPoints;
        }

        @Override
        public Shape accept(IShapeVisitor visitor) {
            return visitor.visit(this);
        }
        
        @Override
        public void accept(IRenderVisitor visitor,
                           imgui.ImDrawList drawList, com.plot.ui.canvas.CanvasCamera camera) {
            visitor.render(this, drawList, camera);
        }
        
        @Override
        public List<Shape> breakShape(Vec2d firstBreakPoint, Vec2d secondBreakPoint, String breakMode) {
            List<Shape> newShapes = new ArrayList<>();
            
            // 对每条线进行打断操作
            for (LineShape line : lines) {
                List<Shape> brokenLines = line.breakShape(firstBreakPoint, secondBreakPoint, breakMode);
                newShapes.addAll(brokenLines);
            }
            
            return newShapes;
        }
        
        @Override
        public double getDistanceToPoint(Vec2d point) {
            if (lines.isEmpty()) {
                return Double.MAX_VALUE;
            }
            
            // 计算点到所有线条的最小距离
            double minDistance = Double.MAX_VALUE;
            for (LineShape line : lines) {
                double distance = line.getDistanceToPoint(point);
                minDistance = Math.min(minDistance, distance);
            }
            
            return minDistance;
        }
        
        @Override
        protected void drawImGui(imgui.ImDrawList drawList, com.plot.ui.canvas.CanvasCamera camera) {
            try {
                // 绘制所有线条
                for (LineShape line : lines) {
                    line.renderImGui(drawList, camera);
                }
            } catch (Exception e) {
                // 记录错误但不抛出异常
                LOGGER.error("LineTool: 渲染多线ImGui时发生错误", e);
            }
        }
    }
}