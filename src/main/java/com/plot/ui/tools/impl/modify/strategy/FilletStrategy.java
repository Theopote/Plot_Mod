package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.graphics.DrawContext;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.tools.impl.modify.constants.FilletConstants;
import com.plot.ui.tools.impl.modify.helper.FilletHandler;
import com.plot.ui.tools.impl.modify.helper.IModifyHandler;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.KeyEvent;
import java.util.List;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.geometry.shapes.PolylineShape;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.geometry.shapes.ArcShape;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.EllipseShape;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.EllipticalArcShape;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.SineCurveShape;
import com.plot.core.geometry.shapes.SpiralShape;
import com.plot.core.geometry.shapes.CableShape;
import com.plot.utils.PlotI18n;

/**
 * 圆角策略 - 控制器版本
 * 
 * <p>作为控制器角色，专注于用户交互流程和状态管理，
 * 所有几何计算委托给FilletHandler完成。</p>
 * 
 * @version 4.0 - 优化版本，改进用户交互和预览稳定性
 */
public class FilletStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(FilletStrategy.class);

    // 键盘常量
    private static final int KEY_ESC = KeyEvent.VK_ESCAPE;
    private static final int KEY_PLUS = KeyEvent.VK_EQUALS; // + 键
    private static final int KEY_MINUS = KeyEvent.VK_MINUS; // - 键
    
    // 参数记录类 - 包含半径和点击位置
    public record FilletParameters(double radius, Vec2d clickPoint1, Vec2d clickPoint2) 
            implements IModifyHandler.ModifyParameters {
        
        @Override
        public boolean hasParameter(String name) {
            return FilletConstants.CONFIG_KEY_RADIUS.equals(name) 
                || "clickPoint1".equals(name) 
                || "clickPoint2".equals(name);
        }
        
        @Override
        public Object getParameter(String name) {
            return switch (name) {
                case "radius" -> radius;
                case "clickPoint1" -> clickPoint1;
                case "clickPoint2" -> clickPoint2;
                default -> null;
            };
        }
        
        @Override
        public void setParameter(String key, Object value) {
            // 只读参数，不实现设置
            throw new UnsupportedOperationException("FilletParameters is read-only");
        }
    }
    
    public enum FilletState {
        SELECT_FIRST_LINE,
        SELECT_SECOND_LINE,
        READY_TO_APPLY
    }
    
    private FilletState currentState;
    private LineShape line1;
    private LineShape line2;
    private Shape shape1; // 添加第一个图形引用
    private Shape shape2; // 添加第二个图形引用
    private Vec2d clickPoint1; // 第一条线的点击位置
    private Vec2d clickPoint2; // 第二条线的点击位置
    private double radius;
    private List<Shape> previewShapes; // 使用 Shape 列表存储预览图形
    
    // 引入 FilletHandler
    private final FilletHandler filletHandler;
    
    // 框选状态
    private boolean isBoxSelecting = false;
    private Vec2d boxStartPoint;
    private Vec2d boxCurrentPoint;
    private java.util.List<Shape> boxSelectedShapes = new java.util.ArrayList<>();
    
    // 从 Tool 传入的配置
    private boolean previewEnabled = true;
    
    // 预览稳定性优化
    private long lastPreviewUpdate = 0;
    private static final long PREVIEW_DEBOUNCE_MS = 50; // 50ms防抖

    public FilletStrategy(AppState appState) {
        this.radius = FilletConstants.DEFAULT_RADIUS;
        // 实例化 FilletHandler
        this.filletHandler = new FilletHandler(appState);
        reset();
    }
    
    @Override
    public void reset() {
        currentState = FilletState.SELECT_FIRST_LINE;
        line1 = null;
        line2 = null;
        shape1 = null; // 重置第一个图形
        shape2 = null; // 重置第二个图形
        clickPoint1 = null; // 重置第一个点击位置
        clickPoint2 = null; // 重置第二个点击位置
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
            return applyFillet(context);
        }
        if (button != 0) return ModifyResult.IGNORED;

        // 开始框选
        isBoxSelecting = true;
        boxStartPoint = point;
        boxCurrentPoint = point;
        boxSelectedShapes.clear();
        LOGGER.debug("开始框选，起始点: {}", point);
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
            // 获取吸附后的点
            Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(point, context.getCamera());
            
            return switch (currentState) {
                case SELECT_FIRST_LINE -> handleMouseMove_SelectFirst(snappedPoint, context);
                case SELECT_SECOND_LINE -> handleMouseMove_SelectSecond(snappedPoint, context);
                case READY_TO_APPLY -> handleMouseMove_Ready(snappedPoint, context);
            };
            
        } catch (Exception e) {
            LOGGER.error("FilletStrategy 鼠标移动处理失败: {}", e.getMessage(), e);
            return ModifyResult.IGNORED;
        }
    }
    
    @Override
    public ModifyResult onMouseUp(Vec2d point, int button, ModifyToolContext context) {
        // 右键确认应用圆角
        if (button == 1 && isReadyToApply()) {
            return applyFillet(context);
        }
        
        if (isBoxSelecting) {
            if (boxStartPoint == null) {
                isBoxSelecting = false;
                boxCurrentPoint = null;
                boxSelectedShapes.clear();
                return ModifyResult.CONTINUE;
            }
            // 检查是否为点选（拖动距离小于阈值）
            double dragDistance = boxStartPoint.distance(point);
            if (dragDistance < 4.0) { // 拖动阈值
                // 点选模式：选择单个图形
                Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(point, context.getCamera());
                Shape clickedShape = context.findShapeAt(snappedPoint, FilletConstants.SELECTION_TOLERANCE);
                if (isFilletableShape(clickedShape)) {
                    boxSelectedShapes.clear();
                    boxSelectedShapes.add(clickedShape);
                    LOGGER.debug("点选模式：选中图形 {}", clickedShape.getId());
                    
                    // 保存点击位置并调用对应的处理方法
                    ModifyResult result = switch (currentState) {
                        case SELECT_FIRST_LINE -> handleMouseDown_SelectFirst(clickedShape, snappedPoint, context);
                        case SELECT_SECOND_LINE -> handleMouseDown_SelectSecond(clickedShape, snappedPoint, context);
                        case READY_TO_APPLY -> ModifyResult.IGNORED;
                    };
                    
                    // 重置框选状态
                    isBoxSelecting = false;
                    boxStartPoint = null;
                    boxCurrentPoint = null;
                    return result;
                }
            } else {
                // 框选模式：应用最终选择
                finalizeBoxSelection(context);
                LOGGER.debug("框选模式：选中 {} 个图形", boxSelectedShapes.size());
            }
            
            // 重置框选状态
            isBoxSelecting = false;
            boxStartPoint = null;
            boxCurrentPoint = null;
            
            return ModifyResult.COMPLETE;
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
                return handleKeyAdjustment(keyCode, context);
        }
        return ModifyResult.IGNORED;
    }
    
    @Override
    public ModifyResult onMouseWheel(Vec2d pos, double delta, ModifyToolContext context) {
        // 在所有状态下都支持滚轮调整半径
        double step = getDynamicStep();
        radius += delta * step;
        radius = Math.max(FilletConstants.MIN_RADIUS, Math.min(FilletConstants.MAX_RADIUS, radius));
        
        // 更新预览（带防抖）
        if (isReadyToApply()) {
            updatePreviewWithDebounce(context);
        }
        
        context.setStatusMessage(PlotI18n.status("status.plot.fillet.radius_scroll", radius));
        return ModifyResult.CONTINUE;
    }
    
    @Override
    public String getStrategyName() {
        return PlotI18n.modeLabel("strategy.plot.name.fillet");
    }

    @Override
    public String getStrategyDescription() {
        return PlotI18n.modeLabel("strategy.plot.desc.fillet");
    }
    
    public String getStatusMessage() {
        return switch (currentState) {
            case SELECT_FIRST_LINE -> PlotI18n.status("status.plot.fillet.select_first_line", radius);
            case SELECT_SECOND_LINE -> FilletConstants.STATUS_SELECT_SECOND_LINE;
            case READY_TO_APPLY -> PlotI18n.status("status.plot.fillet.ready_confirm", radius);
        };
    }
    
    @Override
    public void renderPreview(DrawContext context) {
        // 渲染框选预览
        if (isBoxSelecting && boxStartPoint != null && boxCurrentPoint != null) {
            renderBoxSelectionPreview(context);
        }
        
        // 渲染圆角预览
        if (previewEnabled && previewShapes != null) {
            for (Shape shape : previewShapes) {
                // 修复：使用render方法而不是draw方法，确保应用正确的样式
                shape.render(context);
            }
        }
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
                }
                // 可以添加其他Shape类型的绘制逻辑
            }
        }
    }
    
    @Override
    public ModifyCommand getModifyCommand() {
        if (!isReadyToApply()) {
            return null;
        }
        
        IModifyHandler handler = getCurrentHandler();
        if (handler == null) {
            return null;
        }
        
        FilletParameters params = createModifyParameters();
        List<Shape> originalShapes = List.of(shape1, shape2);
        
        return handler.createModifyCommand(originalShapes, null, params);
    }
    
    public boolean isReadyToApply() {
        return shape1 != null && shape2 != null;
    }

    public void updateConfig(String key, Object value) {
        boolean needsPreviewUpdate = false;

        switch (value) {
            case Number num when FilletConstants.CONFIG_KEY_RADIUS.equals(key) -> {
                this.radius = Math.max(FilletConstants.MIN_RADIUS, Math.min(FilletConstants.MAX_RADIUS, num.doubleValue()));
                needsPreviewUpdate = true;
            }
            case Boolean val when FilletConstants.CONFIG_KEY_PREVIEW.equals(key) -> this.previewEnabled = val;
            case Boolean val when FilletConstants.CONFIG_KEY_HIGHLIGHT.equals(key) -> {
                boolean highlightEnabled = val;
            }
            case null, default -> LOGGER.debug("未知的配置项: {} = {}", key, value);
        }
        
        // 安全地更新预览，不依赖context
        if (needsPreviewUpdate && isReadyToApply()) {
            updatePreviewInternal();
        }
    }
    
    /**
     * 获取当前半径
     */
    public double getRadius() {
        return radius;
    }
    
    @Override
    public Object getParameter(String key) {
        if (FilletConstants.CONFIG_KEY_RADIUS.equals(key)) {
            return this.radius;
        }
        return null;
    }

    // ====== 状态处理方法 ======
    
    /**
     * 处理选择第一个图形时的鼠标按下
     */
    private ModifyResult handleMouseDown_SelectFirst(Shape shape, Vec2d clickPoint, ModifyToolContext context) {
        line1 = null; // 重置为null，因为现在可能是其他类型的图形
        shape1 = shape; // 保存第一个图形
        clickPoint1 = clickPoint; // 保存点击位置
        currentState = FilletState.SELECT_SECOND_LINE;
        context.setStatusMessage(FilletConstants.STATUS_SELECT_SECOND_LINE);
        LOGGER.debug("选中第一个图形，点击位置: {}", clickPoint);
        return ModifyResult.CONTINUE;
    }
    
    /**
     * 处理选择第二个图形时的鼠标按下
     */
    private ModifyResult handleMouseDown_SelectSecond(Shape shape, Vec2d clickPoint, ModifyToolContext context) {
        boolean sameShapeCornerMode = shape == shape1 && (shape instanceof PolylineShape || shape instanceof Polygon);
        if (shape != shape1 || sameShapeCornerMode) {
            line2 = null; // 重置为null，因为现在可能是其他类型的图形
            shape2 = shape; // 保存第二个图形
            clickPoint2 = clickPoint; // 保存点击位置
            LOGGER.debug("选中第二个图形，点击位置: {}", clickPoint);
            // 验证并生成预览
            updatePreviewWithContext(context);
            if (isReadyToApply()) {
               currentState = FilletState.READY_TO_APPLY;
               context.setStatusMessage(PlotI18n.status(FilletConstants.STATUS_READY_TEMPLATE, radius));
            }
            return ModifyResult.CONTINUE;
        }
        return ModifyResult.IGNORED;
    }
    
    /**
     * 处理选择第一个图形时的鼠标移动
     */
    private ModifyResult handleMouseMove_SelectFirst(Vec2d snappedPoint, ModifyToolContext context) {
        // 在第一个图形选择阶段，高亮鼠标下的图形
        Shape shape = findShapeAtPoint(snappedPoint, context);
        if (isFilletableShape(shape)) {
            updateHighlight(shape);
            context.setStatusMessage("status.plot.fillet.select_first");
            return ModifyResult.CONTINUE;
        } else {
            clearHighlight();
            context.setStatusMessage("status.plot.fillet.select_valid");
            return ModifyResult.IGNORED;
        }
    }
    
    /**
     * 处理选择第二个图形时的鼠标移动
     */
    private ModifyResult handleMouseMove_SelectSecond(Vec2d snappedPoint, ModifyToolContext context) {
        // 在第二个图形选择阶段，高亮鼠标下的图形，并显示预览
        Shape shape = findShapeAtPoint(snappedPoint, context);
        if (isFilletableShape(shape) && shape != shape1) {
            updateHighlight(shape);
            // 临时设置第二个图形进行预览
            updatePreviewWithShapes(shape1, shape, context);
            context.setStatusMessage("status.plot.fillet.select_second");
            return ModifyResult.CONTINUE;
        } else if (shape == shape1) {
            clearHighlight();
            context.setStatusMessage("status.plot.fillet.select_different");
            return ModifyResult.IGNORED;
        } else {
            clearHighlight();
            clearPreview();
            context.setStatusMessage("status.plot.fillet.select_second_valid");
            return ModifyResult.IGNORED;
        }
    }
    
    /**
     * 处理准备应用时的鼠标移动
     */
    private ModifyResult handleMouseMove_Ready(Vec2d snappedPoint, ModifyToolContext context) {
        // 在准备应用阶段，更新预览
        if (shape1 != null && shape2 != null) {
            updatePreviewWithShapes(shape1, shape2, context);
            context.setStatusMessage(PlotI18n.status("status.plot.fillet.confirm_scroll", radius));
            return ModifyResult.CONTINUE;
        }
        return ModifyResult.IGNORED;
    }
    
    /**
     * 处理键盘调整参数
     */
    private ModifyResult handleKeyAdjustment(int keyCode, ModifyToolContext context) {
        double step = getDynamicStep();
        radius += (keyCode == KEY_PLUS ? step : -step);
        radius = Math.max(FilletConstants.MIN_RADIUS, Math.min(FilletConstants.MAX_RADIUS, radius));
        
        if (isReadyToApply()) {
            updatePreviewWithContext(context); // 更新预览
        }
        context.setStatusMessage(PlotI18n.status("status.plot.fillet.radius_value", radius));
        return ModifyResult.CONTINUE;
    }
    
    /**
     * 获取动态调整步长
     */
    private double getDynamicStep() {
        if (radius >= FilletConstants.RADIUS_THRESHOLD_LARGE_STEP) {
            return FilletConstants.KEYBOARD_STEP_LARGE;
        } else {
            return FilletConstants.KEYBOARD_STEP_SMALL;
        }
    }

    /**
     * 获取当前Handler
     */
    private IModifyHandler getCurrentHandler() {
        return this.filletHandler; // 直接返回
    }
    
    /**
     * 内部预览更新 - 不依赖context
     */
    private void updatePreviewInternal() {
        if (!previewEnabled || shape1 == null || shape2 == null) {
            previewShapes = null;
            return;
        }

        IModifyHandler handler = getCurrentHandler();
        if (handler == null) {
            previewShapes = null;
            return;
        }

        FilletParameters params = createModifyParameters();
        IModifyHandler.ValidationResult validation = handler.validateModification(List.of(shape1, shape2), params);

        if (validation.isValid()) {
            this.previewShapes = handler.createPreviewShapes(List.of(shape1, shape2), params);
        } else {
            this.previewShapes = null;
        }
    }
    
    /**
     * 带防抖的预览更新
     */
    private void updatePreviewWithDebounce(ModifyToolContext context) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPreviewUpdate > PREVIEW_DEBOUNCE_MS) {
            updatePreviewWithContext(context);
            lastPreviewUpdate = currentTime;
        }
    }

    private ModifyResult applyFillet(ModifyToolContext context) {
        if (!isReadyToApply()) return ModifyResult.IGNORED;
        
        IModifyHandler handler = getCurrentHandler();
        if (handler == null) {
            context.setStatusMessage("status.plot.fillet.invalid_mode");
            return ModifyResult.IGNORED;
        }

        FilletParameters params = createModifyParameters();
        List<Shape> originalShapes = List.of(shape1, shape2);
        
        // 最终应用时再次验证
        IModifyHandler.ValidationResult validation = handler.validateModification(originalShapes, params);
        if (!validation.isValid()) {
            context.setStatusMessage(validation.getErrorMessage());
            return ModifyResult.IGNORED;
        }
        
        // 创建命令
        ModifyCommand command = handler.createModifyCommand(originalShapes, null, params);
        if (command != null) {
            context.executeModifyCommand(command);
            context.setStatusMessage(PlotI18n.status(FilletConstants.STATUS_COMPLETE_TEMPLATE, radius));
            reset();
            return ModifyResult.COMPLETE;
        } else {
            context.setStatusMessage(FilletConstants.ERROR_COMMAND_CREATION_FAILED);
            return ModifyResult.IGNORED;
        }
    }
    
    /**
     * 创建参数对象 - 简化版本
     */
    private FilletParameters createModifyParameters() {
        return new FilletParameters(radius, clickPoint1, clickPoint2); // 传递半径和点击位置
    }
    
    // ====== 辅助方法 ======
    
    /**
     * 检查图形是否可以进行圆角操作
     */
    private boolean isFilletableShape(Shape shape) {
        return shape instanceof LineShape ||
               shape instanceof RectangleShape ||
               shape instanceof PolylineShape ||
               shape instanceof Polygon ||
               shape instanceof ArcShape ||
               shape instanceof CircleShape ||
               shape instanceof EllipseShape ||
               shape instanceof BezierCurveShape ||
               shape instanceof EllipticalArcShape ||
               shape instanceof FreeDrawPath ||
               shape instanceof SineCurveShape ||
               shape instanceof SpiralShape ||
               shape instanceof CableShape;
    }
    
    /**
     * 在指定位置查找图形
     */
    private Shape findShapeAtPoint(Vec2d point, ModifyToolContext context) {
        try {
            // 使用上下文提供的统一选择检测
            Shape shape = context.findShapeAt(point, FilletConstants.SELECTION_TOLERANCE);
            if (shape != null) {
                LOGGER.debug("找到目标图形: {}", shape.getClass().getSimpleName());
            }
            return shape;
        } catch (Exception e) {
            LOGGER.error("查找图形时发生错误: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 更新高亮显示
     */
    private void updateHighlight(Shape shape) {
        // 清除之前的高亮
        if (highlightedShape != null && highlightedShape != shape1 && highlightedShape != shape2) {
            highlightedShape.setHighlighted(false);
        }
        
        // 设置新的高亮
        if (shape != null && shape != shape1 && shape != shape2) {
            highlightedShape = shape;
            shape.setHighlighted(true);
        } else {
            highlightedShape = null;
        }
    }
    
    /**
     * 清除高亮显示
     */
    private void clearHighlight() {
        if (highlightedShape != null) {
            highlightedShape.setHighlighted(false);
            highlightedShape = null;
        }
    }
    
    /**
     * 清除预览
     */
    private void clearPreview() {
        previewShapes = null;
        // 修复：不清除previewEnabled标志
    }
    
    /**
     * 使用两个图形更新预览
     */
    private void updatePreviewWithShapes(Shape shape1, Shape shape2, ModifyToolContext context) {
        try {
            if (shape1 == null || shape2 == null) {
                clearPreview();
                return;
            }
            
            // 创建参数
            FilletParameters params = createModifyParameters();
            List<Shape> originalShapes = List.of(shape1, shape2);
            
            // 验证操作
            IModifyHandler handler = getCurrentHandler();
            if (handler == null) {
                clearPreview();
                return;
            }
            
            IModifyHandler.ValidationResult validation = handler.validateModification(originalShapes, params);
            if (!validation.isValid()) {
                clearPreview();
                return;
            }
            
            // 创建预览图形
            previewShapes = handler.createPreviewShapes(originalShapes, params);
            previewEnabled = true;
            
            // 启用预览
            context.setPreviewEnabled(true);
            
        } catch (Exception e) {
            LOGGER.error("更新预览失败: {}", e.getMessage(), e);
            clearPreview();
        }
    }
    
    /**
     * 使用上下文更新预览
     */
    private void updatePreviewWithContext(ModifyToolContext context) {
        if (shape1 != null && shape2 != null) {
            updatePreviewWithShapes(shape1, shape2, context);
        }
    }
    
    // 高亮图形引用
    private Shape highlightedShape;
    
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
            if (shape != null && shape.isVisible() && !shape.isDeleted() && isFilletableShape(shape)) {
                // 使用虚线框选择逻辑（总是相交选择）
                if (com.plot.ui.tools.impl.modify.helper.GeometricSelectionHelper.isShapeInRectangleSelection(shape, boxStartPoint, boxCurrentPoint, false)) {
                    boxSelectedShapes.add(shape);
                }
            }
        }
        
        LOGGER.debug("框选临时选择：{} 个图形", boxSelectedShapes.size());
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
    private void renderBoxSelectionPreview(com.plot.core.graphics.DrawContext context) {
        if (!isBoxSelecting || boxStartPoint == null || boxCurrentPoint == null) {
            return;
        }
        
        // 绘制虚线框
        context.drawRect(boxStartPoint, boxCurrentPoint, toColor(ThemeManager.getInstance().getCurrentTheme().text));
    }

    private static java.awt.Color toColor(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return new java.awt.Color(red, green, blue, alpha);
    }
} 