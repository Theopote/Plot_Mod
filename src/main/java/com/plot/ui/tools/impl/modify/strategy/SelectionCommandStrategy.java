package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.graphics.DrawContext;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于当前选择的单次命令策略：支持点选/框选，右键或外部调用 {@link #prepare} 执行命令。
 */
abstract class SelectionCommandStrategy extends BaseSelectionStrategy implements IModifyStrategy {

    private ModifyCommand pendingCommand;

    protected abstract String emptySelectionStatusKey();

    protected abstract String noEffectStatusKey();

    protected abstract String historyOperationKey();

    protected abstract List<Shape> computeResult(List<Shape> selected);

    protected abstract boolean hasEffect(List<Shape> selected, List<Shape> result);

    public boolean prepare(ModifyToolContext context) {
        pendingCommand = null;
        List<Shape> selected = new ArrayList<>(context.getSelectedShapes());
        if (selected.isEmpty()) {
            context.setStatusMessage(emptySelectionStatusKey());
            return false;
        }

        List<Shape> result = computeResult(selected);
        if (!hasEffect(selected, result)) {
            context.setStatusMessage(noEffectStatusKey());
            return false;
        }

        pendingCommand = new ModifyCommand(
                selected,
                result,
                (AppState) context.getAppState(),
                PlotI18n.tr(historyOperationKey()));
        return true;
    }

    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        if (button == MOUSE_RIGHT) {
            return tryExecute(context);
        }
        if (button == MOUSE_LEFT) {
            return handleSelectionMouseDown(pos, context);
        }
        return ModifyResult.IGNORED;
    }

    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        if (isSelecting) {
            context.setPreviewEnabled(true);
            return handleSelectionMouseMove(pos, context);
        }
        return ModifyResult.IGNORED;
    }

    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        if (button == MOUSE_LEFT && isSelecting) {
            return handleSelectionMouseUp(pos, context);
        }
        return ModifyResult.IGNORED;
    }

    private ModifyResult tryExecute(ModifyToolContext context) {
        if (prepare(context)) {
            return ModifyResult.COMPLETE;
        }
        return ModifyResult.CONTINUE;
    }

    @Override
    public ModifyCommand getModifyCommand() {
        return pendingCommand;
    }

    @Override
    public void reset() {
        pendingCommand = null;
        resetSelectionState();
    }

    @Override
    public void renderPreview(DrawContext context) {
        if (isSelecting) {
            renderSelectionPreview(context);
        }
    }

    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (isSelecting) {
            renderSelectionPreviewImGui(drawList, camera);
        }
    }

    @Override
    public boolean requiresSelection() {
        return true;
    }

    @Override
    public int getMinimumSelectionCount() {
        return 1;
    }

    @Override
    public int getMaximumSelectionCount() {
        return Integer.MAX_VALUE;
    }
}
