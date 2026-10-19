package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.context.ApplicationContext;
import com.plot.core.graphics.DrawContext;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.theme.UITheme;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.tools.impl.drawing.helper.DrawMeasureHelper;
import com.plot.ui.tools.impl.modify.constants.ModifyConstraints;
import com.plot.ui.tools.impl.modify.dto.ModifyParameters;
import com.plot.ui.tools.impl.modify.helper.IModifyHandler;
import com.plot.ui.tools.impl.modify.helper.MoveHandler;
import com.plot.utils.ExceptionDebug;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.List;

/**
 * 复制工具策略：点选/框选图形后，参考点 → 目标点复制，支持连续复制。
 */
public class CopyWithSelectionStrategy extends BaseSelectionStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(CopyWithSelectionStrategy.class);

    public enum StrategyMode {
        SELECTION("strategy.plot.mode.selection", "strategy.plot.mode.selection.desc"),
        COPY("strategy.plot.mode.copy", "strategy.plot.mode.copy.desc");

        private final String nameKey;
        private final String descKey;

        StrategyMode(String nameKey, String descKey) {
            this.nameKey = nameKey;
            this.descKey = descKey;
        }

        public String getDisplayName() {
            return PlotI18n.modeLabel(nameKey);
        }

        public String getDescription() {
            return PlotI18n.modeLabel(descKey);
        }
    }

    private static final double MIN_COPY_DISTANCE = 0.1;
    private static final int ESC_KEY = 27;
    private static final int SHIFT_KEY = 16;
    private static final int PREVIEW_ALPHA = 180;

    private StrategyMode currentMode = StrategyMode.SELECTION;
    private boolean hasBasePoint = false;
    private boolean isShiftPressed = false;

    private Vec2d copyStartPoint;
    private Vec2d copyCurrentPoint;
    private Vec2d constrainedEndPoint;

    private MoveHandler moveHandler;
    private ModifyParameters copyParameters;
    private ModifyConstraints copyConstraints;
    private List<Shape> previewShapes;
    private ModifyCommand pendingCommand;

    public CopyWithSelectionStrategy() {
        this.copyParameters = new ModifyParameters();
        this.copyConstraints = new ModifyConstraints();
        this.copyConstraints.setOrthogonalConstraintEnabled(false);
    }

    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        if (button == MOUSE_RIGHT) {
            return handleRightMouseDown(pos, context);
        }
        if (button == MOUSE_LEFT) {
            return handleLeftMouseDown(pos, context);
        }
        return ModifyResult.IGNORED;
    }

    private ModifyResult handleRightMouseDown(Vec2d pos, ModifyToolContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            if (!selectedShapeIds.isEmpty()) {
                currentMode = StrategyMode.COPY;
                selectedShapes = getSelectedShapesFromIds(context);
                context.setStatusMessage(PlotI18n.status("status.plot.copy.initial_base", selectedShapeIds.size()));
                return ModifyResult.CONTINUE;
            }
            context.setStatusMessage("status.plot.copy.initial_select");
            return ModifyResult.NEED_SELECTION;
        }

        resetCopyOperationState();
        currentMode = StrategyMode.SELECTION;
        context.setStatusMessage("status.plot.copy.cancelled");
        return ModifyResult.CANCEL;
    }

    private ModifyResult handleLeftMouseDown(Vec2d pos, ModifyToolContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            return handleSelectionMouseDown(pos, context);
        }
        return handleCopyMouseDown(pos, context);
    }

    private ModifyResult handleCopyMouseDown(Vec2d pos, ModifyToolContext context) {
        if (selectedShapes == null || selectedShapes.isEmpty()) {
            context.setStatusMessage("status.plot.copy.no_selection");
            return ModifyResult.NEED_SELECTION;
        }

        if (moveHandler == null) {
            AppState appState = ApplicationContext.getInstance().getAppState();
            if (appState == null) {
                context.setStatusMessage("status.plot.common.no_app_state");
                return ModifyResult.CANCEL;
            }
            moveHandler = new MoveHandler(appState);
        }

        Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
        try {
            isShiftPressed = imgui.ImGui.getIO().getKeyShift();
        } catch (Exception e) {
            ExceptionDebug.log("CopyWithSelectionStrategy: read Shift key state", e);
        }

        if (!hasBasePoint) {
            copyStartPoint = snappedPoint;
            copyCurrentPoint = snappedPoint;
            hasBasePoint = true;
            copyParameters.setStartPoint(copyStartPoint);
            copyParameters.setEndPoint(copyCurrentPoint);
            context.setStatusMessage("status.plot.copy.base_set");
            return ModifyResult.CONTINUE;
        }

        copyCurrentPoint = snappedPoint;
        copyParameters.setEndPoint(copyCurrentPoint);
        return completeCopy(snappedPoint, context);
    }

    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            return handleSelectionMouseMove(pos, context);
        }
        return handleCopyMouseMove(pos, context);
    }

    private ModifyResult handleCopyMouseMove(Vec2d pos, ModifyToolContext context) {
        if (!hasBasePoint) {
            return ModifyResult.IGNORED;
        }

        try {
            copyCurrentPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            copyParameters.setStartPoint(copyStartPoint);
            copyParameters.setEndPoint(copyCurrentPoint);

            updateConstraints();
            IModifyHandler.ModifyParameters constrainedParameters =
                    moveHandler.applyConstraints(copyParameters, copyConstraints);
            if (constrainedParameters instanceof ModifyParameters cp) {
                constrainedEndPoint = cp.getEndPoint();
            } else {
                constrainedEndPoint = copyCurrentPoint;
            }

            previewShapes = moveHandler.createPreviewShapes(selectedShapes, constrainedParameters);

            String statusMessage = PlotI18n.status("status.plot.copy.distance",
                    copyCurrentPoint.subtract(copyStartPoint).length());
            if (isShiftPressed) {
                statusMessage += PlotI18n.status("status.plot.common.orthogonal_suffix");
            }
            context.setStatusMessage(PlotI18n.status("status.plot.common.click_finish_suffix",
                    PlotI18n.localizeStatus(statusMessage)));
            context.setPreviewEnabled(true);
            return ModifyResult.CONTINUE;
        } catch (Exception e) {
            LOGGER.error("复制策略鼠标移动处理失败: {}", e.getMessage(), e);
            return ModifyResult.CONTINUE;
        }
    }

    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        if (button != MOUSE_LEFT || !isSelecting) {
            return ModifyResult.IGNORED;
        }
        if (currentMode == StrategyMode.SELECTION) {
            return handleSelectionMouseUp(pos, context);
        }
        return ModifyResult.CONTINUE;
    }

    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        if (keyCode == SHIFT_KEY) {
            isShiftPressed = true;
            if (hasBasePoint) {
                return onMouseMove(copyCurrentPoint, context);
            }
            return ModifyResult.CONTINUE;
        }
        if (keyCode == ESC_KEY) {
            reset();
            context.setStatusMessage("status.plot.copy.ended");
            return ModifyResult.CANCEL;
        }
        return ModifyResult.IGNORED;
    }

    @Override
    public ModifyResult onKeyUp(int keyCode, ModifyToolContext context) {
        if (keyCode == SHIFT_KEY) {
            isShiftPressed = false;
            if (hasBasePoint) {
                return onMouseMove(copyCurrentPoint, context);
            }
        }
        return ModifyResult.IGNORED;
    }

    private ModifyResult completeCopy(Vec2d point, ModifyToolContext context) {
        copyCurrentPoint = point;
        Vec2d offset = copyCurrentPoint.subtract(copyStartPoint);
        if (offset.length() < MIN_COPY_DISTANCE) {
            context.setStatusMessage("status.plot.copy.too_small");
            clearCopyPreviewState();
            return ModifyResult.CONTINUE;
        }

        copyParameters.setStartPoint(copyStartPoint);
        copyParameters.setEndPoint(copyCurrentPoint);
        copyParameters.setCopyMode(true);

        updateConstraints();
        IModifyHandler.ModifyParameters constrainedParameters =
                moveHandler.applyConstraints(copyParameters, copyConstraints);

        IModifyHandler.ValidationResult validation =
                moveHandler.validateModification(selectedShapes, constrainedParameters);
        if (!validation.isValid()) {
            context.setStatusMessage(PlotI18n.status("status.plot.copy.invalid",
                    validation.getLocalizedErrorMessage()));
            return ModifyResult.CONTINUE;
        }

        List<Shape> copiedShapes = moveHandler.calculateModifiedShapes(selectedShapes, constrainedParameters);
        pendingCommand = moveHandler.createModifyCommand(selectedShapes, copiedShapes, constrainedParameters);
        if (pendingCommand != null) {
            context.setStatusMessage("status.plot.copy.complete");
            return ModifyResult.COMPLETE;
        }

        context.setStatusMessage("status.plot.copy.command_failed");
        return ModifyResult.CANCEL;
    }

    /**
     * 单次复制完成后准备下一次复制，保留选择集与参考点，只需再点目标点。
     */
    public void prepareForNextCopy(ModifyToolContext context) {
        clearCopyPreviewState();
        currentMode = StrategyMode.COPY;
        selectedShapes = getSelectedShapesFromIds(context);
        for (Shape shape : selectedShapes) {
            shape.setSelected(true);
            context.addSelectedShape(shape);
        }
        context.setStatusMessage(PlotI18n.status("status.plot.copy.next_target", selectedShapeIds.size()));
    }

    private void updateConstraints() {
        copyConstraints.setOrthogonalConstraintEnabled(isShiftPressed);
    }

    private void clearCopyPreviewState() {
        copyCurrentPoint = copyStartPoint;
        constrainedEndPoint = null;
        previewShapes = null;
        pendingCommand = null;
        if (copyParameters != null && copyStartPoint != null) {
            copyParameters.setStartPoint(copyStartPoint);
            copyParameters.setEndPoint(copyStartPoint);
        }
    }

    private void resetCopyOperationState() {
        hasBasePoint = false;
        copyStartPoint = null;
        copyCurrentPoint = null;
        constrainedEndPoint = null;
        previewShapes = null;
        pendingCommand = null;
        if (copyParameters != null) {
            copyParameters.clear();
        }
    }

    @Override
    public ModifyCommand getModifyCommand() {
        return pendingCommand;
    }

    @Override
    public void reset() {
        resetSelectionState();
        resetCopyOperationState();
        isShiftPressed = false;
        currentMode = StrategyMode.SELECTION;
        moveHandler = null;
    }

    @Override
    public String getStrategyName() {
        return PlotI18n.modeLabel("strategy.plot.name.copy_with_selection");
    }

    @Override
    public String getStrategyDescription() {
        return currentMode.getDescription();
    }

    @Override
    public boolean requiresSelection() {
        return false;
    }

    @Override
    public int getMinimumSelectionCount() {
        return 0;
    }

    @Override
    protected ModifyResult handleSelectionMouseUp(Vec2d pos, ModifyToolContext context) {
        ModifyResult result = super.handleSelectionMouseUp(pos, context);
        if (result == ModifyResult.COMPLETE) {
            int count = getSelectedCount();
            if (count > 0) {
                context.setStatusMessage(PlotI18n.status("status.plot.common.selected_right_click", count,
                        PlotI18n.tr("tool.plot.copy")));
            } else {
                context.setStatusMessage("status.plot.copy.select_shapes");
            }
        }
        return result;
    }

    public void renderPreview(DrawContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            renderSelectionPreview(context);
        } else {
            renderCopyPreview(context);
        }
    }

    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (currentMode == StrategyMode.SELECTION) {
            renderSelectionPreviewImGui(drawList, camera);
        } else {
            renderCopyPreviewImGui(drawList, camera);
        }
    }

    private void renderCopyPreview(DrawContext context) {
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        Color previewColor = withAlpha(toColor(theme.warningText), PREVIEW_ALPHA);

        if (previewShapes != null) {
            for (Shape shape : previewShapes) {
                shape.render(context);
            }
        }

        if (copyStartPoint != null && copyCurrentPoint != null) {
            Vec2d effectiveEnd = constrainedEndPoint != null ? constrainedEndPoint : copyCurrentPoint;
            if (effectiveEnd.subtract(copyStartPoint).length() > MIN_COPY_DISTANCE) {
                context.drawDashedLine(copyStartPoint, effectiveEnd, previewColor);
                context.drawCircle(copyStartPoint, 3.0f, previewColor);
                context.drawCircle(effectiveEnd, 3.0f, previewColor);
                DrawMeasureHelper.drawSegmentLabel(context, copyStartPoint, effectiveEnd);
            }
        }
    }

    private void renderCopyPreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        if (copyStartPoint == null || copyCurrentPoint == null) {
            return;
        }
        try {
            Vec2d effectiveEnd = constrainedEndPoint != null ? constrainedEndPoint : copyCurrentPoint;
            if (effectiveEnd.subtract(copyStartPoint).length() <= MIN_COPY_DISTANCE) {
                return;
            }
            Vec2d screenStart = camera.worldToScreen(copyStartPoint);
            Vec2d screenEnd = camera.worldToScreen(effectiveEnd);
            UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
            drawList.addLine(
                    (float) screenStart.x, (float) screenStart.y,
                    (float) screenEnd.x, (float) screenEnd.y,
                    theme.warningText, 2.0f);
            drawList.addCircleFilled((float) screenStart.x, (float) screenStart.y, 4.0f, theme.errorText);
            drawList.addCircleFilled((float) screenEnd.x, (float) screenEnd.y, 4.0f, theme.errorText);
        } catch (Exception e) {
            LOGGER.warn("渲染复制预览时出错: {}", e.getMessage());
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
}
