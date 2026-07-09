package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.graphics.DrawContext;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.tools.impl.modify.ChamferTool;
import com.plot.ui.tools.impl.modify.helper.ChamferHandler;
import com.plot.ui.tools.impl.modify.helper.IModifyHandler;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.KeyEvent;
import java.awt.Color;
import java.util.List;
import com.plot.utils.PlotI18n;

/**
 * 倒角策略 - 控制器版本
 * 
 * <p>作为控制器角色，专注于用户交互流程和状态管理，
 * 所有几何计算委托给ChamferHandler完成。</p>
 * 
 * @author Plot Team
 * @version 1.1 - 优化状态管理和参数传递
 */
public class ChamferStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChamferStrategy.class);

    // 优化：使用ChamferTool的常量，确保一致性
    public static final String CONFIG_KEY_DISTANCE = ChamferTool.CONFIG_KEY_DISTANCE;
    public static final String CONFIG_KEY_PREVIEW = ChamferTool.CONFIG_KEY_PREVIEW;
    public static final String CONFIG_KEY_HIGHLIGHT = ChamferTool.CONFIG_KEY_HIGHLIGHT;
    
    // 键盘常量
    private static final int KEY_ESC = KeyEvent.VK_ESCAPE;
    private static final int KEY_PLUS = KeyEvent.VK_EQUALS; // + 键
    private static final int KEY_MINUS = KeyEvent.VK_MINUS; // - 键
    
    // 参数记录类 - 优化参数封装
    public record ChamferParameters(double distance, Vec2d clickPoint1, Vec2d clickPoint2) 
            implements IModifyHandler.ModifyParameters {
        
        @Override
        public boolean hasParameter(String name) {
            return CONFIG_KEY_DISTANCE.equals(name)
                    || "clickPoint1".equals(name)
                    || "clickPoint2".equals(name);
        }
        
        @Override
        public Object getParameter(String name) {
            return switch (name) {
                case "distance" -> distance;
                case "clickPoint1" -> clickPoint1;
                case "clickPoint2" -> clickPoint2;
                default -> null;
            };
        }
        
        @Override
        public void setParameter(String key, Object value) {
            // 只读参数，不实现设置
            throw new UnsupportedOperationException("ChamferParameters is read-only");
        }
    }
    
    public enum ChamferState {
        SELECT_FIRST_SHAPE,
        SELECT_SECOND_SHAPE,
        READY_TO_APPLY
    }
    
    private ChamferState currentState;
    private Shape shape1;
    private Shape shape2;
    private Vec2d clickPoint1;
    private Vec2d clickPoint2;
    private double distance;
    private List<Shape> previewShapes; // 使用 Shape 列表存储预览图形
    private Shape highlightedShape;
    
    // 引入 ChamferHandler
    private final ChamferHandler chamferHandler;
    
    // 框选状态
    private boolean isBoxSelecting = false;
    private Vec2d boxStartPoint;
    private Vec2d boxCurrentPoint;
    private final java.util.LinkedList<Shape> boxSelectedShapes = new java.util.LinkedList<>();
    
    // 从 Tool 传入的配置
    private boolean previewEnabled = true;
    private boolean highlightEnabled = true;
    
    // 优化：使用ChamferTool的常量
    private static final double MIN_DISTANCE = ChamferTool.MIN_DISTANCE;
    private static final double DEFAULT_DISTANCE = ChamferTool.DEFAULT_DISTANCE;
    
    // ====== 框选相关方法 ======
    
    /**
     * 更新框选临时选择
     */
    private void updateBoxSelection(ModifyToolContext context) {
        if (boxStartPoint == null || boxCurrentPoint == null) {
            return;
        }
        
        boxSelectedShapes.clear();
        List<Shape> allShapes = context.getAppState().getActiveLayer().getShapes();
        
        for (Shape shape : allShapes) {
            if (shape != null && shape.isVisible() && !shape.isDeleted() && isChamferableShape(shape)) {
                // 使用虚线框选择逻辑（总是相交选择）
                if (com.plot.ui.tools.impl.modify.helper.GeometricSelectionHelper.isShapeInRectangleSelection(shape, boxStartPoint, boxCurrentPoint, false)) {
                    boxSelectedShapes.add(shape);
                }
            }
        }
    }
    
    /**
     * 完成框选选择
     */
    private void finalizeBoxSelection(ModifyToolContext context) {
        context.setStatusMessage(PlotI18n.status("status.plot.trim.box_select_done", boxSelectedShapes.size()));
    }
    
    /**
     * 渲染框选预览
     */
    private void renderBoxSelectionPreview(DrawContext context) {
        if (!isBoxSelecting || boxStartPoint == null || boxCurrentPoint == null) {
            return;
        }
        // 绘制虚线框
        context.drawRect(boxStartPoint, boxCurrentPoint, toColor(ThemeManager.getInstance().getCurrentTheme().text));
    }
    
    /**
     * 处理选择第一个图形时的鼠标按下
     */
    private ModifyResult handleMouseDown_SelectFirst(Shape shape, Vec2d clickPoint, ModifyToolContext context) {
        shape1 = shape;
        clickPoint1 = clickPoint;
        currentState = ChamferState.SELECT_SECOND_SHAPE;
        context.setStatusMessage("status.plot.chamfer.select_second");
        return ModifyResult.CONTINUE;
    }
    
    /**
     * 处理选择第二个图形时的鼠标按下
     */
    private ModifyResult handleMouseDown_SelectSecond(Shape shape, Vec2d clickPoint, ModifyToolContext context) {
        if (shape1 == null) {
            return ModifyResult.IGNORED;
        }

        if (!isChamferableShape(shape)) {
            return ModifyResult.IGNORED;
        }

        boolean sameShapeCornerMode = shape == shape1 && (shape instanceof PolylineShape || shape instanceof Polygon);
        if (shape == shape1 && !sameShapeCornerMode) {
            context.setStatusMessage("status.plot.chamfer.select_different_edge");
            return ModifyResult.IGNORED;
        }

        shape2 = shape;
        clickPoint2 = clickPoint;

        ChamferParameters params = createModifyParameters();
        IModifyHandler.ValidationResult validation = chamferHandler.validateModification(List.of(shape1, shape2), params);
        if (!validation.isValid()) {
            shape2 = null;
            clickPoint2 = null;
            previewShapes = null;
            context.setStatusMessage(validation.getErrorMessage());
            return ModifyResult.CONTINUE;
        }

        // 验证通过后再生成预览并切换到可应用状态
        updatePreviewWithContext(context);
        if (isReadyToApply()) {
           currentState = ChamferState.READY_TO_APPLY;
           context.setStatusMessage(getStatusMessage());
        }
        return ModifyResult.CONTINUE;
    }

    public ChamferStrategy(AppState appState) {
        this.distance = DEFAULT_DISTANCE;
        // 实例化 Handler
        this.chamferHandler = new ChamferHandler(appState);
        reset();
    }
    
    @Override
    public void reset() {
        currentState = ChamferState.SELECT_FIRST_SHAPE;
        shape1 = null;
        shape2 = null;
        clickPoint1 = null;
        clickPoint2 = null;
        previewShapes = null;
        previewEnabled = true;
        isBoxSelecting = false;
        boxStartPoint = null;
        boxCurrentPoint = null;
        boxSelectedShapes.clear();
        clearHighlight();
    }
    
    @Override
    public ModifyResult onMouseDown(Vec2d point, int button, ModifyToolContext context) {
        if (button == 1 && isReadyToApply()) {
            return applyChamfer(context);
        }
        if (button != 0) return ModifyResult.IGNORED;

        // 开始框选
        isBoxSelecting = true;
        boxStartPoint = point;
        boxCurrentPoint = point;
        boxSelectedShapes.clear();
        return ModifyResult.CONTINUE;
    }
    
    @Override
    public ModifyResult onMouseMove(Vec2d point, ModifyToolContext context) {
        if (isBoxSelecting) {
            if (boxStartPoint == null) {
                isBoxSelecting = false;
            } else {
            // 框选模式
            boxCurrentPoint = point;
            updateBoxSelection(context);
            return ModifyResult.CONTINUE;
            }
        }

        try {
            Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(point, context.getCamera());
            return switch (currentState) {
                case SELECT_FIRST_SHAPE -> handleMouseMove_SelectFirst(snappedPoint, context);
                case SELECT_SECOND_SHAPE -> handleMouseMove_SelectSecond(snappedPoint, context);
                case READY_TO_APPLY -> handleMouseMove_Ready(snappedPoint, context);
            };
        } catch (Exception e) {
            LOGGER.error("ChamferStrategy 鼠标移动处理失败: {}", e.getMessage(), e);
            return ModifyResult.IGNORED;
        }
    }
    
    @Override
    public ModifyResult onMouseUp(Vec2d point, int button, ModifyToolContext context) {
        if (button == 1 && isReadyToApply()) {
            return applyChamfer(context);
        }
        if (isBoxSelecting) {
            // 检查是否为点选（拖动距离小于阈值）
            double dragDistance = boxStartPoint.distance(point);
            if (dragDistance < 4.0) { // 拖动阈值
                // 点选模式：选择单个图形
                Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(point, context.getCamera());
                Shape clickedShape = context.findShapeAt(snappedPoint, 10.0);
                if (isChamferableShape(clickedShape)) {
                    boxSelectedShapes.clear();
                    boxSelectedShapes.add(clickedShape);
                }
            } else {
                // 框选模式：应用最终选择
                finalizeBoxSelection(context);
            }
            
            // 重置框选状态
            isBoxSelecting = false;
            boxStartPoint = null;
            boxCurrentPoint = null;
            
            // 如果有选中的图形，选择第一个作为目标
            if (!boxSelectedShapes.isEmpty()) {
                Shape selectedShape = boxSelectedShapes.getFirst();
                Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(point, context.getCamera());
                ModifyResult res = switch (currentState) {
                    case SELECT_FIRST_SHAPE -> handleMouseDown_SelectFirst(selectedShape, snappedPoint, context);
                    case SELECT_SECOND_SHAPE -> handleMouseDown_SelectSecond(selectedShape, snappedPoint, context);
                    case READY_TO_APPLY -> ModifyResult.IGNORED;
                };
                // 不在此处结束工具；保持 CONTINUE 让用户确认（例如按鼠标右键）或继续交互
                return res == ModifyResult.IGNORED ? ModifyResult.CONTINUE : res;
            }
            return ModifyResult.CONTINUE;
        }
        return ModifyResult.IGNORED;
    }
    
    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        switch (keyCode) {
            case KEY_ESC:
                reset();
                context.setStatusMessage(getStatusMessage());
                return ModifyResult.CANCEL;
                
            case KEY_PLUS:
            case KEY_MINUS:
                distance += (keyCode == KEY_PLUS ? 0.5 : -0.5);
                distance = Math.max(MIN_DISTANCE, distance);
                if (isReadyToApply()) {
                    updatePreviewWithContext(context); // 更新预览
                }
                context.setStatusMessage(getStatusMessage());
                return ModifyResult.CONTINUE;
        }
        return ModifyResult.IGNORED;
    }

    @Override
    public ModifyResult onMouseWheel(Vec2d pos, double delta, ModifyToolContext context) {
        distance += delta * 0.5;
        distance = Math.max(MIN_DISTANCE, distance);
        if (isReadyToApply()) {
            updatePreviewWithContext(context);
        }
        context.setStatusMessage(getStatusMessage());
        return ModifyResult.CONTINUE;
    }
    
    @Override
    public String getStrategyName() {
        return PlotI18n.modeLabel("strategy.plot.name.chamfer");
    }

    @Override
    public String getStrategyDescription() {
        return PlotI18n.modeLabel("strategy.plot.desc.chamfer");
    }
    
    /**
     * 优化：更清晰的状态消息管理，根据当前状态和验证结果动态生成消息
     */
    public String getStatusMessage() {
        return switch (currentState) {
            case SELECT_FIRST_SHAPE -> PlotI18n.status("status.plot.chamfer.select_first_with_distance", distance);
            case SELECT_SECOND_SHAPE -> PlotI18n.status("status.plot.chamfer.select_second_corner");
            case READY_TO_APPLY -> {
                if (shape1 != null && shape2 != null) {
                    ChamferParameters params = createModifyParameters();
                    IModifyHandler.ValidationResult validation = chamferHandler.validateModification(List.of(shape1, shape2), params);
                    if (validation.isValid()) {
                        yield PlotI18n.status("status.plot.chamfer.confirm_right", distance);
                    } else {
                        yield validation.getErrorMessage();
                    }
                } else {
                    yield PlotI18n.status("status.plot.chamfer.ready");
                }
            }
        };
    }
    
    @Override
    public void renderPreview(DrawContext context) {
        // 渲染框选预览
        if (isBoxSelecting && boxStartPoint != null && boxCurrentPoint != null) {
            renderBoxSelectionPreview(context);
        }
        
        // 渲染倒角预览
        if (previewEnabled && previewShapes != null) {
            for (Shape shape : previewShapes) {
                renderDashedPreviewShape(context, shape);
            }
        }
    }

    private void renderDashedPreviewShape(DrawContext context, Shape shape) {
        Color previewColor = toColor(ThemeManager.getInstance().getCurrentTheme().text);
        if (shape instanceof LineShape line) {
            context.drawDashedLine(line.getStart(), line.getEnd(), previewColor);
            return;
        }

        if (shape instanceof PolylineShape polyline) {
            List<Vec2d> points = polyline.getPoints();
            for (int i = 0; i < points.size() - 1; i++) {
                context.drawDashedLine(points.get(i), points.get(i + 1), previewColor);
            }
            if (polyline.isClosed() && points.size() > 2) {
                context.drawDashedLine(points.getLast(), points.getFirst(), previewColor);
            }
            return;
        }

        if (shape instanceof Polygon polygon) {
            List<Vec2d> points = polygon.getPoints();
            for (int i = 0; i < points.size() - 1; i++) {
                context.drawDashedLine(points.get(i), points.get(i + 1), previewColor);
            }
            if (polygon.isClosed() && points.size() > 2) {
                context.drawDashedLine(points.getLast(), points.getFirst(), previewColor);
            }
            return;
        }

        shape.render(context);
    }
    
    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (previewEnabled && previewShapes != null) {
            var theme = ThemeManager.getInstance().getCurrentTheme();
            for (Shape shape : previewShapes) {
                // ImGui预览渲染实现
                // 这里需要根据具体的Shape类型进行绘制
                if (shape instanceof LineShape lineShape) {
                    // 绘制线段
                    Vec2d start = lineShape.getStart();
                    Vec2d end = lineShape.getEnd();
                    Vec2d screenStart = camera.worldToScreen(start);
                    Vec2d screenEnd = camera.worldToScreen(end);
                    drawList.addLine(
                        (float)screenStart.x, (float)screenStart.y,
                        (float)screenEnd.x, (float)screenEnd.y,
                        theme.text
                    );
                } else if (shape instanceof PolylineShape polylineShape) {
                    List<Vec2d> points = polylineShape.getPoints();
                    for (int i = 0; i < points.size() - 1; i++) {
                        Vec2d p1 = camera.worldToScreen(points.get(i));
                        Vec2d p2 = camera.worldToScreen(points.get(i + 1));
                        drawList.addLine((float)p1.x, (float)p1.y, (float)p2.x, (float)p2.y, theme.text);
                    }
                    if (polylineShape.isClosed() && points.size() > 2) {
                        Vec2d p1 = camera.worldToScreen(points.getLast());
                        Vec2d p2 = camera.worldToScreen(points.getFirst());
                        drawList.addLine((float)p1.x, (float)p1.y, (float)p2.x, (float)p2.y, theme.text);
                    }
                } else if (shape instanceof Polygon polygon) {
                    List<Vec2d> points = polygon.getPoints();
                    for (int i = 0; i < points.size() - 1; i++) {
                        Vec2d p1 = camera.worldToScreen(points.get(i));
                        Vec2d p2 = camera.worldToScreen(points.get(i + 1));
                        drawList.addLine((float)p1.x, (float)p1.y, (float)p2.x, (float)p2.y, theme.text);
                    }
                    if (polygon.isClosed() && points.size() > 2) {
                        Vec2d p1 = camera.worldToScreen(points.getLast());
                        Vec2d p2 = camera.worldToScreen(points.getFirst());
                        drawList.addLine((float)p1.x, (float)p1.y, (float)p2.x, (float)p2.y, theme.text);
                    }
                }
                // 可以添加其他Shape类型的绘制逻辑
            }
        }
    }

    private static Color toColor(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return new Color(red, green, blue, alpha);
    }
    
    @Override
    public ModifyCommand getModifyCommand() {
        if (!isReadyToApply()) {
            return null;
        }
        
        ChamferParameters params = createModifyParameters();
        List<Shape> originalShapes = List.of(shape1, shape2);
        
        return chamferHandler.createModifyCommand(originalShapes, null, params);
    }
    
    /**
     * 优化：简化状态机，将isReadyToApply()与currentState绑定
     */
    public boolean isReadyToApply() {
        // 更直观的可用性判定：只要有两条线就认为可以准备应用（状态由调用方维护）
        return shape1 != null && shape2 != null;
    }

    public void updateConfig(String key, Object value) {
        boolean needsPreviewUpdate = false;
        
        if (CONFIG_KEY_DISTANCE.equals(key) && value instanceof Number num) {
            this.distance = Math.max(MIN_DISTANCE, num.doubleValue());
            needsPreviewUpdate = true;
        } else if (CONFIG_KEY_PREVIEW.equals(key) && value instanceof Boolean val) {
            this.previewEnabled = val;
        } else if (CONFIG_KEY_HIGHLIGHT.equals(key) && value instanceof Boolean val) {
            this.highlightEnabled = val;
        }
        
        // 安全地更新预览，不依赖context
        if (needsPreviewUpdate && isReadyToApply()) {
            updatePreviewInternal();
        }
    }
    
    /**
     * 获取当前距离
     */
    public double getDistance() {
        return distance;
    }
    
    /**
     * 内部预览更新 - 不依赖context
     */
    private void updatePreviewInternal() {
        if (!previewEnabled || shape1 == null || shape2 == null) {
            previewShapes = null;
            return;
        }

        ChamferParameters params = createModifyParameters();
        IModifyHandler.ValidationResult validation = chamferHandler.validateModification(List.of(shape1, shape2), params);

        if (validation.isValid()) {
            this.previewShapes = chamferHandler.createPreviewShapes(List.of(shape1, shape2), params);
        } else {
            this.previewShapes = null;
        }
    }
    
    /**
     * 带context的预览更新 - 用于交互过程中的状态消息更新
     */
    private void updatePreviewWithContext(ModifyToolContext context) {
        if (!previewEnabled || shape1 == null || shape2 == null) {
            previewShapes = null;
            return;
        }

        ChamferParameters params = createModifyParameters();
        IModifyHandler.ValidationResult validation = chamferHandler.validateModification(List.of(shape1, shape2), params);

        if (validation.isValid()) {
            this.previewShapes = chamferHandler.createPreviewShapes(List.of(shape1, shape2), params);
        } else {
            this.previewShapes = null;
            if (context != null) {
                context.setStatusMessage(validation.getErrorMessage());
            }
        }
    }

    private ModifyResult applyChamfer(ModifyToolContext context) {
        if (!isReadyToApply()) return ModifyResult.IGNORED;

        ChamferParameters params = createModifyParameters();
        List<Shape> originalShapes = List.of(shape1, shape2);
        
        // 最终应用时再次验证
        IModifyHandler.ValidationResult validation = chamferHandler.validateModification(originalShapes, params);
        if (!validation.isValid()) {
            context.setStatusMessage(validation.getErrorMessage());
            return ModifyResult.IGNORED;
        }
        
        // 创建命令
        ModifyCommand command = chamferHandler.createModifyCommand(originalShapes, null, params);
        if (command != null) {
            context.executeModifyCommand(command);
            context.setStatusMessage(PlotI18n.status("status.plot.chamfer.complete_distance", distance));
            reset();
            return ModifyResult.COMPLETE;
        } else {
            context.setStatusMessage("status.plot.chamfer.command_failed");
            return ModifyResult.IGNORED;
        }
    }
    
    /**
     * 创建参数对象 - 使用record类优化
     */
    private ChamferParameters createModifyParameters() {
        return new ChamferParameters(distance, clickPoint1, clickPoint2);
    }

    private ModifyResult handleMouseMove_SelectFirst(Vec2d snappedPoint, ModifyToolContext context) {
        Shape shape = findShapeAtPoint(snappedPoint, context);
        if (isChamferableShape(shape)) {
            updateHighlight(shape);
            context.setStatusMessage("status.plot.fillet.select_first");
            return ModifyResult.CONTINUE;
        } else {
            clearHighlight();
            context.setStatusMessage("status.plot.chamfer.select_valid");
            return ModifyResult.IGNORED;
        }
    }

    private ModifyResult handleMouseMove_SelectSecond(Vec2d snappedPoint, ModifyToolContext context) {
        Shape shape = findShapeAtPoint(snappedPoint, context);
        boolean sameShapeCornerMode = shape == shape1 && (shape instanceof PolylineShape || shape instanceof Polygon);

        if (isChamferableShape(shape) && (shape != shape1 || sameShapeCornerMode)) {
            updateHighlight(shape);
            updatePreviewWithShapes(shape1, shape, snappedPoint, context);
            context.setStatusMessage("status.plot.chamfer.select_second_scroll");
            return ModifyResult.CONTINUE;
        } else if (shape == shape1) {
            clearHighlight();
            clearPreview();
            context.setStatusMessage("status.plot.chamfer.select_different_edge");
            return ModifyResult.IGNORED;
        } else {
            clearHighlight();
            clearPreview();
            context.setStatusMessage("status.plot.chamfer.select_second_valid");
            return ModifyResult.IGNORED;
        }
    }

    private ModifyResult handleMouseMove_Ready(Vec2d snappedPoint, ModifyToolContext context) {
        if (shape1 != null && shape2 != null) {
            updatePreviewWithContext(context);
            context.setStatusMessage(PlotI18n.status("status.plot.chamfer.confirm_right_scroll", distance));
            return ModifyResult.CONTINUE;
        }
        return ModifyResult.IGNORED;
    }

    private void updatePreviewWithShapes(Shape firstShape, Shape secondShape, Vec2d hoverPoint, ModifyToolContext context) {
        if (!previewEnabled || firstShape == null || secondShape == null) {
            previewShapes = null;
            return;
        }

        Vec2d originalClickPoint2 = clickPoint2;
        clickPoint2 = hoverPoint;
        try {
            ChamferParameters params = createModifyParameters();
            IModifyHandler.ValidationResult validation = chamferHandler.validateModification(List.of(firstShape, secondShape), params);
            if (validation.isValid()) {
                previewShapes = chamferHandler.createPreviewShapes(List.of(firstShape, secondShape), params);
                if (context != null) {
                    context.setPreviewEnabled(true);
                }
            } else {
                if (previewShapes == null && context != null) {
                    context.setStatusMessage(validation.getErrorMessage());
                }
            }
        } finally {
            clickPoint2 = originalClickPoint2;
        }
    }

    private Shape findShapeAtPoint(Vec2d point, ModifyToolContext context) {
        try {
            return context.findShapeAt(point, 10.0);
        } catch (Exception e) {
            LOGGER.error("查找图形时发生错误: {}", e.getMessage(), e);
            return null;
        }
    }

    private void updateHighlight(Shape shape) {
        if (!highlightEnabled) {
            return;
        }

        if (highlightedShape != null && highlightedShape != shape1 && highlightedShape != shape2) {
            highlightedShape.setHighlighted(false);
        }

        if (shape != null && shape != shape1 && shape != shape2) {
            highlightedShape = shape;
            shape.setHighlighted(true);
        } else {
            highlightedShape = null;
        }
    }

    private void clearHighlight() {
        if (highlightedShape != null && highlightedShape != shape1 && highlightedShape != shape2) {
            highlightedShape.setHighlighted(false);
        }
        highlightedShape = null;
    }

    private void clearPreview() {
        previewShapes = null;
    }

    private boolean isChamferableShape(Shape shape) {
        return shape instanceof LineShape
                || shape instanceof PolylineShape
                || shape instanceof Polygon;
    }
}
