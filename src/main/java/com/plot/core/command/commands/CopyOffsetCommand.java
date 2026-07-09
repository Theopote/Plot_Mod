package com.plot.core.command.commands;

import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.utils.PlotI18n;

import java.util.List;

/**
 * 仅添加新偏移图形，不移除原图形的命令
 */
public class CopyOffsetCommand extends ModifyCommand {

    public CopyOffsetCommand(List<Shape> originalShapes, List<Shape> offsetCopies, AppState appState) {
        super(originalShapes, offsetCopies, appState, "history.plot.op.copy_offset");
    }

    @Override
    public void execute() {
        // 复制偏移：只添加新图形，不移除原图形
        for (Shape shape : newShapes) {
            appState.addShape(shape);
        }
    }

    @Override
    public void undo() {
        // 撤销：移除添加的偏移副本
        for (Shape shape : newShapes) {
            appState.removeShape(shape);
        }
    }

    @Override
    public void redo() {
        // 重做：再次添加偏移副本
        for (Shape shape : newShapes) {
            appState.addShape(shape);
        }
    }

    @Override
    public String getDescription() {
        return PlotI18n.tr("history.plot.copy_offset", newShapes.size());
    }
}


