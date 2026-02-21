package com.masterplanner.core.command.commands;

import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;

import java.util.List;

/**
 * 复制缩放命令
 *
 * <p>用于在缩放时按下 Ctrl 键的复制场景：保留原图形，只添加缩放后的新图形。</p>
 */
public class CopyScaleCommand extends ModifyCommand {

    public CopyScaleCommand(List<Shape> originalShapes, List<Shape> scaledCopies, AppState appState) {
        super(originalShapes, scaledCopies, appState, "复制缩放");
    }

    @Override
    public void execute() {
        // 复制缩放：只添加新图形，不移除原图形
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
        return String.format("复制缩放 %d 个图形", newShapes.size());
    }
}
