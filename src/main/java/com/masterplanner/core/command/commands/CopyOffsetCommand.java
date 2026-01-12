package com.masterplanner.core.command.commands;

import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;

import java.util.List;

/**
 * 仅添加新偏移图形，不移除原图形的命令
 */
public class CopyOffsetCommand extends ModifyCommand {

    public CopyOffsetCommand(List<Shape> originalShapes, List<Shape> offsetCopies, AppState appState) {
        super(originalShapes, offsetCopies, appState);
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
        return "偏移复制线段";
    }
}


