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
import com.plot.ui.tools.impl.modify.helper.ScaleHandler;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.utils.ExceptionDebug;

import java.awt.Color;
import java.util.List;
import com.plot.utils.PlotI18n;

/**
 * 缩放工具与选择功能结合策略
 *
 * <p>这个策略结合了选择工具和缩放工具的功能：</p>
 * <ul>
 *   <li><strong>选择模式</strong>：左键点选/框选图形，右键完成选择</li>
 *   <li><strong>缩放模式</strong>：右键后进入缩放模式，支持三点缩放</li>
 *   <li><strong>智能切换</strong>：根据右键操作在选择和缩放间切换</li>
 * </ul>
 *
 * @author Plot Team
 * @version 1.0 - 缩放选择结合策略
 */
public class ScaleWithSelectionStrategy extends BaseSelectionStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleWithSelectionStrategy.class);

    // 策略模式枚举
    public enum StrategyMode {
        SELECTION("选择模式", "左键选择图形，右键完成选择"),
        SCALE("缩放模式", "三点缩放：中心点-参考点-目标点");

        private final String displayName;
        private final String description;

        StrategyMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    // 缩放状态枚举
    public enum ScaleState {
        IDLE("空闲", "等待设置缩放中心点"),
        AWAITING_REFERENCE("等待参考点", "status.plot.common.click_reference"),
        SCALING("缩放中", "移动鼠标缩放图形");

        private final String displayName;
        private final String description;

        ScaleState(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    // 常量
    private static final double DEFAULT_SCALE_STEP = 0.1;
    private static final int ESC_KEY = 27;
    private static final int SHIFT_KEY = 16;
    private static final int CTRL_KEY = 17;

    // 渲染常量
    private static final int PREVIEW_ALPHA = 180;

    // 策略状态
    private StrategyMode currentMode = StrategyMode.SELECTION;
    private ScaleState currentState = ScaleState.IDLE;
    private boolean isShiftPressed = false;
    private boolean isCtrlPressed = false;

    // 缩放坐标状态
    private Vec2d centerPoint;
    private Vec2d referencePoint;
    private Vec2d currentPoint;
    private double baseDistance = 1.0; // 参考距离

    // 缩放处理器和参数
    private ScaleHandler scaleHandler;
    private ModifyParameters scaleParameters;
    private ModifyConstraints scaleConstraints;
    private List<Shape> previewShapes;
    private ModifyCommand pendingCommand;

    /**
     * 默认构造函数
     */
    public ScaleWithSelectionStrategy() {
        this.scaleParameters = new ModifyParameters();
        this.scaleConstraints = new ModifyConstraints();
        // 默认启用宽高比约束
        this.scaleConstraints.setAspectRatioEnabled(false);
        this.scaleConstraints.setConstraintValue("scaleStep", DEFAULT_SCALE_STEP);
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
     * 用于在选择模式和缩放模式之间切换
     */
    private ModifyResult handleRightMouseDown(Vec2d pos, ModifyToolContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            // 在选择模式下右键：完成选择，切换到缩放模式
            if (!selectedShapeIds.isEmpty()) {
                currentMode = StrategyMode.SCALE;
                selectedShapes = getSelectedShapesFromIds(context);
                context.setStatusMessage(PlotI18n.status("status.plot.scale.initial_center", selectedShapeIds.size()));
                LOGGER.info("切换到缩放模式，已选择 {} 个图形", selectedShapeIds.size());
                return ModifyResult.CONTINUE;
            } else {
                context.setStatusMessage("status.plot.scale.initial_select");
                return ModifyResult.NEED_SELECTION;
            }
        } else {
            // 在缩放模式下右键：取消缩放，返回选择模式
            resetScaleState();
            currentMode = StrategyMode.SELECTION;
            context.setStatusMessage("status.plot.scale.cancelled");
            LOGGER.info("从缩放模式返回选择模式");
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
            return handleScaleMouseDown(pos, context);
        }
    }

    /**
     * 处理缩放模式下的左键按下
     */
    private ModifyResult handleScaleMouseDown(Vec2d pos, ModifyToolContext context) {
        if (selectedShapes == null || selectedShapes.isEmpty()) {
            context.setStatusMessage("status.plot.scale.no_selection");
            return ModifyResult.NEED_SELECTION;
        }

        // 初始化缩放处理器
        if (scaleHandler == null) {
            scaleHandler = new ScaleHandler(com.plot.core.state.AppState.getInstance());
        }

        Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

        // 同步修饰键状态
        try {
            isShiftPressed = imgui.ImGui.getIO().getKeyShift();
            isCtrlPressed = imgui.ImGui.getIO().getKeyCtrl();
        } catch (Exception e) { ExceptionDebug.log("ScaleWithSelectionStrategy: read modifier key state", e); }

        switch (currentState) {
            case IDLE -> {
                return setCenterPoint(snappedPoint, context);
            }
            case AWAITING_REFERENCE -> {
                return setReferencePoint(snappedPoint, context);
            }
            case SCALING -> {
                return completeScale(snappedPoint, context);
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
            return handleScaleMouseMove(pos, context);
        }
    }

    /**
     * 处理缩放模式下的鼠标移动
     */
    private ModifyResult handleScaleMouseMove(Vec2d pos, ModifyToolContext context) {
        if (currentState != ScaleState.SCALING && currentState != ScaleState.AWAITING_REFERENCE) {
            return ModifyResult.IGNORED;
        }

        try {
            // 获取吸附后的点
            currentPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

            if (currentState == ScaleState.AWAITING_REFERENCE) {
                // 在设置参考点时，显示从中心点到当前点的预览线
                context.setStatusMessage("status.plot.common.click_reference_preview");
                // 启用预览以显示从中心点到当前点的虚线
                context.setPreviewEnabled(true);
                return ModifyResult.CONTINUE;
            }

            // 计算缩放比例
            updateScaleParameters();

            // 应用约束
            ModifyConstraints tempConstraints = scaleConstraints.clone();
            if (isShiftPressed) {
                // Shift键临时启用统一缩放
                tempConstraints.setAspectRatioEnabled(true);
            }

            IModifyHandler.ModifyParameters constrainedParameters = scaleHandler.applyConstraints(scaleParameters, tempConstraints);

            // 创建预览图形
            previewShapes = scaleHandler.createPreviewShapes(selectedShapes, constrainedParameters);

            // 更新状态消息
            String statusMessage = generateStatusMessage(constrainedParameters);
            if (isCtrlPressed) {
                statusMessage += " (复制模式)";
            }
            context.setStatusMessage(PlotI18n.status("status.plot.common.click_finish_suffix", PlotI18n.localizeStatus(statusMessage)));

            // 启用预览
            context.setPreviewEnabled(true);

            return ModifyResult.CONTINUE;

        } catch (Exception e) {
            LOGGER.error("缩放策略鼠标移动处理失败: {}", e.getMessage(), e);
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
            return handleScaleMouseUp(pos, context);
        }
    }

    /**
     * 处理缩放模式下的鼠标释放
     */
    private ModifyResult handleScaleMouseUp(Vec2d pos, ModifyToolContext context) {
        // 缩放操作在onMouseDown中完成，这里只需要处理一些清理工作
        return ModifyResult.CONTINUE;
    }

    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        switch (keyCode) {
            case SHIFT_KEY -> {
                isShiftPressed = true;
                if (currentState == ScaleState.SCALING) {
                    // 重新计算缩放预览以应用约束
                    return onMouseMove(currentPoint, context);
                }
                return ModifyResult.CONTINUE;
            }
            case CTRL_KEY -> {
                isCtrlPressed = true;
                if (currentState == ScaleState.SCALING) {
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
            if (currentState == ScaleState.SCALING) {
                return onMouseMove(currentPoint, context);
            }
        }
        if (keyCode == CTRL_KEY) {
            isCtrlPressed = false;
            if (currentState == ScaleState.SCALING) {
                return onMouseMove(currentPoint, context);
            }
        }
        return ModifyResult.IGNORED;
    }

    /**
     * 设置缩放中心点
     */
    private ModifyResult setCenterPoint(Vec2d point, ModifyToolContext context) {
        centerPoint = point;
        currentState = ScaleState.AWAITING_REFERENCE;
        context.setStatusMessage("status.plot.common.click_reference");
        LOGGER.debug("设置缩放中心点: {}", centerPoint);
        return ModifyResult.CONTINUE;
    }

    /**
     * 设置参考点
     */
    private ModifyResult setReferencePoint(Vec2d point, ModifyToolContext context) {
        referencePoint = point;
        baseDistance = centerPoint.distance(referencePoint);
        currentState = ScaleState.SCALING;
        context.setStatusMessage("status.plot.scale.move_finish");

        LOGGER.debug("设置参考点: {}, 基准距离: {}", referencePoint, baseDistance);
        return ModifyResult.CONTINUE;
    }

    /**
     * 完成缩放操作
     */
    private ModifyResult completeScale(Vec2d point, ModifyToolContext context) {
        currentPoint = point;

        // 计算最终缩放比例
        updateScaleParameters();

        // 设置复制模式
        scaleParameters.setCopyMode(isCtrlPressed);

        // 应用约束
        ModifyConstraints tempConstraints = scaleConstraints.clone();
        if (isShiftPressed) {
            tempConstraints.setAspectRatioEnabled(true);
        }

        IModifyHandler.ModifyParameters constrainedParameters = scaleHandler.applyConstraints(scaleParameters, tempConstraints);

        // 验证缩放操作
        IModifyHandler.ValidationResult validation = scaleHandler.validateModification(selectedShapes, constrainedParameters);
        if (!validation.isValid()) {
            context.setStatusMessage(PlotI18n.status("status.plot.scale.invalid", validation.getErrorMessage()));
            return ModifyResult.CONTINUE;
        }

        // 创建缩放命令
        List<Shape> modifiedShapes = scaleHandler.calculateModifiedShapes(selectedShapes, constrainedParameters);
        pendingCommand = scaleHandler.createModifyCommand(selectedShapes, modifiedShapes, constrainedParameters);

        if (pendingCommand != null) {
            LOGGER.debug("缩放操作完成");
            context.setStatusMessage("status.plot.scale.complete");
            return ModifyResult.COMPLETE;
        }

        context.setStatusMessage("status.plot.scale.command_failed");
        return ModifyResult.CANCEL;
    }

    /**
     * 更新缩放参数
     */
    private void updateScaleParameters() {
        scaleParameters.setCenterPoint(centerPoint);

        // 统一缩放
        double currentDistance = centerPoint.distance(currentPoint);
        double scaleFactor = baseDistance > 0.001 ? currentDistance / baseDistance : 1.0;

        scaleParameters.setScaleFactor(scaleFactor);
        scaleParameters.setUniformScale(true);
    }

    /**
     * 生成状态消息
     */
    private String generateStatusMessage(IModifyHandler.ModifyParameters parameters) {
        if (parameters instanceof ModifyParameters params) {
            double scaleFactor = params.getDouble("scaleFactor", 1.0);
            return String.format("缩放比例: %.2fx", scaleFactor);
        }
        return "status.plot.transform.preview";
    }

    /**
     * 重置缩放状态
     */
    private void resetScaleState() {
        currentState = ScaleState.IDLE;
        centerPoint = null;
        referencePoint = null;
        currentPoint = null;
        baseDistance = 1.0;
        previewShapes = null;
        pendingCommand = null;

        if (scaleParameters != null) {
            scaleParameters.clear();
        }
    }

    @Override
    public ModifyCommand getModifyCommand() {
        return pendingCommand;
    }

    @Override
    public void reset() {
        LOGGER.debug("ScaleWithSelectionStrategy 重置");

        // 重置选择状态
        resetSelectionState();

        // 重置缩放状态
        resetScaleState();

        // 重置修饰键状态
        isShiftPressed = false;
        isCtrlPressed = false;

        // 返回选择模式
        currentMode = StrategyMode.SELECTION;
        
        LOGGER.debug("ScaleWithSelectionStrategy 重置完成，当前模式: {}", currentMode);
    }

    @Override
    public String getStrategyName() {
        return "缩放选择结合策略";
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
     * 获取缩放状态
     */
    public ScaleState getCurrentState() {
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
            renderScalePreview(context);
        }
    }

    /**
     * 渲染预览（ImGui版本）
     */
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (currentMode == StrategyMode.SELECTION) {
            renderSelectionPreviewImGui(drawList, camera);
        } else {
            renderScalePreviewImGui(drawList, camera);
        }
    }

    /**
     * 渲染缩放预览
     */
    private void renderScalePreview(DrawContext context) {
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        Color centerPointColor = toColor(theme.errorText);
        Color referencePointColor = toColor(theme.infoText);
        Color scalePreviewColor = withAlpha(toColor(theme.warningText), PREVIEW_ALPHA);

        if (previewShapes != null) {
            for (Shape shape : previewShapes) {
                shape.render(context);
            }
        }

        // 渲染缩放中心点
        if (centerPoint != null) {
            context.drawCircle(centerPoint, 5.0f, centerPointColor);
        }

        // 渲染参考点
        if (referencePoint != null) {
            context.drawCircle(referencePoint, 3.0f, referencePointColor);
        }

        // 渲染当前点
        if (currentPoint != null && (currentState == ScaleState.SCALING || currentState == ScaleState.AWAITING_REFERENCE)) {
            context.drawCircle(currentPoint, 3.0f, scalePreviewColor);
        }

        // 渲染缩放线
        if (centerPoint != null && referencePoint != null) {
            context.drawDashedLine(centerPoint, referencePoint, referencePointColor);
        }

        // 渲染从中心点到当前点的虚线（在设置参考点和缩放时都显示）
        if (centerPoint != null && currentPoint != null && (currentState == ScaleState.SCALING || currentState == ScaleState.AWAITING_REFERENCE)) {
            context.drawDashedLine(centerPoint, currentPoint, scalePreviewColor);
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
     * 渲染缩放预览（ImGui版本）
     */
    private void renderScalePreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        try {
            UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();

            // 渲染缩放中心点
            if (centerPoint != null) {
                Vec2d screenCenter = camera.worldToScreen(centerPoint);
                drawList.addCircleFilled((float) screenCenter.x, (float) screenCenter.y, 6.0f, theme.errorText);
            }

            // 渲染参考点
            if (referencePoint != null) {
                Vec2d screenRef = camera.worldToScreen(referencePoint);
                drawList.addCircleFilled((float) screenRef.x, (float) screenRef.y, 4.0f, theme.infoText);
            }

            // 渲染当前点
            if (currentPoint != null && (currentState == ScaleState.SCALING || currentState == ScaleState.AWAITING_REFERENCE)) {
                Vec2d screenCurrent = camera.worldToScreen(currentPoint);
                drawList.addCircleFilled((float) screenCurrent.x, (float) screenCurrent.y, 4.0f, theme.warningText);
            }

            // 渲染缩放线
            if (centerPoint != null && referencePoint != null) {
                Vec2d screenCenter = camera.worldToScreen(centerPoint);
                Vec2d screenRef = camera.worldToScreen(referencePoint);
                drawList.addLine(
                    (float) screenCenter.x, (float) screenCenter.y,
                    (float) screenRef.x, (float) screenRef.y,
                    theme.infoText, 2.0f
                );
            }

            // 渲染从中心点到当前点的虚线（在设置参考点和缩放时都显示）
            if (centerPoint != null && currentPoint != null && (currentState == ScaleState.SCALING || currentState == ScaleState.AWAITING_REFERENCE)) {
                Vec2d screenCenter = camera.worldToScreen(centerPoint);
                Vec2d screenCurrent = camera.worldToScreen(currentPoint);
                drawList.addLine(
                    (float) screenCenter.x, (float) screenCenter.y,
                    (float) screenCurrent.x, (float) screenCurrent.y,
                    theme.warningText, 2.0f
                );
            }
        } catch (Exception e) {
            LOGGER.warn("渲染缩放预览时出错: {}", e.getMessage());
        }
    }
}
