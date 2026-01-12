package com.masterplanner.ui.tools.impl.modify.strategy;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.ui.canvas.CanvasCamera;
import com.masterplanner.ui.tools.impl.modify.helper.FillHandler;
import com.masterplanner.ui.tools.impl.modify.helper.IModifyHandler;
import com.masterplanner.ui.tools.impl.modify.constants.ModifyConstraints;
import com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.List;
import java.util.ArrayList;

/**
 * 完整的填充策略实现
 *
 * <p>这个策略结合了选择工具和填充工具的功能，支持两种填充模式：</p>
 * <ul>
 *   <li><strong>点击填充模式</strong>：直接在点击位置进行填充，自动检测封闭区域</li>
 *   <li><strong>边界填充模式</strong>：先选择边界图形，再点击区域内部确认填充</li>
 *   <li><strong>完整选择功能</strong>：支持点选、框选、多选等所有选择操作</li>
 *   <li><strong>智能模式切换</strong>：根据工具配置自动切换填充模式</li>
 *   <li><strong>连续填充模式</strong>：支持连续多次填充操作</li>
 * </ul>
 *
 * @author MasterPlanner Team
 * @version 2.1 - 完善选择逻辑，参考移动工具、旋转工具和镜像工具
 */
public class FillWithSelectionStrategy extends BaseSelectionStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(FillWithSelectionStrategy.class);

    // 策略模式枚举（参考MoveWithSelectionStrategy和RotateWithSelectionStrategy）
    public enum StrategyMode {
        SELECTION("选择模式", "左键点选/框选图形，右键完成选择"),
        FILL("填充模式", "执行填充操作，右键取消返回选择模式");
        
        private final String displayName;
        private final String description;
        
        StrategyMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    // 填充模式枚举
    public enum FillMode {
        POINT_FILL("点击填充", "点击要填充的区域，自动检测封闭边界"),
        BOUNDARY_FILL("边界填充", "先选择边界，再点击区域内部确认填充");
        
        private final String displayName;
        private final String description;
        
        FillMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    // 常量
    private static final int ESC_KEY = 27;
    private static final int ENTER_KEY = 13;
    private static final int CTRL_KEY = 17;

    // 渲染常量
    private static final Color FILL_PREVIEW_COLOR = new Color(0, 128, 255, 100); // 蓝色半透明
    private static final Color BOUNDARY_HIGHLIGHT_COLOR = new Color(255, 255, 0, 180); // 黄色高亮
    private static final Color POINT_FILL_PREVIEW_COLOR = new Color(0, 255, 0, 150); // 绿色半透明

    // 策略配置
    private StrategyMode currentMode = StrategyMode.SELECTION; // 当前策略模式
    private FillMode currentFillMode = FillMode.POINT_FILL; // 当前填充模式
    private boolean isCtrlPressed = false;
    private boolean multipleMode = false; // 连续填充模式

    // 填充相关状态
    private Vec2d fillPoint;
    private float fillOpacity = 1.0f;
    private FillHandler fillHandler;
    private ModifyCommand pendingCommand;
    private List<Shape> selectedBoundaryShapes;
    private ModifyParameters fillParameters;
    private ModifyConstraints fillConstraints;

    /**
     * 依赖注入构造函数
     * 
     * @param appState 应用状态实例，用于创建填充处理器
     */
    public FillWithSelectionStrategy(AppState appState) {
        // 使用依赖注入的AppState初始化填充处理器
        this.fillHandler = new FillHandler(appState);
        this.fillParameters = new ModifyParameters();
        this.fillConstraints = new ModifyConstraints();
        this.selectedBoundaryShapes = new ArrayList<>();
        
        // 确保选择功能正确初始化
        LOGGER.debug("FillWithSelectionStrategy 初始化完成");
        reset();
    }

    /**
     * 默认构造函数（兼容性）
     * @deprecated 使用依赖注入构造函数 {@link #FillWithSelectionStrategy(AppState)}
     */
    @Deprecated
    public FillWithSelectionStrategy() {
        // 兼容性构造函数，使用单例模式
        this(AppState.getInstance());
    }

    /**
     * 设置填充模式
     */
    public void setFillMode(FillMode mode) {
        if (this.currentFillMode != mode) {
            reset(); // 切换模式时重置状态
            this.currentFillMode = mode;
            LOGGER.info("填充模式已切换为: {}", mode.getDisplayName());
        }
    }

    /**
     * 设置连续填充模式
     */
    public void setMultipleMode(boolean enabled) {
        this.multipleMode = enabled;
        LOGGER.debug("连续填充模式设置为: {}", enabled);
    }

    /**
     * 设置填充透明度
     */
    public void setFillOpacity(float opacity) {
        this.fillOpacity = Math.max(0.0f, Math.min(1.0f, opacity));
        LOGGER.debug("填充透明度设置为: {}", this.fillOpacity);
    }

    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        LOGGER.debug("FillWithSelectionStrategy 鼠标按下: button={}, currentMode={}, currentFillMode={}", 
                    button, currentMode, currentFillMode);
        
        if (button == MOUSE_RIGHT) {
            return handleRightMouseDown(pos, context);
        } else if (button == MOUSE_LEFT) {
            return handleLeftMouseDown(pos, context);
        }
        return ModifyResult.IGNORED;
    }

    /**
     * 处理右键按下
     * 用于在选择模式和填充模式之间切换（参考MoveWithSelectionStrategy和RotateWithSelectionStrategy）
     */
    private ModifyResult handleRightMouseDown(Vec2d pos, ModifyToolContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            // 在选择模式下右键：完成选择，切换到填充模式
            if (!selectedShapeIds.isEmpty()) {
                currentMode = StrategyMode.FILL;
                selectedBoundaryShapes = getSelectedShapesFromIds(context);
                
                if (currentFillMode == FillMode.BOUNDARY_FILL) {
                    context.setStatusMessage("已选择 " + selectedShapeIds.size() + " 个边界图形，点击区域内部确认填充");
                } else {
                    context.setStatusMessage("已选择 " + selectedShapeIds.size() + " 个图形，点击要填充的区域");
                }
                
                LOGGER.info("切换到填充模式，已选择 {} 个图形", selectedShapeIds.size());
                return ModifyResult.CONTINUE;
            } else {
                context.setStatusMessage("请先选择要填充的图形");
                return ModifyResult.NEED_SELECTION;
            }
        } else {
            // 在填充模式下右键：取消填充，返回选择模式
            resetFillState();
            currentMode = StrategyMode.SELECTION;
            context.setStatusMessage("填充已取消，请重新选择图形");
            LOGGER.info("从填充模式返回选择模式");
            return ModifyResult.CANCEL;
        }
    }

    /**
     * 处理左键按下
     * 根据当前模式执行不同操作（参考MoveWithSelectionStrategy和RotateWithSelectionStrategy）
     */
    private ModifyResult handleLeftMouseDown(Vec2d pos, ModifyToolContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            // 选择模式：使用基础选择逻辑
            return super.handleSelectionMouseDown(pos, context);
        } else {
            // 填充模式：执行填充操作
            return handleFillMouseDown(pos, context);
        }
    }

    /**
     * 处理填充模式下的左键按下
     */
    private ModifyResult handleFillMouseDown(Vec2d pos, ModifyToolContext context) {
        if (currentFillMode == FillMode.POINT_FILL) {
            // 点击填充模式：直接在点击位置进行填充
            LOGGER.debug("点击填充模式：直接执行填充");
            Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            return performPointFill(snappedPoint, context);
        } else {
            // 边界填充模式：在区域内点击确认填充
            LOGGER.debug("边界填充模式：执行填充");
            Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            return performBoundaryFill(snappedPoint, context);
        }
    }

    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            return super.handleSelectionMouseMove(pos, context);
        } else {
            return handleFillMouseMove(pos, context);
        }
    }

    /**
     * 处理填充模式下的鼠标移动
     */
    private ModifyResult handleFillMouseMove(Vec2d pos, ModifyToolContext context) {
        // 更新状态消息
        if (currentFillMode == FillMode.BOUNDARY_FILL) {
            context.setStatusMessage("点击区域内部确认填充");
        } else {
            context.setStatusMessage("点击要填充的区域");
        }
        
        return ModifyResult.CONTINUE;
    }

    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        LOGGER.debug("FillWithSelectionStrategy 鼠标释放: button={}, isSelecting={}, currentMode={}", 
                    button, isSelecting, currentMode);
        
        if (button != MOUSE_LEFT) {
            return ModifyResult.IGNORED;
        }

        if (currentMode == StrategyMode.SELECTION) {
            LOGGER.debug("选择模式：处理选择鼠标释放");
            ModifyResult result = super.handleSelectionMouseUp(pos, context);
            LOGGER.debug("选择完成，结果: {}, 选中图形数: {}", result, selectedShapeIds.size());
            return result;
        }
        
        return ModifyResult.CONTINUE;
    }

    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        switch (keyCode) {
            case CTRL_KEY -> {
                isCtrlPressed = true;
                return ModifyResult.CONTINUE;
            }
            case ENTER_KEY -> {
                // Enter键确认选择（兼容性功能）
                if (currentMode == StrategyMode.SELECTION && hasSelection()) {
                    currentMode = StrategyMode.FILL;
                    selectedBoundaryShapes = getSelectedShapesFromIds(context);
                    if (currentFillMode == FillMode.BOUNDARY_FILL) {
                        context.setStatusMessage("已选择 " + selectedShapeIds.size() + " 个边界图形，点击区域内部确认填充");
                    } else {
                        context.setStatusMessage("已选择 " + selectedShapeIds.size() + " 个图形，点击要填充的区域");
                    }
                    LOGGER.info("按Enter键切换到填充模式，已选择 {} 个图形", selectedShapeIds.size());
                    return ModifyResult.CONTINUE;
                }
                return ModifyResult.IGNORED;
            }
            case ESC_KEY -> {
                reset();
                context.setStatusMessage("操作已取消");
                return ModifyResult.CANCEL;
            }
            default -> {
                return ModifyResult.IGNORED;
            }
        }
    }

    @Override
    public ModifyResult onKeyUp(int keyCode, ModifyToolContext context) {
        if (keyCode == CTRL_KEY) {
            isCtrlPressed = false;
            return ModifyResult.CONTINUE;
        }
        return ModifyResult.IGNORED;
    }

    /**
     * 执行边界填充操作
     */
    private ModifyResult performBoundaryFill(Vec2d point, ModifyToolContext context) {
        try {
            // currentState = StrategyState.FILLING; // Removed as per new logic
            fillPoint = point;
            
            LOGGER.debug("执行边界填充，填充点: {}", point);

            if (selectedBoundaryShapes == null || selectedBoundaryShapes.isEmpty()) {
                context.setStatusMessage("没有选中的边界图形可以填充");
                LOGGER.warn("边界填充：没有选中的边界图形");
                // currentState = StrategyState.READY_TO_FILL; // Removed as per new logic
                return ModifyResult.NEED_SELECTION;
            }

            // 创建填充参数
            fillParameters = FillHandler.createFillParameters(fillPoint, fillOpacity);
            fillConstraints = new ModifyConstraints();

            // 获取要检查的图形列表 - 边界填充模式下使用选中的图形
            List<Shape> shapesInput = selectedBoundaryShapes;
            LOGGER.debug("边界填充模式，使用 {} 个选中的图形进行边界检测", shapesInput.size());

            // 使用handler执行填充操作
            IModifyHandler.ModifyResult result = fillHandler.performModification(shapesInput, fillParameters);

            if (!result.isSuccess()) {
                context.setStatusMessage("边界填充失败: " + result.getMessage());
                LOGGER.warn("边界填充失败: {}", result.getMessage());
                // currentState = StrategyState.READY_TO_FILL; // Removed as per new logic
                return ModifyResult.IGNORED;
            }

            // 获取修改命令
            pendingCommand = result.getCommand();
            if (pendingCommand == null) {
                context.setStatusMessage("创建边界填充命令失败");
                LOGGER.error("边界填充：创建填充命令失败");
                // currentState = StrategyState.READY_TO_FILL; // Removed as per new logic
                return ModifyResult.IGNORED;
            }

            // 执行填充命令
            context.executeModifyCommand(pendingCommand);
            LOGGER.debug("边界填充：已执行填充命令");

            context.setStatusMessage("边界填充完成");
            LOGGER.info("边界填充成功完成，替换了 {} 个图形", shapesInput.size());

            if (multipleMode) {
                // 连续模式：维持策略状态，继续下一次
                prepareForNextOperation();
                return ModifyResult.CONTINUE;
            } else {
                // 单次模式：重置状态
                resetFillState();
                // currentState = StrategyState.SELECTING_BOUNDARY; // Removed as per new logic
                currentMode = StrategyMode.SELECTION; // Reset to selection mode
                return ModifyResult.COMPLETE;
            }

        } catch (Exception e) {
            LOGGER.error("边界填充操作失败: {}", e.getMessage(), e);
            context.setStatusMessage("边界填充失败: " + e.getMessage());
            // currentState = StrategyState.READY_TO_FILL; // Removed as per new logic
            return ModifyResult.IGNORED;
        }
    }

    /**
     * 执行点击填充操作
     */
    private ModifyResult performPointFill(Vec2d point, ModifyToolContext context) {
        try {
            LOGGER.info("=== 开始执行点击填充操作 ===");
            LOGGER.info("填充点: {}", point);
            
            // currentState = StrategyState.FILLING; // Removed as per new logic
            fillPoint = point;

            // 创建填充参数
            fillParameters = FillHandler.createFillParameters(fillPoint, fillOpacity);
            fillConstraints = new ModifyConstraints();
            LOGGER.info("填充参数创建完成，透明度: {}", fillOpacity);

            // 获取要检查的图形列表 - 点击填充模式下使用所有图形
            List<Shape> shapesInput = context.getAllShapesInActiveLayer();
            LOGGER.info("点击填充模式，使用 {} 个图形进行边界检测", shapesInput.size());
            
            // 详细记录图形信息
            for (int i = 0; i < Math.min(shapesInput.size(), 5); i++) { // 只记录前5个
                Shape shape = shapesInput.get(i);
                LOGGER.info("图形 {}: 类型={}, ID={}", i, shape.getClass().getSimpleName(), shape.getId());
            }
            if (shapesInput.size() > 5) {
                LOGGER.info("... 还有 {} 个图形", shapesInput.size() - 5);
            }

            // 使用handler执行填充操作
            LOGGER.info("开始执行填充操作...");
            IModifyHandler.ModifyResult result = fillHandler.performModification(shapesInput, fillParameters);
            LOGGER.info("填充操作执行完成，结果: success={}, message={}", result.isSuccess(), result.getMessage());

            if (!result.isSuccess()) {
                context.setStatusMessage("填充失败: " + result.getMessage());
                LOGGER.warn("点击填充失败: {}", result.getMessage());
                // currentState = StrategyState.SELECTING_BOUNDARY; // Removed as per new logic
                return ModifyResult.IGNORED;
            }

            // 获取修改命令
            pendingCommand = result.getCommand();
            if (pendingCommand == null) {
                context.setStatusMessage("创建填充命令失败");
                LOGGER.error("点击填充：创建填充命令失败");
                // currentState = StrategyState.SELECTING_BOUNDARY; // Removed as per new logic
                return ModifyResult.IGNORED;
            }
            LOGGER.info("填充命令创建成功: {}", pendingCommand.getClass().getSimpleName());

            // 执行填充命令
            LOGGER.info("开始执行填充命令...");
            context.executeModifyCommand(pendingCommand);
            LOGGER.info("填充命令执行完成");

            context.setStatusMessage("点击填充完成");
            LOGGER.info("=== 点击填充操作成功完成 ===");
            LOGGER.info("修改了 {} 个图形", shapesInput.size());

            if (multipleMode) {
                // 连续模式：维持策略状态，继续下一次
                prepareForNextOperation();
                return ModifyResult.CONTINUE;
            } else {
                // 单次模式：重置状态
                resetFillState();
                // currentState = StrategyState.SELECTING_BOUNDARY; // Removed as per new logic
                currentMode = StrategyMode.SELECTION; // Reset to selection mode
                return ModifyResult.COMPLETE;
            }

        } catch (Exception e) {
            LOGGER.error("点击填充操作失败: {}", e.getMessage(), e);
            context.setStatusMessage("点击填充失败: " + e.getMessage());
            // currentState = StrategyState.SELECTING_BOUNDARY; // Removed as per new logic
            return ModifyResult.IGNORED;
        }
    }

    /**
     * 准备下一次操作
     */
    private void prepareForNextOperation() {
        // currentState = StrategyState.SELECTING_BOUNDARY; // Removed as per new logic
        fillPoint = null;
        pendingCommand = null;
        selectedBoundaryShapes = null;
        super.resetSelectionState();
    }

    /**
     * 重置填充状态
     */
    private void resetFillState() {
        // currentState = StrategyState.SELECTING_BOUNDARY; // Removed as per new logic
        fillPoint = null;
        pendingCommand = null;
        selectedBoundaryShapes = null;
        if (fillParameters != null) {
            fillParameters.clear();
        }
    }

    /**
     * 从选中的ID获取图形列表（重写父类方法以添加日志）
     */
    @Override
    protected List<Shape> getSelectedShapesFromIds(ModifyToolContext context) {
        List<Shape> shapes = super.getSelectedShapesFromIds(context);
        LOGGER.debug("从选中ID获取到 {} 个图形", shapes.size());
        return shapes;
    }

    @Override
    public ModifyCommand getModifyCommand() {
        return pendingCommand;
    }

    @Override
    public void reset() {
        LOGGER.debug("FillWithSelectionStrategy 重置");

        // 重置选择状态 - 使用父类方法
        super.resetSelectionState();

        // 重置填充状态
        resetFillState();

        // 重置修饰键状态
        isCtrlPressed = false;

        // 重置选择模式状态
        currentMode = StrategyMode.SELECTION; // Reset to selection mode
        
        LOGGER.debug("FillWithSelectionStrategy 重置完成，当前模式: {}, 选择状态: {}", 
                    currentMode, selectedShapeIds.size());
    }

    @Override
    public String getStrategyName() {
        return "完整填充策略";
    }

    @Override
    public String getStrategyDescription() {
        return currentFillMode.getDescription();
    }

    @Override
    public boolean requiresSelection() {
        return false; // 这个策略自己处理选择
    }

    @Override
    public int getMinimumSelectionCount() {
        return 1; // 两种模式都需要至少选择1个图形
    }

    @Override
    public int getMaximumSelectionCount() {
        return -1; // 无限制
    }

    /**
     * 获取当前模式
     */
    public StrategyMode getCurrentMode() {
        return currentMode;
    }

    /**
     * 渲染预览
     */
    public void renderPreview(DrawContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            // 选择状态：显示选择框预览
            super.renderSelectionPreview(context);
        } else {
            // 填充状态：显示填充预览
            renderFillPreview(context);
        }
    }

    /**
     * 渲染预览（ImGui版本）
     */
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (currentMode == StrategyMode.SELECTION) {
            // 选择状态：显示选择框预览
            super.renderSelectionPreviewImGui(drawList, camera);
        } else {
            // 填充状态：显示填充预览
            renderFillPreviewImGui(drawList, camera);
        }
    }

    /**
     * 渲染填充预览
     */
    private void renderFillPreview(DrawContext context) {
        // 高亮显示选中的图形
        if (selectedBoundaryShapes != null && !selectedBoundaryShapes.isEmpty()) {
            for (Shape shape : selectedBoundaryShapes) {
                // 临时改变图形颜色进行高亮
                int originalColor = shape.getStyle().getFillColor();
                shape.getStyle().setFillColor(BOUNDARY_HIGHLIGHT_COLOR.getRGB());
                shape.render(context);
                shape.getStyle().setFillColor(originalColor);
            }
        }

        // 显示填充点
        if (fillPoint != null) {
            Color previewColor = currentFillMode == FillMode.POINT_FILL ? 
                POINT_FILL_PREVIEW_COLOR : FILL_PREVIEW_COLOR;
            context.drawCircle(fillPoint, 5.0f, previewColor);
        }
    }

    /**
     * 渲染填充预览（ImGui版本）
     */
    private void renderFillPreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        // 高亮显示选中的图形
        if (selectedBoundaryShapes != null && !selectedBoundaryShapes.isEmpty()) {
            for (Shape shape : selectedBoundaryShapes) {
                // 获取图形的实际顶点进行精确渲染
                List<Vec2d> vertices = getShapeVerticesForRendering(shape);
                if (vertices != null && !vertices.isEmpty()) {
                    renderShapeOutlineImGui(drawList, camera, vertices);
                } else {
                    // 回退到包围盒渲染
                    renderBoundingBoxImGui(drawList, camera, shape);
                }
            }
        }

        // 显示填充点
        if (fillPoint != null) {
            Vec2d screenPos = camera.worldToScreen(fillPoint);
            Color previewColor = currentFillMode == FillMode.POINT_FILL ? 
                POINT_FILL_PREVIEW_COLOR : FILL_PREVIEW_COLOR;
            int fillColor = imgui.ImGui.getColorU32(
                previewColor.getRed() / 255.0f,
                previewColor.getGreen() / 255.0f,
                previewColor.getBlue() / 255.0f,
                previewColor.getAlpha() / 255.0f
            );
            
            drawList.addCircleFilled(
                (float)screenPos.x,
                (float)screenPos.y,
                5.0f,
                fillColor
            );
        }
    }

    /**
     * 获取状态消息
     */
    public String getStatusMessage() {
        if (currentMode == StrategyMode.SELECTION) {
            if (hasSelection()) {
                return String.format("已选择 %d 个图形，右键或按Enter确认", getSelectedCount());
            } else {
                return "请选择图形";
            }
        } else { // currentMode == StrategyMode.FILL
            if (currentFillMode == FillMode.BOUNDARY_FILL) {
                return "点击区域内部确认填充";
            } else {
                return "点击要填充的区域";
            }
        }
    }

    /**
     * 更新配置
     */
    public void updateConfig(String key, String value) {
        LOGGER.debug("更新配置: {} = {}", key, value);
        
        switch (key) {
            case "mode" -> {
                if ("POINT_FILL".equals(value)) {
                    setFillMode(FillMode.POINT_FILL);
                } else if ("BOUNDARY_FILL".equals(value)) {
                    setFillMode(FillMode.BOUNDARY_FILL);
                }
            }
            case "fillOpacity" -> {
                try {
                    setFillOpacity(Float.parseFloat(value));
                } catch (NumberFormatException e) {
                    LOGGER.warn("无效的透明度值: {}", value);
                }
            }
            case "multipleMode" -> setMultipleMode("true".equalsIgnoreCase(value));
            default -> LOGGER.debug("未知的配置项: {} = {}", key, value);
        }
    }

    // ==================== 精确渲染相关方法 ====================

    /**
     * 获取图形顶点用于渲染
     * 
     * @param shape 要渲染的图形
     * @return 图形的顶点列表，如果获取失败返回null
     */
    private List<Vec2d> getShapeVerticesForRendering(Shape shape) {
        try {
            if (shape instanceof com.masterplanner.core.geometry.shapes.RectangleShape rect) {
                // 获取矩形的实际顶点（考虑旋转）
                return getRectangleVertices(rect);
            }
            if (shape instanceof com.masterplanner.core.geometry.shapes.PolylineShape poly) {
                // 获取多段线的顶点
                return getPolylineVertices(poly);
            }
            if (shape instanceof com.masterplanner.core.geometry.shapes.CircleShape circle) {
                // 获取圆形的近似顶点
                return getCircleVertices(circle);
            }
            if (shape instanceof com.masterplanner.core.geometry.shapes.EllipseShape ellipse) {
                // 获取椭圆的近似顶点
                return getEllipseVertices(ellipse);
            }
            if (shape instanceof com.masterplanner.core.geometry.shapes.LineShape line) {
                // 获取线段的端点
                return getLineVertices(line);
            }
        } catch (Exception e) {
            LOGGER.debug("获取图形顶点失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取矩形顶点（考虑旋转）
     */
    private List<Vec2d> getRectangleVertices(com.masterplanner.core.geometry.shapes.RectangleShape rect) {
        try {
            com.masterplanner.core.geometry.BoundingBox bounds = rect.getBoundingBox();
            if (bounds != null) {
                // 获取矩形的四个角点
                List<Vec2d> vertices = new ArrayList<>();
                vertices.add(new Vec2d(bounds.getMinX(), bounds.getMinY()));
                vertices.add(new Vec2d(bounds.getMaxX(), bounds.getMinY()));
                vertices.add(new Vec2d(bounds.getMaxX(), bounds.getMaxY()));
                vertices.add(new Vec2d(bounds.getMinX(), bounds.getMaxY()));
                
                // 如果矩形有旋转，应用变换
                if (rect.getTransform() != null && !isIdentityMatrix(rect.getTransform())) {
                    List<Vec2d> transformedVertices = new ArrayList<>();
                    for (Vec2d vertex : vertices) {
                        transformedVertices.add(rect.getTransform().transform(vertex));
                    }
                    return transformedVertices;
                }
                return vertices;
            }
        } catch (Exception e) {
            LOGGER.debug("获取矩形顶点失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取多段线顶点
     */
    private List<Vec2d> getPolylineVertices(com.masterplanner.core.geometry.shapes.PolylineShape poly) {
        try {
            List<Vec2d> localPoints = poly.getPoints();
            if (localPoints != null && !localPoints.isEmpty()) {
                List<Vec2d> worldPoints = new ArrayList<>();
                for (Vec2d localPoint : localPoints) {
                    worldPoints.add(poly.getTransform().transform(localPoint));
                }
                return worldPoints;
            }
        } catch (Exception e) {
            LOGGER.debug("获取多段线顶点失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取圆形顶点（近似为多边形）
     */
    private List<Vec2d> getCircleVertices(com.masterplanner.core.geometry.shapes.CircleShape circle) {
        try {
            Vec2d center = circle.getCenter();
            double radius = circle.getRadius();
            
            if (center != null && radius > 0) {
                List<Vec2d> vertices = new ArrayList<>();
                int segments = 32; // 32段近似圆形
                
                for (int i = 0; i < segments; i++) {
                    double angle = 2 * Math.PI * i / segments;
                    double x = center.x + radius * Math.cos(angle);
                    double y = center.y + radius * Math.sin(angle);
                    vertices.add(new Vec2d(x, y));
                }
                return vertices;
            }
        } catch (Exception e) {
            LOGGER.debug("获取圆形顶点失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取椭圆顶点（近似为多边形）
     */
    private List<Vec2d> getEllipseVertices(com.masterplanner.core.geometry.shapes.EllipseShape ellipse) {
        try {
            Vec2d center = ellipse.getCenter();
            double radiusX = ellipse.getRadiusX();
            double radiusY = ellipse.getRadiusY();
            
            if (center != null && radiusX > 0 && radiusY > 0) {
                List<Vec2d> vertices = new ArrayList<>();
                int segments = 32; // 32段近似椭圆
                
                for (int i = 0; i < segments; i++) {
                    double angle = 2 * Math.PI * i / segments;
                    double x = center.x + radiusX * Math.cos(angle);
                    double y = center.y + radiusY * Math.sin(angle);
                    Vec2d localPoint = new Vec2d(x, y);
                    vertices.add(ellipse.getTransform().transform(localPoint));
                }
                return vertices;
            }
        } catch (Exception e) {
            LOGGER.debug("获取椭圆顶点失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取线段顶点
     */
    private List<Vec2d> getLineVertices(com.masterplanner.core.geometry.shapes.LineShape line) {
        try {
            List<Vec2d> vertices = new ArrayList<>();
            vertices.add(line.getStart());
            vertices.add(line.getEnd());
            return vertices;
        } catch (Exception e) {
            LOGGER.debug("获取线段顶点失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 在ImGui中渲染图形轮廓
     * 
     * @param drawList ImGui绘制列表
     * @param camera 相机
     * @param vertices 图形顶点列表
     */
    private void renderShapeOutlineImGui(ImDrawList drawList, CanvasCamera camera, List<Vec2d> vertices) {
        if (vertices == null || vertices.size() < 2) {
            return;
        }

        try {
            int highlightColor = imgui.ImGui.getColorU32(
                BOUNDARY_HIGHLIGHT_COLOR.getRed() / 255.0f,
                BOUNDARY_HIGHLIGHT_COLOR.getGreen() / 255.0f,
                BOUNDARY_HIGHLIGHT_COLOR.getBlue() / 255.0f,
                BOUNDARY_HIGHLIGHT_COLOR.getAlpha() / 255.0f
            );

            // 转换所有顶点到屏幕坐标
            List<Vec2d> screenVertices = new ArrayList<>();
            for (Vec2d worldVertex : vertices) {
                screenVertices.add(camera.worldToScreen(worldVertex));
            }

            // 绘制图形轮廓
            if (screenVertices.size() == 2) {
                // 线段
                Vec2d start = screenVertices.get(0);
                Vec2d end = screenVertices.get(1);
                drawList.addLine(
                    (float)start.x, (float)start.y,
                    (float)end.x, (float)end.y,
                    highlightColor,
                    2.0f
                );
            } else if (screenVertices.size() > 2) {
                // 多边形或圆形
                for (int i = 0; i < screenVertices.size(); i++) {
                    Vec2d current = screenVertices.get(i);
                    Vec2d next = screenVertices.get((i + 1) % screenVertices.size());
                    
                    drawList.addLine(
                        (float)current.x, (float)current.y,
                        (float)next.x, (float)next.y,
                        highlightColor,
                        2.0f
                    );
                }
            }
        } catch (Exception e) {
            LOGGER.debug("渲染图形轮廓失败: {}", e.getMessage());
        }
    }

    /**
     * 在ImGui中渲染包围盒（回退方案）
     * 
     * @param drawList ImGui绘制列表
     * @param camera 相机
     * @param shape 图形
     */
    private void renderBoundingBoxImGui(ImDrawList drawList, CanvasCamera camera, Shape shape) {
        try {
            com.masterplanner.core.geometry.BoundingBox bounds = shape.getBoundingBox();
            if (bounds != null) {
                Vec2d center = bounds.getCenter();
                double width = bounds.getWidth();
                double height = bounds.getHeight();
                
                int highlightColor = imgui.ImGui.getColorU32(
                    BOUNDARY_HIGHLIGHT_COLOR.getRed() / 255.0f,
                    BOUNDARY_HIGHLIGHT_COLOR.getGreen() / 255.0f,
                    BOUNDARY_HIGHLIGHT_COLOR.getBlue() / 255.0f,
                    BOUNDARY_HIGHLIGHT_COLOR.getAlpha() / 255.0f
                );
                
                Vec2d screenPos = camera.worldToScreen(center);
                Vec2d screenSize = camera.worldToScreen(new Vec2d(width, height));
                
                drawList.addRect(
                    (float)(screenPos.x - screenSize.x / 2),
                    (float)(screenPos.y - screenSize.y / 2),
                    (float)(screenPos.x + screenSize.x / 2),
                    (float)(screenPos.y + screenSize.y / 2),
                    highlightColor,
                    0.0f,
                    0,
                    2.0f
                );
            }
        } catch (Exception e) {
            LOGGER.debug("渲染包围盒失败: {}", e.getMessage());
        }
    }

    /**
     * 检查矩阵是否为单位矩阵
     * 
     * @param matrix 要检查的矩阵
     * @return 如果是单位矩阵返回true，否则返回false
     */
    private boolean isIdentityMatrix(com.masterplanner.api.geometry.Matrix3d matrix) {
        if (matrix == null) {
            return false;
        }
        
        try {
            // 检查对角线元素是否为1，其他元素是否为0
            return Math.abs(matrix.get(0, 0) - 1.0) < 1e-10 &&
                   Math.abs(matrix.get(1, 1) - 1.0) < 1e-10 &&
                   Math.abs(matrix.get(2, 2) - 1.0) < 1e-10 &&
                   Math.abs(matrix.get(0, 1)) < 1e-10 &&
                   Math.abs(matrix.get(0, 2)) < 1e-10 &&
                   Math.abs(matrix.get(1, 0)) < 1e-10 &&
                   Math.abs(matrix.get(1, 2)) < 1e-10 &&
                   Math.abs(matrix.get(2, 0)) < 1e-10 &&
                   Math.abs(matrix.get(2, 1)) < 1e-10;
        } catch (Exception e) {
            LOGGER.debug("检查矩阵是否为单位矩阵失败: {}", e.getMessage());
            return false;
        }
    }
}