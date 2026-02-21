package com.masterplanner.core.command.commands;

import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;

import java.util.List;

/**
 * 复制镜像命令
 *
 * <p>用于在镜像时使用复制镜像模式的场景：保留原图形，只添加镜像后的新图形。</p>
 */
public class CopyMirrorCommand extends ModifyCommand {

    public CopyMirrorCommand(List<Shape> originalShapes, List<Shape> mirroredCopies, AppState appState) {
        super(originalShapes, mirroredCopies, appState, "复制镜像");
    }

    @Override
    public void execute() {
        // 复制镜像：只添加新图形，不移除原图形
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
        return String.format("复制镜像 %d 个图形", newShapes.size());
    }
}
