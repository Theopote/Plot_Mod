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
import com.plot.ui.tools.impl.modify.helper.RotateHandler;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.utils.ExceptionDebug;

import java.awt.Color;
import java.util.List;

/**
 * 旋转工具与选择功能结合策略
 *
 * <p>这个策略结合了选择工具和旋转工具的功能：</p>
 * <ul>
 *   <li><strong>选择模式</strong>：左键点选/框选图形，右键完成选择</li>
 *   <li><strong>旋转模式</strong>：右键后进入旋转模式，支持三点旋转</li>
 *   <li><strong>智能切换</strong>：根据右键操作在选择和旋转间切换</li>
 * </ul>
 *
 * @author Plot Team
 * @version 1.0 - 旋转选择结合策略
 */
public class RotateWithSelectionStrategy extends BaseSelectionStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(RotateWithSelectionStrategy.class);

    // 策略模式枚举
    public enum StrategyMode {
        SELECTION("选择模式", "左键选择图形，右键完成选择"),
        ROTATE("旋转模式", "三点旋转：中心点-参考点-目标点");

        private final String displayName;
        private final String description;

        StrategyMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    // 旋转状态枚举
    public enum RotateState {
        IDLE("空闲", "等待设置旋转中心点"),
        SETTING_CENTER("设置中心", "点击设置旋转中心点"),
        SETTING_REFERENCE("设置参考", "点击设置参考点"),
        ROTATING("旋转中", "移动鼠标旋转图形");

        private final String displayName;
        private final String description;

        RotateState(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    // 常量
    private static final double DEFAULT_ANGLE_STEP = Math.PI / 12.0; // 15度
    private static final int ESC_KEY = 27;
    private static final int SHIFT_KEY = 16;
    private static final int CTRL_KEY = 17;

    // 渲染常量
    private static final int PREVIEW_ALPHA = 180;

    // 策略状态
    private StrategyMode currentMode = StrategyMode.SELECTION;
    private RotateState currentState = RotateState.IDLE;
    private boolean isShiftPressed = false;
    private boolean isCtrlPressed = false;
    private boolean isAltPressed = false;

    // 旋转坐标状态
    private Vec2d centerPoint;
    private Vec2d referencePoint;
    private Vec2d currentPoint;
    private double baseAngle = 0.0; // 参考角度
    // 约束后用于渲染的当前点（当启用角度约束时使用）
    private Vec2d constrainedCurrentPoint;

    // 旋转处理器和参数
    private RotateHandler rotateHandler;
    private ModifyParameters rotateParameters;
    private ModifyConstraints rotateConstraints;
    private List<Shape> previewShapes;
    private ModifyCommand pendingCommand;

    /**
     * 默认构造函数
     */
    public RotateWithSelectionStrategy() {
        this.rotateParameters = new ModifyParameters();
        this.rotateConstraints = new ModifyConstraints();
        // 默认启用角度约束
        this.rotateConstraints.setAngleConstraintEnabled(false);
        this.rotateConstraints.setAngleStep(DEFAULT_ANGLE_STEP);
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
     * 用于在选择模式和旋转模式之间切换
     */
    private ModifyResult handleRightMouseDown(Vec2d pos, ModifyToolContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            // 在选择模式下右键：完成选择，切换到旋转模式
            if (!selectedShapeIds.isEmpty()) {
                currentMode = StrategyMode.ROTATE;
                selectedShapes = getSelectedShapesFromIds(context);
                context.setStatusMessage("已选择 " + selectedShapeIds.size() + " 个图形，点击设置旋转中心点");
                LOGGER.info("切换到旋转模式，已选择 {} 个图形", selectedShapeIds.size());
                return ModifyResult.CONTINUE;
            } else {
                context.setStatusMessage("请先选择要旋转的图形");
                return ModifyResult.NEED_SELECTION;
            }
        } else {
            // 在旋转模式下右键：取消旋转，返回选择模式
            resetRotateState();
            currentMode = StrategyMode.SELECTION;
            context.setStatusMessage("旋转已取消");
            LOGGER.info("从旋转模式返回选择模式");
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
            return handleRotateMouseDown(pos, context);
        }
    }

    /**
     * 处理旋转模式下的左键按下
     */
    private ModifyResult handleRotateMouseDown(Vec2d pos, ModifyToolContext context) {
        if (selectedShapes == null || selectedShapes.isEmpty()) {
            context.setStatusMessage("没有选中的图形可以旋转");
            return ModifyResult.NEED_SELECTION;
        }

        // 初始化旋转处理器
        if (rotateHandler == null) {
            rotateHandler = new RotateHandler(com.plot.core.state.AppState.getInstance());
        }

        Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

        // 同步修饰键状态
        try {
            isShiftPressed = imgui.ImGui.getIO().getKeyShift();
            isCtrlPressed = imgui.ImGui.getIO().getKeyCtrl();
            isAltPressed = imgui.ImGui.getIO().getKeyAlt();
        } catch (Exception e) { ExceptionDebug.log("RotateWithSelectionStrategy: read modifier key state", e); }

        switch (currentState) {
            case IDLE, SETTING_CENTER -> {
                return setCenterPoint(snappedPoint, context);
            }
            case SETTING_REFERENCE -> {
                return setReferencePoint(snappedPoint, context);
            }
            case ROTATING -> {
                return completeRotation(snappedPoint, context);
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
            return handleRotateMouseMove(pos, context);
        }
    }

    /**
     * 处理旋转模式下的鼠标移动
     */
    private ModifyResult handleRotateMouseMove(Vec2d pos, ModifyToolContext context) {
        if (currentState != RotateState.ROTATING && currentState != RotateState.SETTING_REFERENCE) {
            return ModifyResult.IGNORED;
        }

        try {
            // 获取吸附后的点
            currentPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

            if (currentState == RotateState.SETTING_REFERENCE) {
                // 在设置参考点时，显示从中心点到当前点的预览线
                context.setStatusMessage("点击设置参考点，移动鼠标查看预览");
                // 启用预览以显示从中心点到当前点的虚线
                context.setPreviewEnabled(true);
                return ModifyResult.CONTINUE;
            }

            // 计算旋转角度
            double currentAngle = rotateHandler.calculateAngle(centerPoint, currentPoint);
            double rotationAngle = rotateHandler.calculateAngleDifference(baseAngle, currentAngle);

            // 更新旋转参数
            rotateParameters.setCenterPoint(centerPoint);
            rotateParameters.setRotationAngle(rotationAngle);

            // 应用约束
            updateConstraints();
            IModifyHandler.ModifyParameters constrainedParameters = rotateHandler.applyConstraints(rotateParameters, rotateConstraints);

            // 创建预览图形
            previewShapes = rotateHandler.createPreviewShapes(selectedShapes, constrainedParameters);

            // 计算并保存约束后的当前点，用于渲染辅助线与角度弧线
            try {
                if (constrainedParameters instanceof ModifyParameters mp) {
                    double constrainedAngle = mp.getDouble("rotationAngle", 0.0);
                    // 当前角度 = baseAngle + constrainedAngle
                    double effectiveAngle = baseAngle + constrainedAngle;
                    double radius = centerPoint.distance(currentPoint);
                    constrainedCurrentPoint = new Vec2d(
                        centerPoint.x + radius * Math.cos(effectiveAngle),
                        centerPoint.y + radius * Math.sin(effectiveAngle)
                    );
                } else {
                    constrainedCurrentPoint = currentPoint;
                }
            } catch (Exception e) {
                ExceptionDebug.log("RotateWithSelectionStrategy: constrain rotation angle", e);
                constrainedCurrentPoint = currentPoint;
            }

            // 更新状态消息
            String statusMessage;
            if (constrainedParameters instanceof ModifyParameters) {
                statusMessage = rotateHandler.getStatusMessage((ModifyParameters) constrainedParameters);
            } else {
                statusMessage = "旋转预览中...";
            }
            if (isCtrlPressed) {
                statusMessage += " (复制模式)";
            }
            context.setStatusMessage(statusMessage + " - 点击完成旋转");

            // 启用预览
            context.setPreviewEnabled(true);

            return ModifyResult.CONTINUE;

        } catch (Exception e) {
            LOGGER.error("旋转策略鼠标移动处理失败: {}", e.getMessage(), e);
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
            return handleRotateMouseUp(pos, context);
        }
    }

    /**
     * 处理旋转模式下的鼠标释放
     */
    private ModifyResult handleRotateMouseUp(Vec2d pos, ModifyToolContext context) {
        // 旋转操作在onMouseDown中完成，这里只需要处理一些清理工作
        return ModifyResult.CONTINUE;
    }

    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        switch (keyCode) {
            case SHIFT_KEY -> {
                isShiftPressed = true;
                if (currentState == RotateState.ROTATING) {
                    // 重新计算旋转预览以应用角度约束
                    return onMouseMove(currentPoint, context);
                }
                return ModifyResult.CONTINUE;
            }
            case CTRL_KEY -> {
                isCtrlPressed = true;
                if (currentState == RotateState.ROTATING) {
                    return onMouseMove(currentPoint, context);
                }
                return ModifyResult.CONTINUE;
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
        if (keyCode == SHIFT_KEY) {
            isShiftPressed = false;
            if (currentState == RotateState.ROTATING) {
                return onMouseMove(currentPoint, context);
            }
        }
        if (keyCode == CTRL_KEY) {
            isCtrlPressed = false;
            if (currentState == RotateState.ROTATING) {
                return onMouseMove(currentPoint, context);
            }
        }
        return ModifyResult.IGNORED;
    }

    /**
     * 设置旋转中心点
     */
    private ModifyResult setCenterPoint(Vec2d point, ModifyToolContext context) {
        centerPoint = point;
        currentState = RotateState.SETTING_REFERENCE;
        context.setStatusMessage("点击设置参考点");
        LOGGER.debug("设置旋转中心点: {}", centerPoint);
        return ModifyResult.CONTINUE;
    }

    /**
     * 设置参考点
     */
    private ModifyResult setReferencePoint(Vec2d point, ModifyToolContext context) {
        referencePoint = point;
        baseAngle = rotateHandler.calculateAngle(centerPoint, referencePoint);
        currentState = RotateState.ROTATING;
        context.setStatusMessage("移动鼠标旋转图形，点击完成");

        LOGGER.debug("设置参考点: {}, 基准角度: {}°", referencePoint, Math.toDegrees(baseAngle));
        return ModifyResult.CONTINUE;
    }

    /**
     * 完成旋转操作
     */
    private ModifyResult completeRotation(Vec2d point, ModifyToolContext context) {
        currentPoint = point;

        // 计算最终旋转角度
        double currentAngle = rotateHandler.calculateAngle(centerPoint, currentPoint);
        double rotationAngle = rotateHandler.calculateAngleDifference(baseAngle, currentAngle);

        rotateParameters.setCenterPoint(centerPoint);
        rotateParameters.setRotationAngle(rotationAngle);

        // 设置复制模式
        rotateParameters.setCopyMode(isCtrlPressed);

        // 应用约束
        updateConstraints();
        IModifyHandler.ModifyParameters constrainedParameters = rotateHandler.applyConstraints(rotateParameters, rotateConstraints);

        // 验证旋转操作
        IModifyHandler.ValidationResult validation = rotateHandler.validateModification(selectedShapes, constrainedParameters);
        if (!validation.isValid()) {
            context.setStatusMessage("旋转无效: " + validation.getErrorMessage());
            return ModifyResult.CONTINUE;
        }

        // 创建旋转命令
        List<Shape> modifiedShapes = rotateHandler.calculateModifiedShapes(selectedShapes, constrainedParameters);
        pendingCommand = rotateHandler.createModifyCommand(selectedShapes, modifiedShapes, constrainedParameters);

        if (pendingCommand != null) {
            LOGGER.debug("旋转操作完成，角度: {}°", Math.toDegrees(rotationAngle));
            context.setStatusMessage("旋转完成");
            return ModifyResult.COMPLETE;
        }

        context.setStatusMessage("创建旋转命令失败");
        return ModifyResult.CANCEL;
    }

    /**
     * 更新约束状态
     */
    private void updateConstraints() {
        // 优先级：Ctrl/Alt > Shift > UI设置
        // Shift：启用角度约束
        // 使用UI设置
        if (isCtrlPressed || isAltPressed) {
            // Ctrl/Alt：精确旋转模式（禁用角度吸附）
            rotateConstraints.setAngleConstraintEnabled(false);
        } else rotateConstraints.setAngleConstraintEnabled(isShiftPressed);
    }

    /**
     * 重置旋转状态
     */
    private void resetRotateState() {
        currentState = RotateState.IDLE;
        centerPoint = null;
        referencePoint = null;
        currentPoint = null;
        baseAngle = 0.0;
        previewShapes = null;
        pendingCommand = null;
        constrainedCurrentPoint = null;

        if (rotateParameters != null) {
            rotateParameters.clear();
        }
    }

    @Override
    public ModifyCommand getModifyCommand() {
        return pendingCommand;
    }

    @Override
    public void reset() {
        LOGGER.debug("RotateWithSelectionStrategy 重置");

        // 重置选择状态
        resetSelectionState();

        // 重置旋转状态
        resetRotateState();

        // 重置修饰键状态
        isShiftPressed = false;
        isCtrlPressed = false;
        isAltPressed = false;

        // 返回选择模式
        currentMode = StrategyMode.SELECTION;
        
        LOGGER.debug("RotateWithSelectionStrategy 重置完成，当前模式: {}", currentMode);
    }

    @Override
    public String getStrategyName() {
        return "旋转选择结合策略";
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
     * 获取旋转状态
     */
    public RotateState getCurrentState() {
        return currentState;
    }

    /**
     * 获取预览图形
     */
    public List<Shape> getPreviewShapes() {
        return previewShapes;
    }

    /**
     * 获取旋转约束配置（用于外部设置角度步长等）
     */
    public com.plot.ui.tools.impl.modify.constants.ModifyConstraints getRotateConstraints() {
        return rotateConstraints;
    }

    /**
     * 渲染预览
     */
    public void renderPreview(DrawContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            renderSelectionPreview(context);
        } else {
            renderRotatePreview(context);
        }
    }

    /**
     * 渲染预览（ImGui版本）
     */
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (currentMode == StrategyMode.SELECTION) {
            renderSelectionPreviewImGui(drawList, camera);
        } else {
            renderRotatePreviewImGui(drawList, camera);
        }
    }

    /**
     * 渲染旋转预览
     */
    private void renderRotatePreview(DrawContext context) {
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        Color centerPointColor = toColor(theme.errorText);
        Color referencePointColor = toColor(theme.infoText);
        Color rotatePreviewColor = withAlpha(toColor(theme.successText), PREVIEW_ALPHA);
        Color angleColor = toColor(theme.warningText);

        if (previewShapes != null) {
            for (Shape shape : previewShapes) {
                shape.render(context);
            }
        }

        // 渲染旋转中心点
        if (centerPoint != null) {
            context.drawCircle(centerPoint, 5.0f, centerPointColor);
        }

        // 渲染参考点
        if (referencePoint != null) {
            context.drawCircle(referencePoint, 3.0f, referencePointColor);
        }

        // 渲染当前点
        if (currentPoint != null && (currentState == RotateState.ROTATING || currentState == RotateState.SETTING_REFERENCE)) {
            context.drawCircle(currentPoint, 3.0f, rotatePreviewColor);
        }

        // 渲染旋转线
        if (centerPoint != null && referencePoint != null) {
            context.drawDashedLine(centerPoint, referencePoint, referencePointColor);
        }

        // 渲染从中心点到当前点的虚线（在设置参考点和旋转时都显示）
        if (centerPoint != null && currentPoint != null && (currentState == RotateState.ROTATING || currentState == RotateState.SETTING_REFERENCE)) {
            Vec2d effectiveCurrent = constrainedCurrentPoint != null ? constrainedCurrentPoint : currentPoint;
            context.drawDashedLine(centerPoint, effectiveCurrent, rotatePreviewColor);

            // 绘制角度弧线与角度文本（使用参考点与有效当前点）
            if (referencePoint != null) {
                // 计算角度
                double referenceAngle = Math.atan2(referencePoint.y - centerPoint.y, referencePoint.x - centerPoint.x);
                double effectiveAngle = Math.atan2(effectiveCurrent.y - centerPoint.y, effectiveCurrent.x - centerPoint.x);

                double refRadius = centerPoint.distance(referencePoint);
                double curRadius = centerPoint.distance(effectiveCurrent);
                double radius = Math.min(refRadius, curRadius) * 0.3;

                context.drawArc(centerPoint, radius, referenceAngle, effectiveAngle, angleColor);

                double midAngle = referenceAngle + (effectiveAngle - referenceAngle) / 2.0;
                double angleDiff = Math.toDegrees(effectiveAngle - referenceAngle);
                while (angleDiff > 180) angleDiff -= 360;
                while (angleDiff < -180) angleDiff += 360;

                double textRadius = radius * 1.2;
                Vec2d textPos = new Vec2d(
                    centerPoint.x + textRadius * Math.cos(midAngle),
                    centerPoint.y + textRadius * Math.sin(midAngle)
                );
                context.drawText(String.format("%.1f°", angleDiff), textPos, angleColor);
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
     * 渲染旋转预览（ImGui版本）
     */
    private void renderRotatePreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        try {
            UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();

            // 渲染旋转中心点
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
            if (currentPoint != null && (currentState == RotateState.ROTATING || currentState == RotateState.SETTING_REFERENCE)) {
                Vec2d screenCurrent = camera.worldToScreen(currentPoint);
                drawList.addCircleFilled((float) screenCurrent.x, (float) screenCurrent.y, 4.0f, theme.successText);
            }

            // 渲染旋转线
            if (centerPoint != null && referencePoint != null) {
                Vec2d screenCenter = camera.worldToScreen(centerPoint);
                Vec2d screenRef = camera.worldToScreen(referencePoint);
                drawList.addLine(
                    (float) screenCenter.x, (float) screenCenter.y,
                    (float) screenRef.x, (float) screenRef.y,
                    theme.infoText, 2.0f
                );
            }

            // 渲染从中心点到当前点的虚线（在设置参考点和旋转时都显示）
            if (centerPoint != null && currentPoint != null && (currentState == RotateState.ROTATING || currentState == RotateState.SETTING_REFERENCE)) {
                Vec2d screenCenter = camera.worldToScreen(centerPoint);
                Vec2d screenCurrent = camera.worldToScreen(currentPoint);
                drawList.addLine(
                    (float) screenCenter.x, (float) screenCenter.y,
                    (float) screenCurrent.x, (float) screenCurrent.y,
                    theme.successText, 2.0f
                );
            }
        } catch (Exception e) {
            LOGGER.warn("渲染旋转预览时出错: {}", e.getMessage());
        }
    }
}
