package com.plot.core.command.commands;

import com.plot.core.model.Shape;
import com.plot.core.state.AppState;

import java.util.List;

/**
 * 复制移动命令
 *
 * <p>用于在移动时按下 Ctrl 键的复制场景：保留原图形，只添加位移后的新图形。</p>
 */
public class CopyMoveCommand extends ModifyCommand {

    public CopyMoveCommand(List<Shape> originalShapes, List<Shape> movedCopies, AppState appState) {
        super(originalShapes, movedCopies, appState, "复制移动");
    }

    @Override
    public void execute() {
        // 复制移动：只添加新图形，不移除原图形
        for (Shape shape : newShapes) {
            appState.addShape(shape);
        }
    }

    @Override
    public void undo() {
        // 撤销：移除添加的复制品
        for (Shape shape : newShapes) {
            appState.removeShape(shape);
        }
    }

    @Override
    public void redo() {
        // 重做：再次添加复制品
        for (Shape shape : newShapes) {
            appState.addShape(shape);
        }
    }

    @Override
    public String getDescription() {
        return String.format("复制移动 %d 个图形", newShapes.size());
    }
}


