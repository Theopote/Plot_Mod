package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.graphics.DrawContext;
import com.plot.core.model.Shape;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.theme.UITheme;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.tools.impl.modify.helper.IModifyHandler;
import com.plot.ui.tools.impl.modify.constants.ModifyConstraints;
import com.plot.ui.tools.impl.modify.dto.ModifyParameters;
import com.plot.ui.tools.impl.modify.helper.MirrorHandler;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.utils.ExceptionDebug;

import java.awt.Color;
import java.util.List;
import com.plot.utils.PlotI18n;

/**
 * 镜像工具与选择功能结合策略
 *
 * <p>这个策略结合了选择工具和镜像工具的功能：</p>
 * <ul>
 *   <li><strong>选择模式</strong>：左键点选/框选图形，右键完成选择</li>
 *   <li><strong>镜像模式</strong>：右键后进入镜像模式，支持两点定义镜像轴</li>
 *   <li><strong>智能切换</strong>：根据右键操作在选择和镜像间切换</li>
 * </ul>
 *
 * @author Plot Team
 * @version 1.0 - 镜像选择结合策略
 */
public class MirrorWithSelectionStrategy extends BaseSelectionStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(MirrorWithSelectionStrategy.class);

    // 策略模式枚举
    public enum StrategyMode {
        SELECTION("strategy.plot.mode.selection", "strategy.plot.mode.selection.desc"),
        MIRROR("strategy.plot.mode.mirror", "strategy.plot.mode.mirror.desc");

        private final String nameKey;
        private final String descKey;

        StrategyMode(String nameKey, String descKey) {
            this.nameKey = nameKey;
            this.descKey = descKey;
        }

        public String getDisplayName() { return PlotI18n.modeLabel(nameKey); }
        public String getDescription() { return PlotI18n.modeLabel(descKey); }
    }

    // 镜像状态枚举
    public enum MirrorState {
        IDLE("mode.plot.common.idle", "mode.plot.mirror.state.idle.desc"),
        SETTING_AXIS_END("mode.plot.mirror.state.setting_axis_end", "mode.plot.mirror.state.setting_axis_end.desc");

        private final String nameKey;
        private final String descKey;

        MirrorState(String nameKey, String descKey) {
            this.nameKey = nameKey;
            this.descKey = descKey;
        }

        public String getDisplayName() { return PlotI18n.modeLabel(nameKey); }
        public String getDescription() { return PlotI18n.modeLabel(descKey); }
    }

    // 注意：镜像“模式”已改为几何对称类型（轴对称/中心对称），详见 {@link MirrorMode}

    // 常量
    private static final int ESC_KEY = 27;
    private static final int SHIFT_KEY = 16;
    private static final int CTRL_KEY = 17;

    // 渲染常量
    private static final int PREVIEW_ALPHA = 180;

    // 策略状态
    private StrategyMode currentMode = StrategyMode.SELECTION;
    private MirrorState currentState = MirrorState.IDLE;
    private MirrorMode mirrorMode = MirrorMode.AXIS_SYMMETRY;
    private boolean isShiftPressed = false;
    private boolean isCtrlPressed = false;

    // 镜像坐标状态
    private Vec2d axisStartPoint;
    private Vec2d axisEndPoint;
    private Vec2d currentPoint;
    // 预览时受约束后的终点（用于按Shift显示正交辅助线）
    private Vec2d previewAxisEndPoint;

    // 镜像处理器和参数
    private MirrorHandler mirrorHandler;
    private ModifyParameters mirrorParameters;
    private ModifyConstraints mirrorConstraints;
    private List<Shape> previewShapes;
    private ModifyCommand pendingCommand;

    /**
     * 设置镜像模式（几何对称类型）
     */
    public void setMirrorMode(MirrorMode mode) {
        if (mode != null && this.mirrorMode != mode) {
            this.mirrorMode = mode;
            LOGGER.debug("MirrorWithSelectionStrategy 镜像模式已设置为: {}", mode.getDisplayName());
        }
    }

    public MirrorMode getMirrorMode() {
        return mirrorMode;
    }

    /**
     * 默认构造函数
     */
    public MirrorWithSelectionStrategy() {
        this.mirrorParameters = new ModifyParameters();
        this.mirrorConstraints = new ModifyConstraints();
        // 默认启用正交约束
        this.mirrorConstraints.setOrthogonalConstraintEnabled(false);
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
     * 用于在选择模式和镜像模式之间切换
     */
    private ModifyResult handleRightMouseDown(Vec2d pos, ModifyToolContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            // 在选择模式下右键：完成选择，切换到镜像模式
            if (!selectedShapeIds.isEmpty()) {
                currentMode = StrategyMode.MIRROR;
                selectedShapes = getSelectedShapesFromIds(context);
                String hint = (mirrorMode == MirrorMode.CENTRAL_SYMMETRY) ? "status.plot.mirror.initial_center" : "status.plot.mirror.initial_axis";
                context.setStatusMessage(PlotI18n.status("status.plot.common.selected_suffix", selectedShapeIds.size(), PlotI18n.localizeStatus(hint)));
                LOGGER.info("切换到镜像模式，已选择 {} 个图形", selectedShapeIds.size());
                return ModifyResult.CONTINUE;
            } else {
                context.setStatusMessage("status.plot.mirror.initial_select");
                return ModifyResult.NEED_SELECTION;
            }
        } else {
            // 在镜像模式下右键：取消镜像，返回选择模式
            resetMirrorState();
            currentMode = StrategyMode.SELECTION;
            context.setStatusMessage("status.plot.mirror.cancelled");
            LOGGER.info("从镜像模式返回选择模式");
            return ModifyResult.CANCEL;
        }
    }

    /**
     * 处理左键按下
     * 根据当前模式执行不同操作
     */
    private ModifyResult handleLeftMouseDown(Vec2d pos, ModifyToolContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            return handleSelectionMouseDown(pos, context);
        } else {
            return handleMirrorMouseDown(pos, context);
        }
    }

    /**
     * 处理镜像模式下的左键按下
     */
    private ModifyResult handleMirrorMouseDown(Vec2d pos, ModifyToolContext context) {
        if (selectedShapes == null || selectedShapes.isEmpty()) {
            context.setStatusMessage("status.plot.mirror.no_selection");
            return ModifyResult.NEED_SELECTION;
        }

        // 初始化镜像处理器
        if (mirrorHandler == null) {
            mirrorHandler = new MirrorHandler(com.plot.core.state.AppState.getInstance());
        }

        Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

        // 同步修饰键状态
        try {
            isShiftPressed = imgui.ImGui.getIO().getKeyShift();
            isCtrlPressed = imgui.ImGui.getIO().getKeyCtrl();
        } catch (Exception e) { ExceptionDebug.log("MirrorWithSelectionStrategy: read modifier key state", e); }

        switch (currentState) {
            case IDLE -> {
                return setAxisStartPoint(snappedPoint, context);
            }
            case SETTING_AXIS_END -> {
                return completeMirror(snappedPoint, context);
            }
            default -> {
                return ModifyResult.IGNORED;
            }
        }
    }

    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            return handleSelectionMouseMove(pos, context);
        } else {
            return handleMirrorMouseMove(pos, context);
        }
    }

    /**
     * 处理镜像模式下的鼠标移动
     */
    private ModifyResult handleMirrorMouseMove(Vec2d pos, ModifyToolContext context) {
        if (currentState != MirrorState.SETTING_AXIS_END) {
            return ModifyResult.IGNORED;
        }

        try {
            // 同步修饰键状态，确保在移动时能立即响应 Shift/Ctrl
            try {
                isShiftPressed = imgui.ImGui.getIO().getKeyShift();
                isCtrlPressed = imgui.ImGui.getIO().getKeyCtrl();
            } catch (Throwable ignore) {}
            // 获取吸附后的点
            currentPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

            // 设置镜像参数
            mirrorParameters.setVec2d(ModifyParameters.MIRROR_AXIS_START, axisStartPoint);
            mirrorParameters.setVec2d(ModifyParameters.MIRROR_AXIS_END, currentPoint);
            mirrorParameters.setString("mirrorMode", mirrorMode.name());
            // Ctrl：临时复制（保留原图形）
            mirrorParameters.setBoolean(ModifyParameters.COPY_MODE, isCtrlPressed);

            // 应用约束
            updateConstraints();
            IModifyHandler.ModifyParameters constrainedParameters = mirrorHandler.applyConstraints(mirrorParameters, mirrorConstraints);

            // 创建预览图形
            previewShapes = mirrorHandler.createPreviewShapes(selectedShapes, constrainedParameters);

            // 更新预览终点（用于渲染受约束的辅助线）
            if (constrainedParameters instanceof com.plot.ui.tools.impl.modify.dto.ModifyParameters cp) {
                previewAxisEndPoint = cp.getVec2d(ModifyParameters.MIRROR_AXIS_END);
            } else {
                previewAxisEndPoint = currentPoint;
            }

            // 更新状态消息
            String statusMessage;
            if (mirrorMode == MirrorMode.CENTRAL_SYMMETRY) {
                statusMessage = PlotI18n.status("status.plot.mirror.central_preview",
                        axisStartPoint.x, axisStartPoint.y);
            } else {
                statusMessage = PlotI18n.status("status.plot.mirror.axis_preview",
                        axisStartPoint.distance(currentPoint));
                if (isShiftPressed) {
                    statusMessage += PlotI18n.status("status.plot.common.orthogonal_suffix");
                }
            }
            if (isCtrlPressed) {
                statusMessage += PlotI18n.status("status.plot.common.copy_suffix");
            }
            context.setStatusMessage(statusMessage + PlotI18n.status("status.plot.common.click_finish_dash"));

            // 启用预览
            context.setPreviewEnabled(true);

            return ModifyResult.CONTINUE;

        } catch (Exception e) {
            LOGGER.error("镜像策略鼠标移动处理失败: {}", e.getMessage(), e);
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
            return handleSelectionMouseUp(pos, context);
        } else {
            return handleMirrorMouseUp(pos, context);
        }
    }

    /**
     * 处理镜像模式下的鼠标释放
     */
    private ModifyResult handleMirrorMouseUp(Vec2d pos, ModifyToolContext context) {
        // 镜像操作在onMouseDown中完成，这里只需要处理一些清理工作
        return ModifyResult.CONTINUE;
    }

    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        switch (keyCode) {
            case SHIFT_KEY -> {
                isShiftPressed = true;
                if (currentState == MirrorState.SETTING_AXIS_END) {
                    // 重新计算镜像预览以应用正交约束
                    return onMouseMove(currentPoint, context);
                }
                return ModifyResult.CONTINUE;
            }
            case CTRL_KEY -> {
                isCtrlPressed = true;
                if (currentState == MirrorState.SETTING_AXIS_END) {
                    return onMouseMove(currentPoint, context);
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
            if (currentState == MirrorState.SETTING_AXIS_END) {
                return onMouseMove(currentPoint, context);
            }
        }
        if (keyCode == CTRL_KEY) {
            isCtrlPressed = false;
            if (currentState == MirrorState.SETTING_AXIS_END) {
                return onMouseMove(currentPoint, context);
            }
        }
        return ModifyResult.IGNORED;
    }

    /**
     * 设置镜像轴起点
     */
    private ModifyResult setAxisStartPoint(Vec2d point, ModifyToolContext context) {
        axisStartPoint = point;
        currentState = MirrorState.SETTING_AXIS_END;
        if (mirrorMode == MirrorMode.CENTRAL_SYMMETRY) {
            context.setStatusMessage("status.plot.mirror.center_confirm");
        } else {
            context.setStatusMessage("status.plot.mirror.axis_drag");
        }
        LOGGER.debug("设置镜像轴起点: {}", axisStartPoint);
        return ModifyResult.CONTINUE;
    }

    /**
     * 完成镜像操作
     */
    private ModifyResult completeMirror(Vec2d point, ModifyToolContext context) {
        axisEndPoint = point;
        currentPoint = point;

        // 设置最终镜像参数
        mirrorParameters.setVec2d(ModifyParameters.MIRROR_AXIS_START, axisStartPoint);
        mirrorParameters.setVec2d(ModifyParameters.MIRROR_AXIS_END, axisEndPoint);
        mirrorParameters.setString("mirrorMode", mirrorMode.name());
        mirrorParameters.setBoolean(ModifyParameters.COPY_MODE, isCtrlPressed);

        // 应用约束
        updateConstraints();
        IModifyHandler.ModifyParameters constrainedParameters = mirrorHandler.applyConstraints(mirrorParameters, mirrorConstraints);

        // 验证镜像操作
        IModifyHandler.ValidationResult validation = mirrorHandler.validateModification(selectedShapes, constrainedParameters);
        if (!validation.isValid()) {
            context.setStatusMessage(PlotI18n.status("status.plot.mirror.invalid", validation.getLocalizedErrorMessage()));
            return ModifyResult.CONTINUE;
        }

        // 创建镜像命令
        List<Shape> modifiedShapes = mirrorHandler.calculateModifiedShapes(selectedShapes, constrainedParameters);
        pendingCommand = mirrorHandler.createModifyCommand(selectedShapes, modifiedShapes, constrainedParameters);

        if (pendingCommand != null) {
            LOGGER.debug("镜像操作完成");
            context.setStatusMessage("status.plot.mirror.complete");
            return ModifyResult.COMPLETE;
        }

        context.setStatusMessage("status.plot.mirror.command_failed");
        return ModifyResult.CANCEL;
    }

    /**
     * 更新约束状态
     */
    private void updateConstraints() {
        // Shift键启用正交约束
        mirrorConstraints.setOrthogonalConstraintEnabled(isShiftPressed);
    }

    /**
     * 重置镜像状态
     */
    private void resetMirrorState() {
        currentState = MirrorState.IDLE;
        axisStartPoint = null;
        axisEndPoint = null;
        currentPoint = null;
        previewAxisEndPoint = null;
        previewShapes = null;
        pendingCommand = null;

        if (mirrorParameters != null) {
            mirrorParameters.clear();
        }
    }

    @Override
    public ModifyCommand getModifyCommand() {
        return pendingCommand;
    }

    @Override
    public void reset() {
        LOGGER.debug("MirrorWithSelectionStrategy 重置");

        // 重置选择状态
        resetSelectionState();

        // 重置镜像状态
        resetMirrorState();

        // 重置修饰键状态
        isShiftPressed = false;
        isCtrlPressed = false;

        // 返回选择模式
        currentMode = StrategyMode.SELECTION;
        
        LOGGER.debug("MirrorWithSelectionStrategy 重置完成，当前模式: {}", currentMode);
    }

    @Override
    public String getStrategyName() {
        return "镜像选择结合策略";
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
     * 获取镜像状态
     */
    public MirrorState getCurrentState() {
        return currentState;
    }

    /**
     * 获取预览图形
     */
    public List<Shape> getPreviewShapes() {
        return previewShapes;
    }

    /**
     * 渲染预览
     */
    public void renderPreview(DrawContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            renderSelectionPreview(context);
        } else {
            renderMirrorPreview(context);
        }
    }

    /**
     * 渲染预览（ImGui版本）
     */
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (currentMode == StrategyMode.SELECTION) {
            renderSelectionPreviewImGui(drawList, camera);
        } else {
            renderMirrorPreviewImGui(drawList, camera);
        }
    }

    /**
     * 渲染镜像预览
     */
    private void renderMirrorPreview(DrawContext context) {
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        Color axisColor = toColor(theme.warningText);
        Color mirrorPreviewColor = withAlpha(toColor(theme.accent), PREVIEW_ALPHA);

        if (previewShapes != null) {
            for (Shape shape : previewShapes) {
                shape.render(context);
            }
        }

        // 辅助提示：轴对称显示轴线；中心对称显示中心点
        if (mirrorMode == MirrorMode.CENTRAL_SYMMETRY) {
            if (axisStartPoint != null) {
                context.drawCircle(axisStartPoint, 4.0f, axisColor);
                // 给用户一个“确认”的视觉反馈：从中心到鼠标画虚线（不参与计算）
                if (currentPoint != null && currentState == MirrorState.SETTING_AXIS_END) {
                    Vec2d endToDraw = previewAxisEndPoint != null ? previewAxisEndPoint : currentPoint;
                    context.drawDashedLine(axisStartPoint, endToDraw, axisColor);
                }
            }
        } else {
            // 轴对称：渲染镜像轴
            if (axisStartPoint != null && axisEndPoint != null) {
                context.drawLine(axisStartPoint, axisEndPoint, axisColor);
                context.drawCircle(axisStartPoint, 3.0f, axisColor);
                context.drawCircle(axisEndPoint, 3.0f, axisColor);
                } else if (axisStartPoint != null && currentPoint != null) {
                    // 绘制临时镜像轴，优先使用受约束的预览终点
                    Vec2d endToDraw = previewAxisEndPoint != null ? previewAxisEndPoint : currentPoint;
                    context.drawDashedLine(axisStartPoint, endToDraw, axisColor);
                    context.drawCircle(axisStartPoint, 3.0f, axisColor);
                    context.drawCircle(endToDraw, 3.0f, mirrorPreviewColor);
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
     * 渲染镜像预览（ImGui版本）
     */
    private void renderMirrorPreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        try {
            UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
            if (mirrorMode == MirrorMode.CENTRAL_SYMMETRY) {
                if (axisStartPoint != null) {
                    Vec2d screenCenter = camera.worldToScreen(axisStartPoint);
                    drawList.addCircleFilled((float) screenCenter.x, (float) screenCenter.y, 5.0f, theme.warningText);
                    if (currentPoint != null && currentState == MirrorState.SETTING_AXIS_END) {
                        Vec2d screenCurrent = camera.worldToScreen(currentPoint);
                        drawList.addLine(
                            (float) screenCenter.x, (float) screenCenter.y,
                            (float) screenCurrent.x, (float) screenCurrent.y,
                            theme.warningText, 2.0f
                        );
                    }
                }
            } else {
                // 渲染镜像轴
                if (axisStartPoint != null && axisEndPoint != null) {
                    Vec2d screenStart = camera.worldToScreen(axisStartPoint);
                    Vec2d screenEnd = camera.worldToScreen(axisEndPoint);

                    drawList.addLine(
                        (float) screenStart.x, (float) screenStart.y,
                        (float) screenEnd.x, (float) screenEnd.y,
                        theme.warningText, 3.0f
                    );

                    drawList.addCircleFilled((float) screenStart.x, (float) screenStart.y, 4.0f, theme.warningText);
                    drawList.addCircleFilled((float) screenEnd.x, (float) screenEnd.y, 4.0f, theme.warningText);
                } else if (axisStartPoint != null && currentPoint != null) {
                    // 绘制临时镜像轴（优先使用受约束的预览终点）
                    Vec2d endToDraw = previewAxisEndPoint != null ? previewAxisEndPoint : currentPoint;
                    Vec2d screenStart = camera.worldToScreen(axisStartPoint);
                    Vec2d screenCurrent = camera.worldToScreen(endToDraw);

                    drawList.addLine(
                        (float) screenStart.x, (float) screenStart.y,
                        (float) screenCurrent.x, (float) screenCurrent.y,
                        theme.warningText, 2.0f
                    );

                    drawList.addCircleFilled((float) screenStart.x, (float) screenStart.y, 4.0f, theme.warningText);
                    drawList.addCircleFilled((float) screenCurrent.x, (float) screenCurrent.y, 4.0f, theme.accent);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("渲染镜像预览时出错: {}", e.getMessage());
        }
    }
}
