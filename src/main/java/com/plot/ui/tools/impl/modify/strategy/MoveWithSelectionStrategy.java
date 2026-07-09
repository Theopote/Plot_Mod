package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.graphics.DrawContext;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.theme.UITheme;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.tools.impl.modify.helper.IModifyHandler;
import com.plot.ui.tools.impl.modify.helper.MoveHandler;
import com.plot.ui.tools.impl.modify.constants.ModifyConstraints;
import com.plot.ui.tools.impl.modify.dto.ModifyParameters;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.utils.ExceptionDebug;

import java.awt.Color;
import java.util.List;
import com.plot.utils.PlotI18n;

/**
 * 移动工具与选择功能结合策略
 *
 * <p>这个策略结合了选择工具和移动工具的功能：</p>
 * <ul>
 *   <li><strong>选择模式</strong>：左键点选/框选图形，右键完成选择</li>
 *   <li><strong>移动模式</strong>：右键后进入移动模式，支持拖拽和点-移动-点</li>
 *   <li><strong>智能切换</strong>：根据右键操作在选择和移动间切换</li>
 * </ul>
 *
 * @author Plot Team
 * @version 1.0 - 移动选择结合策略
 */
public class MoveWithSelectionStrategy extends BaseSelectionStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoveWithSelectionStrategy.class);

    // 策略模式枚举
    public enum StrategyMode {
        SELECTION("strategy.plot.mode.selection", "strategy.plot.mode.selection.desc"),
        MOVE("strategy.plot.mode.move", "strategy.plot.mode.move.desc");

        private final String nameKey;
        private final String descKey;

        StrategyMode(String nameKey, String descKey) {
            this.nameKey = nameKey;
            this.descKey = descKey;
        }

        public String getDisplayName() { return PlotI18n.modeLabel(nameKey); }
        public String getDescription() { return PlotI18n.modeLabel(descKey); }
    }

    // 常量
    private static final double MIN_MOVE_DISTANCE = 0.1;
    private static final int ESC_KEY = 27;
    private static final int SHIFT_KEY = 16;

    // 渲染常量
    private static final int PREVIEW_ALPHA = 180;

    // 策略状态
    private StrategyMode currentMode = StrategyMode.SELECTION;
    private boolean isMoving = false;
    private boolean hasBasePoint = false; // 是否已设置基点
    private boolean isShiftPressed = false;
    private boolean isCtrlPressed = false;

    // 移动坐标状态
    private Vec2d moveStartPoint;
    private Vec2d moveCurrentPoint;
    // 约束后用于渲染的终点（例如正交约束时）
    private Vec2d constrainedEndPoint;

    // 移动处理器和参数
    private MoveHandler moveHandler;
    private ModifyParameters moveParameters;
    private ModifyConstraints moveConstraints;
    private List<Shape> previewShapes;
    private ModifyCommand pendingCommand;

    /**
     * 默认构造函数
     */
    public MoveWithSelectionStrategy() {
        this.moveParameters = new ModifyParameters();
        this.moveConstraints = new ModifyConstraints();
        // 默认启用正交约束
        this.moveConstraints.setOrthogonalConstraintEnabled(false);
    }

    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        if (button == MOUSE_RIGHT) {
            return handleRightMouseDown(pos, context);
        } else if (button == MOUSE_LEFT) {
            return handleLeftMouseDown(pos, context);
        }
        return ModifyResult.IGNORED;
    }

    /**
     * 处理右键按下
     * 用于在选择模式和移动模式之间切换
     */
    private ModifyResult handleRightMouseDown(Vec2d pos, ModifyToolContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            // 在选择模式下右键：完成选择，切换到移动模式
            if (!selectedShapeIds.isEmpty()) {
                currentMode = StrategyMode.MOVE;
                selectedShapes = getSelectedShapesFromIds(context);
                context.setStatusMessage(PlotI18n.status("status.plot.move.initial_base", selectedShapeIds.size()));
                LOGGER.info("切换到移动模式，已选择 {} 个图形", selectedShapeIds.size());
                return ModifyResult.CONTINUE;
            } else {
                context.setStatusMessage("status.plot.move.initial_select");
                return ModifyResult.NEED_SELECTION;
            }
        } else {
            // 在移动模式下右键：取消移动，返回选择模式
            resetMoveState();
            currentMode = StrategyMode.SELECTION;
            context.setStatusMessage("status.plot.move.cancelled");
            LOGGER.info("从移动模式返回选择模式");
            return ModifyResult.CANCEL;
        }
    }

    /**
     * 处理左键按下
     * 根据当前模式执行不同操作
     */
    private ModifyResult handleLeftMouseDown(Vec2d pos, ModifyToolContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            return super.handleSelectionMouseDown(pos, context);
        } else {
            return handleMoveMouseDown(pos, context);
        }
    }

    /**
     * 处理移动模式下的左键按下
     */
    private ModifyResult handleMoveMouseDown(Vec2d pos, ModifyToolContext context) {
        if (selectedShapes == null || selectedShapes.isEmpty()) {
            context.setStatusMessage("status.plot.move.no_selection");
            return ModifyResult.NEED_SELECTION;
        }

        // 初始化移动处理器
        if (moveHandler == null) {
            AppState appState = AppState.getInstance();
            if (appState == null) {
                context.setStatusMessage("status.plot.common.no_app_state");
                return ModifyResult.CANCEL;
            }
            moveHandler = new MoveHandler(appState);
        }

        Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

        // 同步修饰键状态
        try {
            isShiftPressed = imgui.ImGui.getIO().getKeyShift();
            isCtrlPressed = imgui.ImGui.getIO().getKeyCtrl();
        } catch (Exception e) { ExceptionDebug.log("MoveWithSelectionStrategy: read modifier key state", e); }

        if (!hasBasePoint) {
            // 第一次点击：设置基点
            moveStartPoint = snappedPoint;
            moveCurrentPoint = snappedPoint;
            hasBasePoint = true;

            // 设置移动参数
            moveParameters.setStartPoint(moveStartPoint);
            moveParameters.setEndPoint(moveCurrentPoint);

            context.setStatusMessage("status.plot.move.base_set");
            LOGGER.debug("设置移动基点: {}", moveStartPoint);
            return ModifyResult.CONTINUE;
        } else {
            // 第二次点击：设置目标点，完成移动
            moveCurrentPoint = snappedPoint;
            moveParameters.setEndPoint(moveCurrentPoint);
            isMoving = true; // 标记为移动状态

            // 直接完成移动操作（参考MoveStrategy的实现）
            return completeMove(snappedPoint, context);
        }
    }

    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            return super.handleSelectionMouseMove(pos, context);
        } else {
            return handleMoveMouseMove(pos, context);
        }
    }

    /**
     * 处理移动模式下的鼠标移动
     */
    private ModifyResult handleMoveMouseMove(Vec2d pos, ModifyToolContext context) {
        if (!hasBasePoint) {
            return ModifyResult.IGNORED;
        }

        try {
            // 获取吸附后的点
            moveCurrentPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

            // 更新移动参数
            moveParameters.setStartPoint(moveStartPoint);
            moveParameters.setEndPoint(moveCurrentPoint);

            // 计算移动向量
            Vec2d moveVector = moveCurrentPoint.subtract(moveStartPoint);

            // 应用约束
            updateConstraints();
            IModifyHandler.ModifyParameters constrainedParameters = moveHandler.applyConstraints(moveParameters, moveConstraints);

            // 保存约束后的终点用于渲染参考线（确保虚线与预览图形一致）
            if (constrainedParameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters cp) {
                constrainedEndPoint = cp.getEndPoint();
            } else {
                constrainedEndPoint = moveCurrentPoint;
            }

            // 创建预览图形
            previewShapes = moveHandler.createPreviewShapes(selectedShapes, constrainedParameters);

            // 更新状态消息
            String statusMessage = PlotI18n.status("status.plot.move.distance", moveVector.length());
            if (isShiftPressed) {
                statusMessage += " (正交约束)";
            }
            if (isCtrlPressed) {
                statusMessage += " (复制模式)";
            }
            context.setStatusMessage(PlotI18n.status("status.plot.common.click_finish_suffix", PlotI18n.localizeStatus(statusMessage)));

            // 启用预览
            context.setPreviewEnabled(true);

            return ModifyResult.CONTINUE;

        } catch (Exception e) {
            LOGGER.error("移动策略鼠标移动处理失败: {}", e.getMessage(), e);
            return ModifyResult.CONTINUE;
        }
    }

    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        // 参考SelectionStrategy的条件检查
        if (button != MOUSE_LEFT || !isSelecting) {
            return ModifyResult.IGNORED;
        }

        if (currentMode == StrategyMode.SELECTION) {
            return super.handleSelectionMouseUp(pos, context);
        } else {
            return handleMoveMouseUp(pos, context);
        }
    }

    /**
     * 处理移动模式下的鼠标释放
     */
    private ModifyResult handleMoveMouseUp(Vec2d pos, ModifyToolContext context) {
        if (hasBasePoint && !isMoving) {
            // 如果已经设置了基点但还没有开始移动，则开始移动
            isMoving = true;
        }

        // 移动操作在onMouseDown中完成，这里只需要处理一些清理工作
        return ModifyResult.CONTINUE;
    }

    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        switch (keyCode) {
            case SHIFT_KEY -> {
                isShiftPressed = true;
                if (hasBasePoint) {
                    // 重新计算移动预览以应用正交约束
                    return onMouseMove(moveCurrentPoint, context);
                }
                return ModifyResult.CONTINUE;
            }
            case ESC_KEY -> {
                reset();
                context.setStatusMessage("status.plot.common.operation_cancelled");
                return ModifyResult.CANCEL;
            }
            default -> {
                return ModifyResult.IGNORED;
            }
        }
    }

    @Override
    public ModifyResult onKeyUp(int keyCode, ModifyToolContext context) {
        if (keyCode == SHIFT_KEY) {
            isShiftPressed = false;
            if (hasBasePoint) {
                return onMouseMove(moveCurrentPoint, context);
            }
        }
        return ModifyResult.IGNORED;
    }

    /**
     * 完成移动操作
     */
    private ModifyResult completeMove(Vec2d point, ModifyToolContext context) {
        moveCurrentPoint = point;

        // 计算最终移动向量
        Vec2d finalMoveVector = moveCurrentPoint.subtract(moveStartPoint);

        // 检查最小移动距离
        if (finalMoveVector.length() < MIN_MOVE_DISTANCE) {
            LOGGER.debug("移动距离太小，忽略此次移动");
            context.setStatusMessage("status.plot.move.too_small");
            return ModifyResult.COMPLETE; // 仍然返回完成，但重置工具状态
        }

        moveParameters.setStartPoint(moveStartPoint);
        moveParameters.setEndPoint(moveCurrentPoint);

        // 设置复制模式
        moveParameters.setCopyMode(isCtrlPressed);
        
        // 应用约束
        updateConstraints();
        IModifyHandler.ModifyParameters constrainedParameters = moveHandler.applyConstraints(moveParameters, moveConstraints);

        // 验证移动操作
        IModifyHandler.ValidationResult validation = moveHandler.validateModification(selectedShapes, constrainedParameters);
        if (!validation.isValid()) {
            context.setStatusMessage(PlotI18n.status("status.plot.move.invalid", validation.getLocalizedErrorMessage()));
            return ModifyResult.CONTINUE;
        }

        // 创建移动命令
        List<Shape> modifiedShapes = moveHandler.calculateModifiedShapes(selectedShapes, constrainedParameters);
        pendingCommand = moveHandler.createModifyCommand(selectedShapes, modifiedShapes, constrainedParameters);

        if (pendingCommand != null) {
            LOGGER.debug("移动操作完成，向量: {}", finalMoveVector);
            context.setStatusMessage("status.plot.move.complete");
            return ModifyResult.COMPLETE;
        }

        context.setStatusMessage("status.plot.move.command_failed");
        return ModifyResult.CANCEL;
    }

    /**
     * 更新约束状态
     */
    private void updateConstraints() {
        // Shift键启用正交约束
        moveConstraints.setOrthogonalConstraintEnabled(isShiftPressed);
    }

    /**
     * 重置移动状态
     */
    private void resetMoveState() {
        isMoving = false;
        hasBasePoint = false;
        moveStartPoint = null;
        moveCurrentPoint = null;
        previewShapes = null;
        pendingCommand = null;
        constrainedEndPoint = null;

        if (moveParameters != null) {
            moveParameters.clear();
        }
    }

    @Override
    public ModifyCommand getModifyCommand() {
        return pendingCommand;
    }

    @Override
    public void reset() {
        LOGGER.debug("MoveWithSelectionStrategy 重置");

        // 重置选择状态
        resetSelectionState();

        // 重置移动状态
        resetMoveState();

        // 重置修饰键状态
        isShiftPressed = false;
        isCtrlPressed = false;

        // 返回选择模式
        currentMode = StrategyMode.SELECTION;
        
        LOGGER.debug("MoveWithSelectionStrategy 重置完成，当前模式: {}", currentMode);
    }

    @Override
    public String getStrategyName() {
        return "移动选择结合策略";
    }

    @Override
    public String getStrategyDescription() {
        return currentMode.getDescription();
    }

    @Override
    public boolean requiresSelection() {
        return false; // 这个策略自己处理选择
    }

    @Override
    public int getMinimumSelectionCount() {
        return 0;
    }

    @Override
    public int getMaximumSelectionCount() {
        return IModifyStrategy.super.getMaximumSelectionCount();
    }

    /**
     * 获取当前模式
     */
    public StrategyMode getCurrentMode() {
        return currentMode;
    }

    /**
     * 获取预览图形
     */
    public List<Shape> getPreviewShapes() {
        return previewShapes;
    }

    /**
     * 重写选择状态消息，添加移动相关信息
     */
    @Override
    protected ModifyResult handleSelectionMouseUp(Vec2d pos, ModifyToolContext context) {
        ModifyResult result = super.handleSelectionMouseUp(pos, context);
        
        // 更新状态消息以包含移动信息
        if (result == ModifyResult.COMPLETE) {
            int count = getSelectedCount();
            if (count > 0) {
                context.setStatusMessage(PlotI18n.status("status.plot.common.selected_right_click", count, PlotI18n.tr("tool.plot.move")));
            } else {
                context.setStatusMessage("status.plot.move.select_shapes");
            }
        }
        
        return result;
    }

    /**
     * 渲染预览
     */
    public void renderPreview(DrawContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            renderSelectionPreview(context);
        } else {
            renderMovePreview(context);
        }
    }

    /**
     * 渲染预览（ImGui版本）
     */
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (currentMode == StrategyMode.SELECTION) {
            renderSelectionPreviewImGui(drawList, camera);
        } else {
            renderMovePreviewImGui(drawList, camera);
        }
    }

    /**
     * 渲染移动预览
     */
    private void renderMovePreview(DrawContext context) {
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        Color movePreviewColor = withAlpha(toColor(theme.warningText), PREVIEW_ALPHA);

        if (previewShapes != null) {
            for (Shape shape : previewShapes) {
                shape.render(context);
            }
        }

        // 渲染移动参考线
        if (moveStartPoint != null && moveCurrentPoint != null) {
            Vec2d effectiveEnd = constrainedEndPoint != null ? constrainedEndPoint : moveCurrentPoint;
            Vec2d moveVector = effectiveEnd.subtract(moveStartPoint);
            double moveDistance = moveVector.length();

            if (moveDistance > MIN_MOVE_DISTANCE) {
                context.drawDashedLine(moveStartPoint, effectiveEnd, movePreviewColor);
                context.drawCircle(moveStartPoint, 3.0f, movePreviewColor);
                context.drawCircle(effectiveEnd, 3.0f, movePreviewColor);
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

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    /**
     * 渲染移动预览（ImGui版本）
     */
    private void renderMovePreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        // 渲染移动参考线
        if (moveStartPoint != null && moveCurrentPoint != null) {
            try {
                Vec2d moveVector = moveCurrentPoint.subtract(moveStartPoint);
                double moveDistance = moveVector.length();

                if (moveDistance > MIN_MOVE_DISTANCE) {
                    Vec2d effectiveEnd = constrainedEndPoint != null ? constrainedEndPoint : moveCurrentPoint;
                    Vec2d screenStart = camera.worldToScreen(moveStartPoint);
                    Vec2d screenEnd = camera.worldToScreen(effectiveEnd);
                    UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();

                    int lineColor = theme.warningText;
                    float lineWidth = 2.0f;
                    int pointColor = theme.errorText;

                    // 绘制移动线
                    drawList.addLine(
                        (float) screenStart.x, (float) screenStart.y,
                        (float) screenEnd.x, (float) screenEnd.y,
                        lineColor, lineWidth
                    );

                    // 绘制起点和终点
                    drawList.addCircleFilled((float) screenStart.x, (float) screenStart.y, 4.0f, pointColor);
                    drawList.addCircleFilled((float) screenEnd.x, (float) screenEnd.y, 4.0f, pointColor);
                }
            } catch (Exception e) {
                LOGGER.warn("渲染移动预览时出错: {}", e.getMessage());
            }
        }
    }
}