package com.masterplanner.ui.tools.impl.modify.strategy;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.canvas.CanvasCamera;
import com.masterplanner.ui.tools.impl.modify.helper.IModifyHandler;
import com.masterplanner.ui.tools.impl.modify.constants.ModifyConstraints;
import com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters;
import com.masterplanner.ui.tools.impl.modify.helper.MirrorHandler;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.List;

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
 * @author MasterPlanner Team
 * @version 1.0 - 镜像选择结合策略
 */
public class MirrorWithSelectionStrategy extends BaseSelectionStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(MirrorWithSelectionStrategy.class);

    // 策略模式枚举
    public enum StrategyMode {
        SELECTION("选择模式", "左键选择图形，右键完成选择"),
        MIRROR("镜像模式", "两点定义镜像轴");

        private final String displayName;
        private final String description;

        StrategyMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    // 镜像状态枚举
    public enum MirrorState {
        IDLE("空闲", "等待设置镜像轴起点"),
        SETTING_AXIS_END("设置终点", "点击设置镜像轴终点");

        private final String displayName;
        private final String description;

        MirrorState(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    // 镜像模式枚举
    public enum MirrorMode {
        MIRROR("镜像", "将图形镜像到轴的另一侧，删除原图形"),
        COPY_MIRROR("复制镜像", "将图形镜像到轴的另一侧，保留原图形");

        private final String displayName;
        private final String description;

        MirrorMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    // 常量
    private static final int ESC_KEY = 27;
    private static final int SHIFT_KEY = 16;
    private static final int CTRL_KEY = 17;

    // 渲染常量
    private static final Color MIRROR_PREVIEW_COLOR = new Color(128, 0, 128, 180); // 紫色
    private static final Color AXIS_COLOR = new Color(255, 255, 0, 255); // 黄色镜像轴

    // 策略状态
    private StrategyMode currentMode = StrategyMode.SELECTION;
    private MirrorState currentState = MirrorState.IDLE;
    private MirrorMode mirrorMode = MirrorMode.MIRROR;
    private boolean isShiftPressed = false;
    private boolean isCtrlPressed = false;

    // 镜像坐标状态
    private Vec2d axisStartPoint;
    private Vec2d axisEndPoint;
    private Vec2d currentPoint;

    // 镜像处理器和参数
    private MirrorHandler mirrorHandler;
    private ModifyParameters mirrorParameters;
    private ModifyConstraints mirrorConstraints;
    private List<Shape> previewShapes;
    private ModifyCommand pendingCommand;

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
                context.setStatusMessage("已选择 " + selectedShapeIds.size() + " 个图形，点击设置镜像轴起点");
                LOGGER.info("切换到镜像模式，已选择 {} 个图形", selectedShapeIds.size());
                return ModifyResult.CONTINUE;
            } else {
                context.setStatusMessage("请先选择要镜像的图形");
                return ModifyResult.NEED_SELECTION;
            }
        } else {
            // 在镜像模式下右键：取消镜像，返回选择模式
            resetMirrorState();
            currentMode = StrategyMode.SELECTION;
            context.setStatusMessage("镜像已取消");
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
            context.setStatusMessage("没有选中的图形可以镜像");
            return ModifyResult.NEED_SELECTION;
        }

        // 初始化镜像处理器
        if (mirrorHandler == null) {
            mirrorHandler = new MirrorHandler(com.masterplanner.core.state.AppState.getInstance());
        }

        Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

        // 同步修饰键状态
        try {
            isShiftPressed = imgui.ImGui.getIO().getKeyShift();
            isCtrlPressed = imgui.ImGui.getIO().getKeyCtrl();
        } catch (Exception ignored) {}

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
            // 获取吸附后的点
            currentPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

            // 设置镜像参数
            mirrorParameters.setVec2d(ModifyParameters.MIRROR_AXIS_START, axisStartPoint);
            mirrorParameters.setVec2d(ModifyParameters.MIRROR_AXIS_END, currentPoint);
            mirrorParameters.setString("mirrorMode", mirrorMode.name());

            // 应用约束
            updateConstraints();
            IModifyHandler.ModifyParameters constrainedParameters = mirrorHandler.applyConstraints(mirrorParameters, mirrorConstraints);

            // 创建预览图形
            previewShapes = mirrorHandler.createPreviewShapes(selectedShapes, constrainedParameters);

            // 更新状态消息
            String statusMessage = String.format("镜像轴长度: %.2f", axisStartPoint.distance(currentPoint));
            if (isShiftPressed) {
                statusMessage += " (正交约束)";
            }
            if (mirrorMode == MirrorMode.COPY_MIRROR) {
                statusMessage += " (复制模式)";
            }
            context.setStatusMessage(statusMessage + " - 点击完成镜像");

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
                // Ctrl键切换镜像模式
                toggleMirrorMode();
                if (currentState == MirrorState.SETTING_AXIS_END) {
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
            if (currentState == MirrorState.SETTING_AXIS_END) {
                return onMouseMove(currentPoint, context);
            }
        }
        if (keyCode == CTRL_KEY) {
            isCtrlPressed = false;
        }
        return ModifyResult.IGNORED;
    }

    /**
     * 设置镜像轴起点
     */
    private ModifyResult setAxisStartPoint(Vec2d point, ModifyToolContext context) {
        axisStartPoint = point;
        currentState = MirrorState.SETTING_AXIS_END;
        context.setStatusMessage("移动鼠标设置镜像轴终点，点击完成");
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

        // 应用约束
        updateConstraints();
        IModifyHandler.ModifyParameters constrainedParameters = mirrorHandler.applyConstraints(mirrorParameters, mirrorConstraints);

        // 验证镜像操作
        IModifyHandler.ValidationResult validation = mirrorHandler.validateModification(selectedShapes, constrainedParameters);
        if (!validation.isValid()) {
            context.setStatusMessage("镜像无效: " + validation.getErrorMessage());
            return ModifyResult.CONTINUE;
        }

        // 创建镜像命令
        List<Shape> modifiedShapes = mirrorHandler.calculateModifiedShapes(selectedShapes, constrainedParameters);
        pendingCommand = mirrorHandler.createModifyCommand(selectedShapes, modifiedShapes, constrainedParameters);

        if (pendingCommand != null) {
            LOGGER.debug("镜像操作完成");
            context.setStatusMessage("镜像完成");
            return ModifyResult.COMPLETE;
        }

        context.setStatusMessage("创建镜像命令失败");
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
     * 切换镜像模式
     */
    private void toggleMirrorMode() {
        mirrorMode = (mirrorMode == MirrorMode.MIRROR) ? MirrorMode.COPY_MIRROR : MirrorMode.MIRROR;
        LOGGER.debug("切换到镜像模式: {}", mirrorMode.getDisplayName());
    }

    /**
     * 重置镜像状态
     */
    private void resetMirrorState() {
        currentState = MirrorState.IDLE;
        axisStartPoint = null;
        axisEndPoint = null;
        currentPoint = null;
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
        if (previewShapes != null) {
            for (Shape shape : previewShapes) {
                shape.render(context);
            }
        }

        // 渲染镜像轴
        if (axisStartPoint != null && axisEndPoint != null) {
            context.drawLine(axisStartPoint, axisEndPoint, AXIS_COLOR);
            context.drawCircle(axisStartPoint, 3.0f, AXIS_COLOR);
            context.drawCircle(axisEndPoint, 3.0f, AXIS_COLOR);
        } else if (axisStartPoint != null && currentPoint != null) {
            // 绘制临时镜像轴
            context.drawDashedLine(axisStartPoint, currentPoint, AXIS_COLOR);
            context.drawCircle(axisStartPoint, 3.0f, AXIS_COLOR);
            context.drawCircle(currentPoint, 3.0f, MIRROR_PREVIEW_COLOR);
        }
    }

    /**
     * 渲染镜像预览（ImGui版本）
     */
    private void renderMirrorPreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        try {
            // 渲染镜像轴
            if (axisStartPoint != null && axisEndPoint != null) {
                Vec2d screenStart = camera.worldToScreen(axisStartPoint);
                Vec2d screenEnd = camera.worldToScreen(axisEndPoint);

                drawList.addLine(
                    (float) screenStart.x, (float) screenStart.y,
                    (float) screenEnd.x, (float) screenEnd.y,
                    0xFFFFFF00, 3.0f // 黄色轴
                );

                drawList.addCircleFilled((float) screenStart.x, (float) screenStart.y, 4.0f, 0xFFFFFF00);
                drawList.addCircleFilled((float) screenEnd.x, (float) screenEnd.y, 4.0f, 0xFFFFFF00);
            } else if (axisStartPoint != null && currentPoint != null) {
                // 绘制临时镜像轴
                Vec2d screenStart = camera.worldToScreen(axisStartPoint);
                Vec2d screenCurrent = camera.worldToScreen(currentPoint);

                drawList.addLine(
                    (float) screenStart.x, (float) screenStart.y,
                    (float) screenCurrent.x, (float) screenCurrent.y,
                    0xFFFFFF00, 2.0f // 黄色轴（虚线效果）
                );

                drawList.addCircleFilled((float) screenStart.x, (float) screenStart.y, 4.0f, 0xFFFFFF00);
                drawList.addCircleFilled((float) screenCurrent.x, (float) screenCurrent.y, 4.0f, 0xFF800080); // 紫色
            }
        } catch (Exception e) {
            LOGGER.warn("渲染镜像预览时出错: {}", e.getMessage());
        }
    }
}
